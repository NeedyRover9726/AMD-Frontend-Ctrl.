package com.example.ctrl

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.*
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.seconds

@Composable
fun CtrlApp(
    initialNavigateTo: String?,
    registerIntentListener: ((String) -> Unit) -> Unit,
) {
    val navController = rememberNavController()
    val context = LocalContext.current

    var isSessionActive by remember { mutableStateOf(value = false) }
    var studyMinutes by remember { mutableIntStateOf(0) }
    var breakMinutes by remember { mutableIntStateOf(0) }
    var selectedFileName by remember { mutableStateOf("") }
    var selectedFileUri by remember { mutableStateOf<Uri?>(null) }
    var readingProgress by remember { mutableFloatStateOf(0.0f) }
    var isReadingStarted by remember { mutableStateOf(false) }
    var quizTopic by remember { mutableStateOf("") }
    val blockedAppsList = remember { mutableStateListOf<String>() }

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
            SplashScreen(onNavigateToHome = { navController.navigate("home") { popUpTo("splash") { inclusive = true } } })
        }
        composable("home") {
            // Mock increment logic: Only starts after a simulated "open" event
            LaunchedEffect(isSessionActive, isReadingStarted) {
                if (isSessionActive && isReadingStarted && readingProgress < 1.0f) {
                    while (readingProgress < 1.0f) {
                        delay(5.seconds) // Slower increment for realism
                        readingProgress = (readingProgress + 0.1f).coerceAtMost(1.0f)
                    }
                    // AUTO-REDIRECT TO QUIZ AT 100%
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
                onStartSession = { navController.navigate("create_step_1") },
                onUpdateSession = { 
                    // Simulate opening the file when clicking update/dashboard
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
            CreateSessionStep3(currentFileName = selectedFileName, onFileSelected = { fileName, uri -> selectedFileName = fileName; selectedFileUri = uri }, onNext = { navController.navigate("create_step_4") }, onBack = { navController.popBackStack() })
        }
        composable("create_step_4") {
            CreateSessionStep4(globalBlockedApps = blockedAppsList, onNext = { navController.navigate("session_overview") }, onBack = { navController.popBackStack() })
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
            InterceptInputScreen(fileName = selectedFileName, onGenerateQuiz = { topic -> 
                quizTopic = topic
                navController.navigate("quiz") 
            })
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
