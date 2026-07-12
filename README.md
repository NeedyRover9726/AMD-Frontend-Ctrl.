# Ctrl. - The AI-Powered Focus App 🧠⚡

**Ctrl.** is an Android productivity application that forces you to earn your screen time. It intercepts your most distracting apps and locks them behind an AI-generated quiz based on your own uploaded study materials. No more mindless scrolling—if you want to use TikTok, you have to pass a quiz on Calculus first!

## ✨ Features
* **Smart App Interception:** Detects and blocks distracting apps the second you try to open them.
* **AI Quiz Generation (RAG):** Upload PDFs or images of your notes. The app uses AI to read them and generates multiple-choice quizzes on the spot.
* **Pomodoro Loop:** Automated Study & Break timers running in a resilient Foreground Service.
* **Anti-Cheat System:** Hardware-level screen pinning prevents users from swiping away the quiz to cheat.
* **Redmi/Xiaomi Optimized:** Custom logic to bypass aggressive HyperOS background restrictions.

## 📸 Screenshots
*(Pro-tip: Create a folder called `assets` in your repo, upload screenshots of your Home Screen, Quiz Screen, and Unlock Screen, and link them here!)*
## 🛠️ Tech Stack
* **Language:** Kotlin
* **UI Toolkit:** Jetpack Compose (Material Design 3)
* **Networking:** Retrofit & OkHttp
* **Architecture:** Foreground Services, Coroutines, Navigation Compose
* **Backend API:** Connects to a custom Python FastAPI backend hosted on Render (handles ChromaDB vector storage and Gemini AI generation).

## 🚀 Installation & Setup

### Prerequisites
* Android Studio (Latest Version)
* Minimum SDK: API 26 (Android 10)
* Target SDK: API 34 (Android 14)

### Running the App
1. Clone the repository:
   ```bash
   git clone [https://github.com/yourusername/ctrl-app.git](https://github.com/yourusername/ctrl-app.git)
