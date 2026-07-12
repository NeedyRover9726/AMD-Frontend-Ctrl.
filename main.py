import os
import base64
import uuid
import json
import chromadb
import fitz  # PyMuPDF for handling PDFs
from fastapi import FastAPI, UploadFile, File, Form, HTTPException, Query
from fastapi.responses import JSONResponse
from pydantic import BaseModel
from openai import OpenAI
from dotenv import load_dotenv
from chromadb.utils.embedding_functions import OpenAIEmbeddingFunction
from langchain_text_splitters import RecursiveCharacterTextSplitter

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

DEBUG_MODE = True

GEMMA_DEPLOYMENT = "accounts/sergecalasara12-123/deployments/c5tpac33"

SERVERLESS_VISION = "accounts/fireworks/models/qwen3p7-plus"
SERVERLESS_TEXT = "accounts/fireworks/models/deepseek-v4-flash"

chroma_client = chromadb.PersistentClient(path="./chroma_db")

# UPGRADE 1: Use Fireworks' Embedding Model instead
fireworks_ef = OpenAIEmbeddingFunction(
    api_key=FIREWORKS_API_KEY,
    api_base="https://api.fireworks.ai/inference/v1",
    model_name="nomic-ai/nomic-embed-text-v1.5"
)

# Pass the custom embedding function to the collection
collection = chroma_client.get_or_create_collection(
    name="textbook_materials",
    embedding_function=fireworks_ef,
    metadata={"hnsw:space": "cosine"}
)

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
    Phase 1: Receives an image or PDF. Extracts text, chunks it semantically, and saves it.
    """
    try:
        file_bytes = await file.read()
        content_type = file.content_type
        extracted_text = ""
        
        if "image" in content_type:
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
            
        elif "pdf" in content_type:
            pdf_document = fitz.open(stream=file_bytes, filetype="pdf")
            pdf_text_pages = []
            for page_num in range(len(pdf_document)):
                page = pdf_document.load_page(page_num)
                pdf_text_pages.append(page.get_text("text"))
            extracted_text = "\n\n".join(pdf_text_pages)
            pdf_document.close()
        else:
            raise HTTPException(status_code=400, detail="Unsupported file type.")

        if not extracted_text or not extracted_text.strip():
            raise HTTPException(status_code=500, detail="Failed to extract text from the file.")

        # UPGRADE 2: Use LangChain for semantic chunking with overlap
        text_splitter = RecursiveCharacterTextSplitter(
            chunk_size=1000, 
            chunk_overlap=150,
            separators=["\n\n", "\n", ".", " ", ""]
        )
        raw_chunks = text_splitter.split_text(extracted_text)

        if not raw_chunks:
            return {"status": "success", "title": title, "chunks_saved": 0}

        # UPGRADE 3: Contextualize chunks by prepending the title
        enhanced_chunks = [f"Source Material - {title}:\n{chunk}" for chunk in raw_chunks]

        chunk_ids = [str(uuid.uuid4()) for _ in enhanced_chunks]
        metadatas = [{"title": title} for _ in enhanced_chunks]
        
        collection.add(
            documents=enhanced_chunks,
            metadatas=metadatas,
            ids=chunk_ids
        )
        
        return {"status": "success", "title": title, "chunks_saved": len(enhanced_chunks)}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.get("/materials/")
async def list_materials():
    """
    Phase 2: Returns unique titles for the frontend session setup wizard.
    """
    try:
        db_data = collection.get(include=["metadatas"])
        metadatas = db_data.get("metadatas", [])
        unique_titles = list(set([meta["title"] for meta in metadatas if meta and "title" in meta]))
        return {"saved_materials": unique_titles}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.delete("/delete-material/")
async def delete_material(title: str = Query(..., description="The title of the material to delete")):
    """
    Deletes a specific material and all its associated chunks from the vector database.
    """
    try:
        # Check if the material actually exists before attempting deletion
        existing_docs = collection.get(
            where={"title": title},
            include=["metadatas"]
        )
        
        if not existing_docs.get("ids"):
            raise HTTPException(status_code=404, detail=f"Material '{title}' not found.")

        # Delete all chunks matching this title from ChromaDB
        collection.delete(
            where={"title": title}
        )
        
        return {
            "status": "success", 
            "message": f"Successfully deleted '{title}' and all its associated chunks."
        }
        
    except HTTPException:
        # Re-raise HTTP exceptions so FastAPI handles them properly
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@app.post("/generate-quiz/")
async def generate_quiz(topic: str = Form(...), selected_titles: str = Form(...)):
    """
    Phase 3: Queries ChromaDB and generates the JSON quiz array using the Fact-Scratchpad Strategy.
    """
    try:
        title_list = [t.strip() for t in selected_titles.split(",") if t.strip()]
        
        if not title_list:
            raise HTTPException(status_code=400, detail="You must select at least one material.")

        results = collection.query(
            query_texts=[topic],
            n_results=25,  # Increased so the LLM has enough text to actually hit 25 facts
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

        best_distance = distances[0]
        filtered_docs = []
        for doc, dist in zip(retrieved_documents, distances):
            if dist <= (best_distance + 0.15) and dist < 0.60:
                filtered_docs.append(doc)
        
        if not filtered_docs:
             return JSONResponse(
                status_code=400,
                content={"error": "INSUFFICIENT_DATA", "message": "No highly relevant material found for this topic."}
            )

        context_text = "\n---\n".join(filtered_docs)

        system_prompt = (
            "You are a strict academic instructor. Your task is to generate a multiple-choice quiz based EXCLUSIVELY on the provided Context.\n"
            "Rules:\n"
            "1. STEP 1 (Fact Extraction): First, identify every distinct, testable fact in the context. \n"
            "2. STEP 2 (Quiz Generation): Generate exactly ONE question for every fact you identified in Step 1. Maximum 25 questions.\n"
            "3. If there are fewer than 3 distinct facts, return exactly 'INSUFFICIENT_DATA'.\n"
            "4. Return ONLY a raw JSON object matching this exact schema (no markdown, no preamble):\n"
            "{\n"
            "  \"scratchpad_facts\": [\"Fact 1\", \"Fact 2\"],\n"
            "  \"quiz\": [\n"
            "    {\n"
            "      \"question\": \"...\",\n"
            "      \"options\": [\"A\", \"B\", \"C\", \"D\"],\n"
            "      \"correct_answer\": \"...\"\n"
            "    }\n"
            "  ]\n"
            "}"
        )
        
        # Removed the python math variables completely
        user_content = f"Context:\n{context_text}\n\nTask: Generate a multiple-choice quiz based on the facts available in the text regarding the topic: '{topic}'."

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

        # Isolating the quiz array so Kotlin doesn't crash
        full_json = json.loads(raw_output)
        
        if "quiz" not in full_json:
            raise ValueError("Model did not return a 'quiz' key.")
            
        return full_json["quiz"]

    except json.JSONDecodeError:
        raise HTTPException(status_code=500, detail="Model failed to output a valid JSON format. Try again.")
    except Exception as e:
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
            "Source Material - Demo: Introduction to AMD Architectures:\n"
            "Advanced Micro Devices (AMD) is a leader in high-performance computing. "
            "Their recent architectures focus heavily on parallel processing and efficient "
            "workload distribution, which is critical for running modern LLMs efficiently."
        )
        
        collection.add(
            documents=[demo_text],
            metadatas=[{"title": demo_title}],
            ids=["demo_chunk_1"]
        )
        print("Demo data loaded for judges!")

if __name__ == "__main__":
    import uvicorn
    port = int(os.environ.get("PORT", 8000))
    uvicorn.run(app, host="0.0.0.0", port=port)
