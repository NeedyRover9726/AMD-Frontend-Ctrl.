import os
import base64
import uuid
import json
import asyncio
import shutil
import tempfile
import logging
from asyncio import Lock
from typing import Dict, Literal
import chromadb
import fitz 
from chromadb.utils.embedding_functions import OpenAIEmbeddingFunction
from fastapi import FastAPI, UploadFile, File, Form, HTTPException, Query
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from openai import OpenAI
from dotenv import load_dotenv

# ADDED: LangChain's semantic chunker
from langchain_text_splitters import RecursiveCharacterTextSplitter

# ADDED: Logging for background task errors
logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# Load environment variables from the .env file
load_dotenv()

app = FastAPI(title="Ctrl. Backend - Gamified RAG Engine")

# Pull the API key securely from the environment
FIREWORKS_API_KEY = os.getenv("FIREWORKS_API_KEY")

if not FIREWORKS_API_KEY:
    raise ValueError("FIREWORKS_API_KEY is not set. Please check your .env file.")

client = OpenAI(
    base_url="https://api.fireworks.ai/inference/v1", 
    api_key=FIREWORKS_API_KEY
)

# ==========================================
# 🛠️ TESTING TOGGLE (Change to False for final build)
# ==========================================
DEBUG_MODE = True  

# Dedicated On-Demand Path (For Final Submission)
GEMMA_DEPLOYMENT = "accounts/your_username/deployments/your_deployment_name"

# The Absolute Best Serverless Fallbacks available right now
SERVERLESS_VISION = "accounts/fireworks/models/qwen3p7-plus"
SERVERLESS_TEXT = "accounts/fireworks/models/deepseek-v4-flash"
# ==========================================

fireworks_ef = OpenAIEmbeddingFunction(
    api_key=FIREWORKS_API_KEY,
    api_base="https://api.fireworks.ai/inference/v1",
    model_name="nomic-ai/nomic-embed-text-v1.5" 
)

# ADDED: Global lock to prevent ChromaDB (SQLite) thread collisions
chroma_db_lock = Lock()

# ADDED: Track PDF processing status globally
# Format: {"title": {"status": "processing|success|failed", "error": "..."}}
pdf_processing_status: Dict[str, Dict] = {}
status_lock = Lock()

# Initialize ChromaDB persistent storage locally
chroma_client = chromadb.PersistentClient(path="./chroma_db")
collection = chroma_client.get_or_create_collection(
    name="textbook_materials",
    embedding_function=fireworks_ef,
    metadata={"hnsw:space": "cosine"} # ADDED: Force Cosine Similarity for Nomic
)

def chunk_text(text: str, chunk_size: int = 1000, overlap: int = 200) -> list:
    """UPDATED: Splits text semantically using LangChain to preserve sentence structure."""
    text_splitter = RecursiveCharacterTextSplitter(
        chunk_size=chunk_size,
        chunk_overlap=overlap,
        length_function=len,
        is_separator_regex=False,
    )
    return text_splitter.split_text(text)

def batch_insert_to_chroma(chunks: list, title: str):
    """Inserts into ChromaDB in batches to prevent payload size errors."""
    batch_size = 100 
    for i in range(0, len(chunks), batch_size):
        batch_chunks = chunks[i:i + batch_size]
        chunk_ids = [str(uuid.uuid4()) for _ in batch_chunks]
        metadatas = [{"title": title} for _ in batch_chunks]
        
        collection.add(
            documents=batch_chunks,
            metadatas=metadatas,
            ids=chunk_ids
        )

# ADDED: Safe background processor for PDFs with proper error handling
async def process_pdf_background(file_path: str, title: str):
    """Safely extracts text, chunks it, and saves to ChromaDB in the background."""
    temp_cleanup_scheduled = False
    try:
        logger.info(f"Starting background processing for: {title}")
        
        # Update status to processing
        async with status_lock:
            pdf_processing_status[title] = {"status": "processing"}
        
        # Extract text from the temp file on disk
        try:
            pdf_document = fitz.open(file_path)
            pdf_text_pages = [page.get_text("text") for page in pdf_document]
            pdf_document.close()
            extracted_text = " ".join(pdf_text_pages)
        except Exception as e:
            logger.error(f"PDF extraction failed for {title}: {e}")
            async with status_lock:
                pdf_processing_status[title] = {
                    "status": "failed",
                    "error": f"PDF extraction failed: {str(e)}"
                }
            return

        if not extracted_text or not extracted_text.strip():
            logger.error(f"No text extracted from {title}")
            async with status_lock:
                pdf_processing_status[title] = {
                    "status": "failed",
                    "error": "PDF contains no readable text"
                }
            return

        # Chunk the text
        try:
            chunks = chunk_text(extracted_text, chunk_size=800, overlap=100)
        except Exception as e:
            logger.error(f"Text chunking failed for {title}: {e}")
            async with status_lock:
                pdf_processing_status[title] = {
                    "status": "failed",
                    "error": f"Text chunking failed: {str(e)}"
                }
            return

        if not chunks:
            logger.warning(f"No chunks generated for {title}")
            async with status_lock:
                pdf_processing_status[title] = {
                    "status": "success",
                    "chunks_saved": 0
                }
            return

        # Use the lock to ensure only ONE thread writes to ChromaDB at a time
        try:
            async with chroma_db_lock:
                await asyncio.to_thread(batch_insert_to_chroma, chunks, title)
        except Exception as e:
            logger.error(f"ChromaDB insertion failed for {title}: {e}")
            async with status_lock:
                pdf_processing_status[title] = {
                    "status": "failed",
                    "error": f"Database insertion failed: {str(e)}"
                }
            return
        
        # Mark as successful
        async with status_lock:
            pdf_processing_status[title] = {
                "status": "success",
                "chunks_saved": len(chunks)
            }
        
        logger.info(f"Successfully processed and saved: {title} ({len(chunks)} chunks)")

    except Exception as e:
        logger.exception(f"Unexpected error processing {title}: {e}")
        async with status_lock:
            pdf_processing_status[title] = {
                "status": "failed",
                "error": f"Unexpected error: {str(e)}"
            }
    finally:
        # ALWAYS clean up the temporary file, with error handling
        try:
            if os.path.exists(file_path):
                os.remove(file_path)
                logger.debug(f"Cleaned up temp file: {file_path}")
        except Exception as cleanup_error:
            logger.error(f"Failed to clean up temp file {file_path}: {cleanup_error}")

def encode_image(file_bytes: bytes) -> str:
    """Converts raw image bytes to a base64 string for the vision model."""
    return base64.b64encode(file_bytes).decode('utf-8')

# --- 🧠 MATH & LOGIC HELPERS ---
def calculate_optimal_break(study_time_minutes: int) -> int:
    """
    Calculates the optimal break duration based on the cognitive load formula.
    """
    if study_time_minutes <= 60:
        break_time = study_time_minutes * 0.2
    else:
        break_time = 12 + ((study_time_minutes - 60) * 0.1)
        
    return int(round(break_time))

# Pydantic model for receiving quiz results
class QuizResult(BaseModel):
    total_questions: int
    correct_answers: int
    is_session_complete: bool = False

# ==========================================
# 🚀 API ENDPOINTS
# ==========================================

@app.get("/calculate-break/")
async def get_break_time(study_time: int = Query(..., description="Study session length in minutes")):
    """
    Returns the optimal break time based on how long the user studied.
    """
    if study_time <= 0:
        raise HTTPException(status_code=400, detail="Study time must be greater than 0.")
        
    recommended_break = calculate_optimal_break(study_time)
    
    return {
        "study_time_minutes": study_time,
        "recommended_break_minutes": recommended_break
    }


@app.post("/upload-material/")
async def upload_material(title: str = Form(...), file: UploadFile = File(...)):
    """
    Phase 1: Receives an image or PDF. Processes it securely.
    For PDFs: Returns immediately with status "processing"; check /upload-status/{title} to poll completion.
    For images: Returns immediately with status "success" once processing is complete.
    """
    try:
        content_type = file.content_type
        
        # --- IMAGE PROCESSING PATH (Remains Synchronous) ---
        if "image" in content_type:
            file_bytes = await file.read()
            base64_image = encode_image(file_bytes)
            active_vision_model = SERVERLESS_VISION if DEBUG_MODE else GEMMA_DEPLOYMENT
            
            vision_completion = client.chat.completions.create(
                model=active_vision_model,
                messages=[
                    {
                        "role": "user",
                        "content": [
                            {"type": "text", "text": "Extract all academic text from this textbook page perfectly. Output only the plain text. Do not include markdown formatting."},
                            {"type": "image_url", "image_url": {"url": f"data:image/jpeg;base64,{base64_image}"}}
                        ]
                    }
                ]
            )
            extracted_text = vision_completion.choices[0].message.content
            
            if not extracted_text or not extracted_text.strip():
                raise HTTPException(status_code=500, detail="Failed to extract text from the file.")

            chunks = chunk_text(extracted_text, chunk_size=800, overlap=100)
            if not chunks:
                return {"status": "success", "title": title, "chunks_saved": 0}

            # Safety lock added here too just in case
            async with chroma_db_lock:
                await asyncio.to_thread(batch_insert_to_chroma, chunks, title)
            
            return {"status": "success", "title": title, "chunks_saved": len(chunks)}
            
        # --- PDF PROCESSING PATH (Updated to Async Background Task) ---
        elif "pdf" in content_type:
            # Initialize status tracking
            async with status_lock:
                pdf_processing_status[title] = {"status": "queued"}
            
            # Write to temp file
            try:
                with tempfile.NamedTemporaryFile(delete=False, suffix=".pdf") as temp_file:
                    shutil.copyfileobj(file.file, temp_file)
                    temp_file_path = temp_file.name
            except Exception as e:
                logger.error(f"Failed to write temp file for {title}: {e}")
                async with status_lock:
                    pdf_processing_status[title] = {
                        "status": "failed",
                        "error": f"Failed to save uploaded file: {str(e)}"
                    }
                raise HTTPException(status_code=500, detail=f"Failed to save file: {str(e)}")

            # Fire off the background task
            asyncio.create_task(process_pdf_background(temp_file_path, title))
            
            return {
                "status": "processing", 
                "title": title,
                "message": "Document received! Processing in the background.",
                "check_status_url": f"/upload-status/{title}"
            }
        else:
            raise HTTPException(status_code=400, detail="Unsupported file type.")

    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Unexpected error in upload_material: {e}")
        raise HTTPException(status_code=500, detail=str(e))

# ADDED: New endpoint to check PDF processing status
@app.get("/upload-status/{title}")
async def get_upload_status(title: str):
    """
    Check the processing status of a PDF upload.
    
    Returns:
    - status: "queued", "processing", "success", or "failed"
    - error (if failed): Error message
    - chunks_saved (if success): Number of chunks saved
    """
    async with status_lock:
        status_info = pdf_processing_status.get(title, {"status": "not_found"})
    
    if status_info["status"] == "not_found":
        raise HTTPException(status_code=404, detail=f"No upload found for title: {title}")
    
    return status_info

@app.get("/materials/")
async def list_materials():
    """
    Phase 2: Returns unique titles for the frontend session setup wizard.
    Note: Only returns materials that have completed processing (status='success' or already in DB).
    """
    try:
        db_data = collection.get(include=["metadatas"])
        metadatas = db_data.get("metadatas", [])
        unique_titles = list(set([meta["title"] for meta in metadatas if meta and "title" in meta]))
        return {"saved_materials": unique_titles}
    except Exception as e:
        logger.error(f"Error listing materials: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/generate-quiz/")
async def generate_quiz(topic: str = Form(...), selected_titles: str = Form(...)):
    """
    Phase 3: Queries ChromaDB and generates the JSON quiz array.
    
    Note: If a selected material is still processing, returns an error asking the user to wait.
    """
    try:
        title_list = [t.strip() for t in selected_titles.split(",") if t.strip()]
        
        if not title_list:
            raise HTTPException(status_code=400, detail="You must select at least one material.")

        # Check if any materials are still processing
        async with status_lock:
            for title in title_list:
                if title in pdf_processing_status:
                    status = pdf_processing_status[title].get("status")
                    if status == "processing":
                        raise HTTPException(
                            status_code=202,
                            detail=f"Material '{title}' is still processing. Please wait and try again."
                        )
                    elif status == "failed":
                        error_msg = pdf_processing_status[title].get("error", "Unknown error")
                        raise HTTPException(
                            status_code=400,
                            detail=f"Material '{title}' failed to process: {error_msg}"
                        )

        results = collection.query(
            query_texts=[topic],
            n_results=15, 
            where={"title": {"$in": title_list}},
            include=["documents", "distances"]
        )
        
        retrieved_documents = results.get("documents", [[]])[0]
        distances = results.get("distances", [[]])[0]
        
        if not retrieved_documents:
            return JSONResponse(
                status_code=400,
                content={"error": "INSUFFICIENT_DATA", "message": "No relevant material found for this topic."}
            )

        # UPDATED: We now filter by Cosine Similarity (< 0.3 is highly relevant)
        highly_relevant_chunks = [dist for dist in distances if dist < 0.3]
        
        concept_count = max(1, len(highly_relevant_chunks))
        calculated_length = concept_count * 2
        
        quiz_length = max(5, min(25, calculated_length))
        
        context_text = "\n---\n".join(retrieved_documents)

        system_prompt = (
            "You are an academic instructor. Your ONLY task is to generate multiple-choice questions based EXCLUSIVELY on the provided Context.\n"
            "Rules:\n"
            "1. Use ONLY the information provided in the Context to form your questions and answers.\n"
            "2. If the Context does not contain enough information, return 'INSUFFICIENT_DATA' instead of fabricating data.\n"
            "3. Do not use outside knowledge or training data.\n"
            "4. Return the output as a raw JSON array matching this structure exactly:\n"
            "[\n"
            "  {\n"
            "    \"question\": \"...\",\n"
            "    \"options\": [\"A\", \"B\", \"C\", \"D\"],\n"
            "    \"correct_answer\": \"...\"\n"
            "  }\n"
            "]"
        )
        
        user_content = f"Context:\n{context_text}\n\nTask: Generate exactly a {quiz_length}-question multiple-choice quiz based on the topic/pages: '{topic}'."

        active_text_model = SERVERLESS_TEXT if DEBUG_MODE else GEMMA_DEPLOYMENT

        quiz_completion = client.chat.completions.create(
            model=active_text_model,
            messages=[
                {"role": "system", "content": system_prompt},
                {"role": "user", "content": user_content}
            ],
            temperature=0.2 
        )
        
        raw_output = quiz_completion.choices[0].message.content.strip()
        
        if "INSUFFICIENT_DATA" in raw_output:
            return JSONResponse(
                status_code=400, 
                content={"error": "INSUFFICIENT_DATA", "message": "The material does not contain enough data on this topic."}
            )

        if raw_output.startswith("```json"):
            raw_output = raw_output.replace("```json", "", 1).rstrip("```").strip()
        elif raw_output.startswith("```"):
            raw_output = raw_output.replace("```", "", 1).rstrip("```").strip()

        quiz_json = json.loads(raw_output)
        return quiz_json

    except json.JSONDecodeError:
        logger.error(f"JSON decode error in generate_quiz")
        raise HTTPException(status_code=500, detail="Model failed to output a valid JSON format. Try again.")
    except HTTPException:
        raise
    except Exception as e:
        logger.error(f"Unexpected error in generate_quiz: {e}")
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/evaluate-quiz/")
async def evaluate_quiz(result: QuizResult):
    """
    Phase 4: Evaluates the quiz score and dictates the next app state.
    """
    if result.total_questions <= 0:
        raise HTTPException(status_code=400, detail="Total questions must be greater than 0.")
        
    score_percentage = (result.correct_answers / result.total_questions) * 100
    
    if result.is_session_complete:
        return {
            "passed": True,
            "score": score_percentage,
            "action": "DISMISS_OVERLAY",
            "message": "You have finished your session, there will no longer be a time limit for your break. Enjoy!"
        }
        
    passed = score_percentage >= 50.0
    
    if passed:
        return {
            "passed": True,
            "score": score_percentage,
            "action": "DISMISS_OVERLAY",
            "message": "Intercept cleared! You earned your break."
        }
    else:
        return {
            "passed": False,
            "score": score_percentage,
            "action": "MAINTAIN_LOCK",
            "message": "Score too low. Return to your textbook and try again to unlock."
        }

@app.on_event("startup")
async def load_demo_data():
    """Automatically loads sample data so judges have something to test immediately."""
    demo_title = "Demo: Introduction to AMD Architectures"
    
    existing_data = collection.get(include=["metadatas"])
    titles = [meta["title"] for meta in existing_data.get("metadatas", []) if meta]
    
    if demo_title not in titles:
        demo_text = (
            "Advanced Micro Devices (AMD) is a leader in high-performance computing. "
            "Their recent architectures focus heavily on parallel processing and efficient "
            "workload distribution, which is critical for running modern LLMs efficiently."
        )
        
        collection.add(
            documents=[demo_text],
            metadatas=[{"title": demo_title}],
            ids=["demo_chunk_1"]
        )
        logger.info("Demo data loaded for judges!")

if __name__ == "__main__":
    import uvicorn
    port = int(os.environ.get("PORT", 8000))
    uvicorn.run(app, host="0.0.0.0", port=port)
