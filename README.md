## Ctrl. - Backend (FastAPI / RAG Engine)

**Live API:** [ctrl-uam1.onrender.com](https://ctrl-uam1.onrender.com/) | **Docs:** [Swagger UI](https://ctrl-uam1.onrender.com/docs)

---

### 📖 Overview

A Dockerized Python FastAPI web service powering **Ctrl.** Parses multimodal study materials and generates dynamic micro-quizzes using RAG. Initially prototyped on bare-metal AMD Instinct MI300X GPUs, it is now optimized for a serverless 24/7 production environment on Render using Fireworks AI.

### 🚀 Quick Start

**1. Live Server (Demo)**
Use the Live API URL above.

> ⚠️ **Cold Starts:** To save costs, the server scales to zero during inactivity. Expect a brief delay on your first request.
> ⚠️ **Credit Limits:** Because this live environment is left open, we may occasionally run out of API credits. **For uninterrupted use, we highly recommend running it locally or from your frontend using your own API key.**

**2. Local Docker (Recommended)**

```bash
git clone https://github.com/yourusername/ctrl-backend.git && cd ctrl-backend
docker build -t ctrl-backend .
# Run with your own API key to bypass live server credit limits
docker run -p 8000:8000 --env FIREWORKS_API_KEY=your_key_here ctrl-backend

```

### 🖥️ AMD Hardware Proof (`/proof` directory)

Check the `proof/` folder for objective evidence of our AMD Developer Cloud usage:

* `hardware_proof.log`: Raw `rocm-smi` output validating AMD Instinct MI300X (gfx942) usage.
* `cleaned_app_behavior.log`: Verifiable success paths for core inference endpoints.

### 🧠 Key Features

* **Multimodal Ingestion:** Parses PDFs and physical photos via Gemma 4 26B MOE vision capabilities [1.1.3].
* **Optimized RAG:** LangChain chunking + Fireworks AI (`nomic-embed-text-v1.5`) + Local ChromaDB.
* **Mobile-Safe Output:** Generates "Fact-Scratchpads" mapped to clean, markdown-stripped JSON arrays.

### 📡 Core Endpoints

* **`POST /upload-material/`**: Ingests PDFs/images, generates embeddings, and stores them in ChromaDB.
* **`POST /generate-quiz/`**: Retrieves relevant chunks and returns a pure JSON array of multiple-choice quizzes.
