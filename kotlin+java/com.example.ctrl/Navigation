package com.example.ctrl

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.provider.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.navigation.compose.*

@Composable
fun CtrlApp(startDestination: String = "splash") {
    val navController = rememberNavController()
    val context = LocalContext.current // Used to launch our service

    var isSessionActive by remember { mutableStateOf(false) }
    var studyMinutes by remember { mutableStateOf(0) }
    var breakMinutes by remember { mutableStateOf(0) }
    var selectedFileName by remember { mutableStateOf("") }
    val blockedAppsList = remember { mutableStateListOf<String>() }

    NavHost(navController = navController, startDestination = startDestination) {

        composable("splash") {
            SplashScreen(onNavigateToHome = {
                navController.navigate("home") { popUpTo("splash") { inclusive = true } }
            })
        }

        composable("home") {
            HomeScreen(isActive = isSessionActive, studyTimeMins = studyMinutes, blockedApps = blockedAppsList, selectedFileName = selectedFileName, onStartSession = { navController.navigate("create_step_1") }, onUpdateSession = { navController.navigate("session_dashboard") })
        }

        composable("create_step_1") {
            CreateSessionStep1(onNext = { minutes -> studyMinutes = minutes; navController.navigate("create_step_2") }, onBack = { navController.popBackStack() })
        }

        composable("create_step_2") {
            CreateSessionStep2(studyTimeMins = studyMinutes, onNext = { minutes -> breakMinutes = minutes; navController.navigate("create_step_3") })
        }

        composable("create_step_3") {
            CreateSessionStep3(currentFileName = selectedFileName, onFileSelected = { fileName -> selectedFileName = fileName }, onNext = { navController.navigate("create_step_4") })
        }

        composable("create_step_4") {
            CreateSessionStep4(globalBlockedApps = blockedAppsList, onNext = { navController.navigate("session_overview") })
        }

        composable("session_overview") {
            SessionOverviewScreen(
                studyTimeMins = studyMinutes, breakTimeMins = breakMinutes, blockedCount = blockedAppsList.size, fileName = selectedFileName,
                onStartSession = {
                    // 1. Check if we have permission to view apps
                    val appOps = context.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
                    val mode = appOps.unsafeCheckOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, android.os.Process.myUid(), context.packageName)

                    if (mode != AppOpsManager.MODE_ALLOWED) {
                        // Ask user for permission if not granted
                        context.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                    } else {
                        // 2. Start the App Blocker Background Service!
                        isSessionActive = true
                        val serviceIntent = Intent(context, AppBlockerService::class.java).apply {
                            putStringArrayListExtra("BLOCKED_APPS", ArrayList(blockedAppsList))
                        }
                        context.startForegroundService(serviceIntent)

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
            EndSessionScreen(
                minutesLeft = studyMinutes,
                onConfirm = { navController.navigate("quiz") },
                onCancel = { navController.popBackStack() }
            )
        }

        composable("intercept_input") {
            InterceptInputScreen(fileName = selectedFileName, onGenerateQuiz = { navController.navigate("quiz") })
        }

        composable("quiz") {
            QuizScreen(
                onPass = {
                    isSessionActive = false
                    blockedAppsList.clear()
                    selectedFileName = ""

                    // STOP THE BLOCKER SERVICE!
                    context.stopService(Intent(context, AppBlockerService::class.java))

                    navController.navigate("unlock") { popUpTo("home") }
                },
                onFailReturn = { navController.popBackStack("session_dashboard", false) }
            )
        }

        composable("unlock") {
            UnlockScreen(onOpenApp = { }, onBackToStudy = { navController.popBackStack("home", false) })
        }
    }
}
