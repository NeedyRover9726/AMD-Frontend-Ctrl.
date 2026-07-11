# Use a lightweight Python base image 
FROM python:3.11-slim

# Set the working directory inside the container 
WORKDIR /app

# Install system dependencies required by PyMuPDF and ChromaDB
RUN apt-get update && apt-get install -y \
    build-essential \
    libmupdf-dev \
    && rm -rf /var/lib/apt/lists/*

# Copy only the requirements first to cache the pip install step 
COPY requirements.txt .

# Install Python dependencies 
RUN pip install --no-cache-dir -r requirements.txt

# Copy the rest of the application code 
COPY . .

# Create a clean directory for ChromaDB to ensure it has write permissions
RUN mkdir -p /app/chroma_db && chmod 777 /app/chroma_db

# Expose the port the app runs on [cite: 2]
EXPOSE 8000

# Command to run the application [cite: 2]
CMD ["uvicorn", "main:app", "--host", "0.0.0.0", "--port", "8000"]
