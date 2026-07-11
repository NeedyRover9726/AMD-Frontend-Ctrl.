package com.example.ctrl

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Log
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import kotlin.time.Duration.Companion.seconds

// --- DATA CLASSES FOR DYNAMIC APP STATE ---
data class AppInfo(val name: String, val packageName: String)
data class StudyMaterial(val name: String, val uri: Uri)

@Composable
fun CtrlApp(
    initialNavigateTo: String?,
    registerIntentListener: ((String) -> Unit) -> Unit,
) {
    val navController = rememberNavController()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isSessionActive by remember { mutableStateOf(value = false) }
    var studyMinutes by remember { mutableIntStateOf(0) }
    var breakMinutes by remember { mutableIntStateOf(0) }
    var selectedFileName by remember { mutableStateOf("") }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var readingProgress by remember { mutableFloatStateOf(0.0f) }
    var isReadingStarted by remember { mutableStateOf(false) }
    var quizTopic by remember { mutableStateOf("") }

    val blockedAppsList = remember { mutableStateListOf<String>() }

    // --- GLOBAL STATE FOR UPLOADED MATERIALS ---
    val uploadedMaterials = remember { mutableStateListOf<StudyMaterial>() }
    var isUploading by remember { mutableStateOf(false) } // New state for the loading spinner

    // --- STATE FOR REAL API QUIZZES & DYNAMIC APPS ---
    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    LaunchedEffect(Unit) {
        // Dynamically load installed apps when the app starts
        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val appList = mutableListOf<AppInfo>()
        for (packageInfo in packages) {
            if (((packageInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0) || packageInfo.packageName.contains("youtube") || packageInfo.packageName.contains("chrome")) {
                val appName = pm.getApplicationLabel(packageInfo).toString()
                appList.add(AppInfo(appName, packageInfo.packageName))
            }
        }
        installedApps = appList.sortedBy { it.name }
    }

    LaunchedEffect(initialNavigateTo) {
        if (!initialNavigateTo.isNullOrEmpty()) {
            navController.navigate(initialNavigateTo) {
                popUpTo("home") { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
    }

    DisposableEffect(Unit) {
        registerIntentListener { dest ->
            navController.navigate(dest) {
                popUpTo("home") { saveState = true }
                launchSingleTop = true
                restoreState = true
            }
        }
        onDispose { registerIntentListener {} }
    }

    NavHost(navController = navController, startDestination = "splash") {
        composable("splash") {
            SplashScreen {
                navController.navigate("home") {
                    popUpTo("splash") { inclusive = true }
                }
            }
        }
        composable("home") {
            // Mock increment logic
            LaunchedEffect(isSessionActive, isReadingStarted) {
                if (isSessionActive && isReadingStarted && readingProgress < 1.0f) {
                    while (readingProgress < 1.0f) {
                        delay(5.seconds)
                        readingProgress = (readingProgress + 0.1f).coerceAtMost(1.0f)
                    }
                    if (readingProgress >= 1.0f) {
                        navController.navigate("quiz") {
                            popUpTo("home") { saveState = true }
                        }
                    }
                }
            }

            HomeScreen(
                isActive = isSessionActive,
                studyTimeMins = studyMinutes,
                blockedApps = blockedAppsList,
                selectedFileName = selectedFileName,
                readingProgress = readingProgress,
                uploadedMaterials = uploadedMaterials,
                isUploading = isUploading, // Passing the loading state down
                onStartSession = { navController.navigate("create_step_1") },
                onUpdateSession = {
                    isReadingStarted = true
                    navController.navigate("session_dashboard")
                },
                onOpenFile = {
                    selectedFileUri?.let { uri ->
                        isReadingStarted = true
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            setDataAndType(uri, context.contentResolver.getType(uri) ?: "application/pdf")
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        }
                        context.startActivity(Intent.createChooser(intent, "Open study material"))
                    }
                },
                onMaterialUploaded = { name, uri ->
                    // FIXED: Actually sending the document to Render over the internet!
                    coroutineScope.launch {
                        isUploading = true
                        try {
                            // Read the file bytes from the URI
                            val inputStream = context.contentResolver.openInputStream(uri)
                            val bytes = inputStream?.use { it.readBytes() }

                            if (bytes != null) {
                                // Prepare the Multipart request
                                val requestBody = RequestBody.create(MediaType.parse("application/pdf"), bytes)
                                val filePart = MultipartBody.Part.createFormData("file", name, requestBody)
                                val titlePart = RequestBody.create(MediaType.parse("text/plain"), name)

                                // Send to the backend
                                val response = RetrofitClient.apiService.uploadMaterial(titlePart, filePart)
                                Log.d("Upload", "Success: ${response.status}")

                                // Once confirmed by server, add to UI
                                if (uploadedMaterials.none { it.uri == uri }) {
                                    uploadedMaterials.add(StudyMaterial(name, uri))
                                }
                            }
                        } catch (e: Exception) {
                            Log.e("Upload", "Failed to upload document", e)
                        } finally {
                            isUploading = false // Stop the loading spinner
                        }
                    }
                }
            )
        }
        composable("create_step_1") {
            CreateSessionStep1(onNext = { minutes -> studyMinutes = minutes; navController.navigate("create_step_2") }, onBack = { navController.popBackStack() })
        }
        composable("create_step_2") {
            CreateSessionStep2(studyTimeMins = studyMinutes, onNext = { minutes -> breakMinutes = minutes; navController.navigate("create_step_3") }, onBack = { navController.popBackStack() })
        }
        composable("create_step_3") {
            CreateSessionStep3(
                uploadedMaterials = uploadedMaterials,
                currentFileName = selectedFileName,
                onFileSelected = { fileName, uri ->
                    selectedFileName = fileName
                    selectedFileUri = uri
                },
                onNext = { navController.navigate("create_step_4") },
                onBack = { navController.popBackStack() }
            )
        }
        composable("create_step_4") {
            CreateSessionStep4(globalBlockedApps = blockedAppsList, installedApps = installedApps, onNext = { navController.navigate("session_overview") }, onBack = { navController.popBackStack() })
        }
        composable("session_overview") {
            SessionOverviewScreen(
                studyTimeMins = studyMinutes, breakTimeMins = breakMinutes, blockedCount = blockedAppsList.size, fileName = selectedFileName,
                onStartSession = {
                    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                    val mode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        appOps.unsafeCheckOpRaw(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
                    } else {
                        @Suppress("DEPRECATION")
                        appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
                    }
                    if (mode != AppOpsManager.MODE_ALLOWED) {
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    } else {
                        isSessionActive = true
                        readingProgress = 0.0f
                        isReadingStarted = false
                        val serviceIntent = Intent(context, AppBlockerService::class.java).apply {
                            putStringArrayListExtra("BLOCKED_APPS", ArrayList(blockedAppsList))
                        }
                        context.startService(serviceIntent)
                        navController.navigate("home") { popUpTo("home") { inclusive = true } }
                    }
                },
                onEditSetup = { navController.popBackStack("create_step_1", inclusive = false) }
            )
        }
        composable("session_dashboard") {
            SessionDashboardScreen(isActive = isSessionActive, fileName = selectedFileName, studyTimeMins = studyMinutes, blockedCount = blockedAppsList.size, onCreateSession = { navController.navigate("create_step_1") }, onBack = { navController.popBackStack() }, onUpdate = { navController.navigate("intercept_input") }, onCancel = { navController.navigate("end_session_confirm") })
        }
        composable("end_session_confirm") {
            EndSessionScreen(minutesLeft = studyMinutes, onConfirm = { navController.navigate("quiz") }, onCancel = { navController.popBackStack() })
        }
        composable("intercept_input") {
            InterceptInputScreen(
                fileName = selectedFileName,
                onGenerateQuiz = { topic ->
                    quizTopic = topic
                    navController.navigate("quiz")
                }
            )
        }
        composable("quiz") {
            QuizScreen(
                topic = quizTopic,
                fileName = selectedFileName,
                onPass = {
                    isSessionActive = false
                    readingProgress = 0.0f
                    isReadingStarted = false
                    blockedAppsList.clear()
                    selectedFileName = ""
                    selectedFileUri = null
                    quizTopic = ""
                    context.stopService(Intent(context, AppBlockerService::class.java))
                    navController.navigate("unlock") { popUpTo("home") }
                },
                onFailReturn = { navController.popBackStack("home", false) }
            )
        }
        composable("unlock") {
            UnlockScreen(onOpenApp = { }, onBackToStudy = { navController.popBackStack("home", false) })
        }
    }
}
