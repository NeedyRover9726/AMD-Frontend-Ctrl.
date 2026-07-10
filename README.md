### 🚀 How to Run the Backend

#### Option 1: Live Demo (Recommended)
The backend is fully containerized and hosted live on Render. The Android app connects to it automatically out of the box.
URL: `https://your-app-name.onrender.com`

#### Option 2: Run Locally via Docker
If you prefer to run the containerized backend locally, ensure you have Docker installed and run:
1. Build the image:
   docker build -t ctrl-backend .
2. Run the container:
   docker run -p 8000:8000 --env FIREWORKS_API_KEY=your_key_here ctrl-backend
