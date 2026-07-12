package com.example.ctrl

import android.Manifest
import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.LocalActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.core.net.toUri
import androidx.navigation.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.MediaType
import okhttp3.MultipartBody
import okhttp3.RequestBody
import okio.BufferedSink
import kotlin.time.Duration.Companion.seconds

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
    val sessionManager = remember { SessionManager(context) }

    // FIXED: Track exactly which screen the user is on
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    var isSessionActive by remember { mutableStateOf(sessionManager.isSessionActive) }
    var isBreakMode by remember { mutableStateOf(sessionManager.isBreakMode) }
    var studyMinutes by remember { mutableIntStateOf(sessionManager.studyMinutes) }
    var breakMinutes by remember { mutableIntStateOf(sessionManager.breakMinutes) }
    var elapsedMinutes by remember { mutableFloatStateOf(sessionManager.elapsedMinutes) }

    var selectedFileName by remember { mutableStateOf(sessionManager.selectedFileName) }
    var selectedFileUri by remember { mutableStateOf(sessionManager.selectedFileUri) }

    var readingProgress by remember { mutableFloatStateOf(0.0f) }
    var isReadingStarted by remember { mutableStateOf(false) }
    var quizTopic by remember { mutableStateOf("") }
    var quizType by remember { mutableStateOf("Multiple Choice") }

    val blockedAppsList = remember { mutableStateListOf<String>().apply { addAll(sessionManager.blockedApps) } }
    val uploadedMaterials = remember { mutableStateListOf<StudyMaterial>() }
    var isUploading by remember { mutableStateOf(false) }

    var installedApps by remember { mutableStateOf<List<AppInfo>>(emptyList()) }

    // FIXED: Protects the UI from being interrupted if the user is busy in the intervention flow
    LaunchedEffect(currentRoute) {
        val isProtectedScreen = currentRoute in listOf("intercept_input", "quiz", "end_session_confirm", "unlock")
        val protectIntent = Intent(context, AppBlockerService::class.java).apply {
            action = AppBlockerService.ACTION_PROTECT_UI
            putExtra(AppBlockerService.EXTRA_PROTECTED_STATE, isProtectedScreen)
        }
        context.startService(protectIntent)
    }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (!isGranted) {
            Toast.makeText(context, "Please enable notifications to receive study and break alerts!", Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val isGranted = ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
            if (!isGranted) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        try {
            val response = RetrofitClient.apiService.getMaterials()
            response.savedMaterials.forEach { serverName ->
                if (uploadedMaterials.none { it.name == serverName }) {
                    uploadedMaterials.add(StudyMaterial(serverName, Uri.EMPTY))
                }
            }
        } catch (e: Exception) {
            Log.e("FetchMaterials", "Failed to fetch materials", e)
        }

        val pm = context.packageManager
        val packages = pm.getInstalledApplications(PackageManager.GET_META_DATA)
        val appList = mutableListOf<AppInfo>()
        val myPackage = context.packageName

        for (packageInfo in packages) {
            if (packageInfo.packageName != myPackage && ((packageInfo.flags and ApplicationInfo.FLAG_SYSTEM) == 0 || packageInfo.packageName.contains("youtube") || packageInfo.packageName.contains("chrome"))) {
                val appName = pm.getApplicationLabel(packageInfo).toString()
                appList.add(AppInfo(appName, packageInfo.packageName))
            }
        }
        installedApps = appList.sortedBy { it.name }
    }

    LaunchedEffect(isSessionActive) {
        if (isSessionActive) {
            while (true) {
                elapsedMinutes = sessionManager.elapsedMinutes
                isBreakMode = sessionManager.isBreakMode
                delay(1.seconds)
            }
        }
    }

    DisposableEffect(Unit) {
        registerIntentListener { dest ->
            navController.navigate(dest) {
                popUpTo(0) { inclusive = true }
                launchSingleTop = true
            }
        }
        onDispose { registerIntentListener {} }
    }

    val startDest = initialNavigateTo ?: "splash"

    NavHost(navController = navController, startDestination = startDest) {
        composable("splash") {
            SplashScreen(onNavigateToHome = {
                navController.navigate("home") { popUpTo("splash") { inclusive = true } }
            })
        }
        composable("home") {
            LaunchedEffect(isSessionActive, isReadingStarted) {
                if (isSessionActive && isReadingStarted && readingProgress < 1.0f) {
                    while (readingProgress < 1.0f) {
                        delay(5.seconds)
                        readingProgress = (readingProgress + 0.1f).coerceAtMost(1.0f)
                    }
                    if (readingProgress >= 1.0f) {
                        navController.navigate("quiz") { popUpTo("home") { saveState = true } }
                    }
                }
            }

            HomeScreen(
                isActive = isSessionActive,
                isBreakMode = isBreakMode,
                studyTimeMins = studyMinutes,
                breakTimeMins = breakMinutes,
                elapsedMinutes = elapsedMinutes,
                blockedApps = blockedAppsList,
                installedApps = installedApps,
                selectedFileName = selectedFileName,
                uploadedMaterials = uploadedMaterials,
                isUploading = isUploading,
                onStartSession = { navController.navigate("create_step_1") },
                onUpdateSession = {
                    isReadingStarted = true
                    navController.navigate("session_dashboard")
                },
                onOpenFile = {
                    selectedFileUri?.let { uri ->
                        isReadingStarted = true
                        if (uri != Uri.EMPTY) {
                            try {
                                val intent = Intent(Intent.ACTION_VIEW).apply {
                                    setDataAndType(uri, context.contentResolver.getType(uri) ?: "application/pdf")
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(intent, "Open study material"))
                            } catch (e: Exception) {
                                Log.e("OpenFile", "Could not open file locally", e)
                            }
                        }
                    }
                },
                onDeleteMaterial = { material ->
                    uploadedMaterials.remove(material)
                    if (selectedFileName == material.name) {
                        selectedFileName = ""
                        selectedFileUri = null
                    }
                },
                onMaterialUploaded = { name, uri ->
                    coroutineScope.launch {
                        isUploading = true
                        try {
                            val mimeType = context.contentResolver.getType(uri) ?: "application/octet-stream"

                            val requestBody = object : RequestBody() {
                                override fun contentType(): MediaType? = MediaType.parse(mimeType)
                                override fun writeTo(sink: BufferedSink) {
                                    context.contentResolver.openInputStream(uri)?.use { inputStream ->
                                        val buffer = ByteArray(8192)
                                        var bytesRead: Int
                                        while (inputStream.read(buffer).also { bytesRead = it } != -1) {
                                            sink.write(buffer, 0, bytesRead)
                                        }
                                    }
                                }
                            }

                            val filePart = MultipartBody.Part.createFormData("file", name, requestBody)
                            val titlePart = RequestBody.create(MediaType.parse("text/plain"), name)

                            val response = RetrofitClient.apiService.uploadMaterial(titlePart, filePart)

                            if (response.status == "processing" || response.status == "success") {
                                Toast.makeText(context,"Document processing in background! Please wait 5-10s.", Toast.LENGTH_LONG).show()

                                val existingIndex = uploadedMaterials.indexOfFirst { it.name == name }
                                if (existingIndex != -1) {
                                    uploadedMaterials[existingIndex] = StudyMaterial(name, uri)
                                } else {
                                    uploadedMaterials.add(StudyMaterial(name, uri))
                                }

                                launch {
                                    delay(6.seconds)
                                    try {
                                        val materialsResponse = RetrofitClient.apiService.getMaterials()
                                        materialsResponse.savedMaterials.forEach { serverName ->
                                            if (uploadedMaterials.none { it.name == serverName }) {
                                                uploadedMaterials.add(StudyMaterial(serverName, Uri.EMPTY))
                                            }
                                        }
                                    } catch (e: Exception) {
                                        Log.e("Upload", "Delayed fetch failed", e)
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Toast.makeText(context, "Upload Failed. Check internet connection.", Toast.LENGTH_SHORT).show()
                            Log.e("Upload", "Failed to upload document", e)
                        } finally {
                            isUploading = false
                        }
                    }
                }
            )
        }
        composable("create_step_1") {
            CreateSessionStep1(onNext = { minutes ->
                studyMinutes = minutes
                sessionManager.studyMinutes = minutes
                navController.navigate("create_step_2")
            }, onBack = { navController.popBackStack() })
        }
        composable("create_step_2") {
            CreateSessionStep2(studyTimeMins = studyMinutes, onNext = { minutes ->
                breakMinutes = minutes
                sessionManager.breakMinutes = minutes
                navController.navigate("create_step_3")
            }, onBack = { navController.popBackStack() })
        }
        composable("create_step_3") {
            CreateSessionStep3(
                uploadedMaterials = uploadedMaterials,
                currentFileName = selectedFileName,
                onFileSelected = { fileName, uri ->
                    selectedFileName = fileName
                    selectedFileUri = uri
                    sessionManager.selectedFileName = fileName
                    sessionManager.selectedFileUri = uri
                },
                onDeleteMaterial = { material ->
                    uploadedMaterials.remove(material)
                    if (selectedFileName == material.name) {
                        selectedFileName = ""
                        selectedFileUri = null
                        sessionManager.selectedFileName = ""
                        sessionManager.selectedFileUri = null
                    }
                },
                onNext = { navController.navigate("create_step_4") },
                onBack = { navController.popBackStack() }
            )
        }
        composable("create_step_4") {
            CreateSessionStep4(globalBlockedApps = blockedAppsList, installedApps = installedApps, onNext = {
                sessionManager.blockedApps = blockedAppsList
                navController.navigate("session_overview")
            }, onBack = { navController.popBackStack() })
        }
        composable("session_overview") {
            SessionOverviewScreen(
                studyTimeMins = studyMinutes, breakTimeMins = breakMinutes, blockedCount = blockedAppsList.size, fileName = selectedFileName,
                onStartSession = {
                    if (!Settings.canDrawOverlays(context)) {
                        Toast.makeText(context, "Please ALLOW 'Display over other apps' to enable the App Blocker", Toast.LENGTH_LONG).show()
                        val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, "package:${context.packageName}".toUri())
                        context.startActivity(intent)
                    } else {
                        val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                        val mode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            appOps.unsafeCheckOpRaw(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
                        } else {
                            @Suppress("DEPRECATION")
                            appOps.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)
                        }
                        if (mode != AppOpsManager.MODE_ALLOWED) {
                            Toast.makeText(context, "Please ALLOW 'Usage Access' so Ctrl. can detect when you open an app.", Toast.LENGTH_LONG).show()
                            context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                        } else {
                            isSessionActive = true
                            isBreakMode = false
                            elapsedMinutes = 0f
                            readingProgress = 0.0f
                            isReadingStarted = false

                            sessionManager.isSessionActive = true
                            sessionManager.isBreakMode = false
                            sessionManager.studyMinutes = studyMinutes
                            sessionManager.breakMinutes = breakMinutes
                            sessionManager.elapsedMinutes = 0f
                            sessionManager.selectedFileName = selectedFileName
                            sessionManager.selectedFileUri = selectedFileUri
                            sessionManager.blockedApps = blockedAppsList.toList()

                            val serviceIntent = Intent(context, AppBlockerService::class.java).apply {
                                putStringArrayListExtra("BLOCKED_APPS", ArrayList(blockedAppsList))
                            }
                            context.startService(serviceIntent)
                            navController.navigate("home") { popUpTo("home") { inclusive = true } }
                        }
                    }
                },
                onEditSetup = { navController.popBackStack("create_step_1", inclusive = false) }
            )
        }
        composable("session_dashboard") {
            SessionDashboardScreen(
                isActive = isSessionActive,
                isBreakMode = isBreakMode,
                fileName = selectedFileName,
                studyTimeMins = studyMinutes,
                breakTimeMins = breakMinutes,
                elapsedMinutes = elapsedMinutes,
                blockedCount = blockedAppsList.size,
                onCreateSession = { navController.navigate("create_step_1") },
                onBack = { navController.popBackStack() },
                onUpdate = { navController.navigate("intercept_input") },
                onCancel = { navController.navigate("end_session_confirm") }
            )
        }
        composable("end_session_confirm") {
            val targetMins = if (isBreakMode) breakMinutes else studyMinutes
            EndSessionScreen(
                minutesLeft = (targetMins - elapsedMinutes.toInt()).coerceAtLeast(0),
                onConfirm = { navController.navigate("intercept_input") },
                onCancel = { navController.popBackStack() }
            )
        }
        composable("intercept_input") {
            InterceptInputScreen(
                fileName = selectedFileName,
                onGenerateQuiz = { topic, type ->
                    quizTopic = topic
                    quizType = type
                    navController.navigate("quiz")
                },
                onBackToStudy = { navController.navigate("home") { popUpTo(0) } }
            )
        }
        composable("quiz") {
            val activity = LocalActivity.current

            DisposableEffect(Unit) {
                try {
                    activity?.startLockTask()
                } catch (e: Exception) {
                    Log.e("AntiCheat", "Could not hardware-pin screen", e)
                }

                val lockIntent = Intent(context, AppBlockerService::class.java).apply {
                    action = AppBlockerService.ACTION_QUIZ_LOCKDOWN
                    putExtra(AppBlockerService.EXTRA_LOCKDOWN_STATE, true)
                }
                context.startService(lockIntent)

                onDispose {
                    try {
                        activity?.stopLockTask()
                    } catch (_: Exception) {}

                    val unlockIntent = Intent(context, AppBlockerService::class.java).apply {
                        action = AppBlockerService.ACTION_QUIZ_LOCKDOWN
                        putExtra(AppBlockerService.EXTRA_LOCKDOWN_STATE, false)
                    }
                    context.startService(unlockIntent)
                }
            }

            QuizScreen(
                topic = quizTopic,
                fileName = selectedFileName,
                onPass = {
                    isSessionActive = false
                    isBreakMode = false
                    elapsedMinutes = 0f
                    readingProgress = 0.0f
                    isReadingStarted = false
                    blockedAppsList.clear()
                    selectedFileName = ""
                    selectedFileUri = null
                    quizTopic = ""

                    sessionManager.clearSession()

                    context.stopService(Intent(context, AppBlockerService::class.java))
                    navController.navigate("unlock") { popUpTo(0) }
                },
                onFailReturn = { navController.navigate("home") { popUpTo(0) } }
            )
        }
        composable("unlock") {
            UnlockScreen(
                onOpenApp = {
                    val homeIntent = Intent(Intent.ACTION_MAIN).apply {
                        addCategory(Intent.CATEGORY_HOME)
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK
                    }
                    context.startActivity(homeIntent)
                    navController.navigate("home") { popUpTo(0) }
                },
                onBackToStudy = { navController.navigate("home") { popUpTo(0) } }
            )
        }
    }
}
