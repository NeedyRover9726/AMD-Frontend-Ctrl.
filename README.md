# Ctrl. - The AI-Powered Focus App 🧠⚡ (Frontend)

The frontend of **Ctrl.** is a native Kotlin Android application designed to intercept doomscrolling at the OS level. Instead of relying on standard focus timers, Ctrl. forces users to earn their screen time by overlaying distracting apps and requiring them to pass an AI-generated micro-quiz based on their uploaded study materials.

---

## 🌍 1. How to Run the Application (System Overview)
Ctrl. operates on a client-server architecture. To experience the full end-to-end functionality:
1. **The Backend Must be Active:** The mobile app relies on the Python FastAPI backend to parse documents, chunk text, and generate quizzes. Ensure the backend is running locally (via Docker/Uvicorn) or deployed to a cloud provider (like Render or an AMD EPYC Droplet).
2. **API Connection:** The Android frontend uses Retrofit to communicate with the backend. If you are running the backend locally, ensure your Android device/emulator and your computer are on the same network, and update the `BASE_URL` in the `RetrofitClient` object (inside `Network.kt`) to your computer's local IP address.
3. **The Mobile Client:** Once the backend is reachable, the Android frontend handles all user interactions, timers, and OS-level app blocking.

---

## 💻 2. How to Run the Frontend (Local Development)
To compile and run the Android app yourself:

1. **Clone the Repository:** Download the project files to your local machine.
2. **Open in Android Studio:** Launch Android Studio and select **Open**. Navigate to the `/android-app` folder (where the `build.gradle.kts` files are located) and open it.
3. **Sync Gradle:** Android Studio will automatically download the required dependencies (Jetpack Compose, Retrofit, OkHttp, Gson). Wait for the Gradle sync to finish successfully.
4. **Configure a Device:** 
   * Connect a physical Android phone via USB/Wireless Debugging, OR
   * Create an Android Virtual Device (AVD) emulator.
   * *Requirement:* The device must be running **Android 10 (API Level 29) or higher**.
5. **Run the App:** Click the green **Play (Run 'app')** button in the top toolbar.

---

## 🚧 3. OEM Blockades & Troubleshooting (How to fix background kills)
Android manufacturers (OEMs) heavily customize their operating systems to save battery. These aggressive optimizations often act as "blockades," freezing the Ctrl. app in the background and preventing it from detecting when a blocked app is opened. 

If the app is not intercepting your screen, you must apply the following device-specific fixes in your phone's **Settings > Apps > Ctrl. > App Info**:

### 🔴 Xiaomi / POCO / Redmi (HyperOS & MIUI)
Xiaomi devices are notorious for blocking background services from launching overlays.
* **Display pop-up windows while running in the background:** MUST be set to **Allow** (Green Checkmark). If this is off, the lock screen will never appear.
* **Show on Lock screen:** Set to **Allow**.
* **Autostart / Background Autostart:** Toggle **ON**.
* **Battery Saver:** Change from "Battery saver (recommended)" to **No restrictions**.

### 🔵 Samsung (One UI)
Samsung devices will frequently put the background service to "sleep" after a few minutes of inactivity.
* **Battery Optimization:** Go to App Info > Battery > set to **Unrestricted**.
* **Never Sleeping Apps:** Go to Device Care > Battery > Background usage limits > ensure Ctrl. is added to the **"Never sleeping apps"** list.
* **Appear on Top:** Ensure this permission is explicitly granted in the special access menu.

### 🟢 Oppo / Vivo / Realme / OnePlus (ColorOS, FuntouchOS)
These OS variants aggressively kill background processes to free up RAM.
* **Auto Launch / Startup Management:** Toggle **ON** so the app can wake itself up.
* **Battery Optimization:** Set to **Don't Optimize**.
* **Lock in Recent Apps:** Open your recent apps screen (multitasking view), swipe down on the Ctrl. app (or click the 3 dots), and click **Lock**. This prevents the OS from clearing it when you tap "Clear All".

---

## 🔐 4. Standard Required Permissions
Because Ctrl. enforces strict behavioral changes at the OS level, it requires elevated Android system permissions:

* **Usage Access (`OPSTR_GET_USAGE_STATS`):** Required to detect when the user launches a targeted, distracting app.
* **Display Over Other Apps (`SYSTEM_ALERT_WINDOW`):** Required to aggressively draw the "Ctrl Intercepted" quiz overlay on top of the blocked application.
* **Post Notifications:** Required to keep the Foreground Service alive and alert the user when their Study or Break timers hit 100%.

---

## ⚙️ 5. How the App Works (Frontend Logic)
The Android client is responsible for the behavioral paradigm shift of the app:

1. **Material Upload:** The user selects a PDF or image of their notes. The app sends this to the backend for processing and vectorization.
2. **Session Configuration:** The user sets a study duration, a break duration (or uses the Auto-Adjusted Break mathematical decay formula), and selects which installed apps to block.
3. **Background Interception:** Once a session starts, a resilient Foreground Service runs continuously. It utilizes Android's `UsageStatsManager` to monitor the top-running app on the screen.
4. **The Lock Screen:** If the user opens a targeted distracting app (e.g., TikTok, Instagram), the Foreground Service detects it and instantly launches an impenetrable overlay. 
5. **The Quiz Engine:** The user inputs where they left off in their reading. The app fetches a dynamically generated JSON quiz from the backend's RAG pipeline. The user must score 50% or higher to dismiss the overlay and unlock the app for their break.
6. **Grace Period:** A 5-minute grace period allows users to safely cancel or edit a newly created session without triggering an anti-cheat lockdown.

---

## 🛠️ Tech Stack
* **Language:** Kotlin
* **UI Toolkit:** Jetpack Compose (Material Design 3)
* **Navigation:** Navigation Compose
* **Networking:** Retrofit2 & OkHttp
* **Architecture:** Foreground Services, Coroutines

