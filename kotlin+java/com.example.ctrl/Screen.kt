@file:OptIn(ExperimentalMaterial3Api::class)

package com.example.ctrl

import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import kotlinx.coroutines.delay
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.seconds

import com.example.ctrl.ui.theme.*

// --- 0. SPLASH SCREEN ---
@Composable
fun SplashScreen(onNavigateToHome: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(2.seconds)
        onNavigateToHome()
    }
    Box(
        modifier = Modifier.fillMaxSize().background(BgMidnight),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("Ctrl.", color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp)
            Spacer(modifier = Modifier.height(24.dp))
            CircularProgressIndicator(color = BtnElectricPurple, modifier = Modifier.size(32.dp))
        }
    }
}

// --- 1. HOME SCREEN ---
@Composable
fun HomeScreen(
    isActive: Boolean,
    studyTimeMins: Int,
    blockedApps: List<String>,
    selectedFileName: String,
    readingProgress: Float,
    onStartSession: () -> Unit,
    onUpdateSession: () -> Unit,
    onOpenFile: () -> Unit = {}
) {
    Column(modifier = Modifier.fillMaxSize().background(BgMidnight).padding(horizontal = 24.dp).padding(top = 48.dp)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {

            Text("Ctrl.", color = BtnElectricPurple, fontSize = 24.sp, fontWeight = FontWeight.Bold)

            IconButton(onClick = { }, modifier = Modifier.background(TopLogoBg, shape = RoundedCornerShape(50)).size(36.dp)) {
                Icon(imageVector = Icons.Outlined.Settings, contentDescription = "Settings", tint = TextMutedLilac, modifier = Modifier.size(20.dp))
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (!isActive) {
            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(GradientStart, GradientEnd))).padding(24.dp)) {
                Column {
                    Text("NO ACTIVE SESSION", color = Color.White.copy(alpha = 0.7f), fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("TAKE CONTROL OF\nYOUR TIME.", color = TextWhite, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, lineHeight = 34.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Ctrl intercepts distracting apps and makes you earn\nyour screen time with an AI quiz.", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, lineHeight = 16.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onStartSession, colors = ButtonDefaults.buttonColors(containerColor = BtnElectricPurple), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), contentPadding = PaddingValues(vertical = 16.dp)) {
                        Icon(imageVector = Icons.Default.PlayArrow, contentDescription = "Start", tint = Color.White, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Start Ctrl Intercept", color = TextWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text("STUDY MATERIALS", color = TextMutedLilac, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 12.dp))
            Box(modifier = Modifier.fillMaxWidth().dashedBorder(BorderMutedViolet, 1.dp, 16.dp).padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Outlined.Folder, contentDescription = "Folder", tint = TextDarkGrayPurple, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No study materials selected", color = TextDarkGrayPurple, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Start a session to add materials", color = TextDarkGrayPurple.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text("BLOCKED APPS", color = TextMutedLilac, fontWeight = FontWeight.Bold, fontSize = 10.sp, letterSpacing = 1.sp, modifier = Modifier.padding(bottom = 12.dp))
            Box(modifier = Modifier.fillMaxWidth().dashedBorder(BorderMutedViolet, 1.dp, 16.dp).padding(vertical = 32.dp), contentAlignment = Alignment.Center) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(imageVector = Icons.Outlined.Lock, contentDescription = "Lock", tint = TextDarkGrayPurple, modifier = Modifier.size(24.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("No apps blocked", color = TextDarkGrayPurple, fontWeight = FontWeight.Medium, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Start a session to select apps to block", color = TextDarkGrayPurple.copy(alpha = 0.6f), fontSize = 12.sp)
                }
            }
        } else {
            val displayHours = studyTimeMins / 60
            val displayMins = studyTimeMins % 60
            val timeLabel = if (displayHours > 0) "${displayHours}h ${displayMins}m" else "${displayMins}m"

            Box(modifier = Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp)).background(Brush.linearGradient(listOf(GradientStart, GradientEnd))).padding(20.dp)) {
                Column {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(SuccessNeonGreen, shape = RoundedCornerShape(50)))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("ACTIVE SESSION", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.End) {
                            Text(timeLabel, color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("session", color = Color.White.copy(alpha = 0.7f), fontSize = 12.sp)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(selectedFileName.ifEmpty { "Study Material" }, color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Time Configured: $timeLabel remaining • ${blockedApps.size} apps locked", color = Color.White.copy(alpha = 0.8f), fontSize = 12.sp)
                        Text("${(readingProgress * 100).toInt()}%", color = SuccessNeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(progress = { readingProgress }, modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(50)), color = SuccessNeonGreen, trackColor = Color.White.copy(alpha = 0.2f))
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = onUpdateSession, colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.15f)), modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                        Icon(imageVector = Icons.Outlined.Lock, contentDescription = "Lock", tint = TextWhite, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Update Ctrl Intercept", color = TextWhite, fontWeight = FontWeight.Medium)
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
            Text("Study Materials", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.height(12.dp))
            StudyMaterialCard(title = selectedFileName, subtitle = "Click to open and study", isActive = true, onClick = onOpenFile)

            Spacer(modifier = Modifier.height(24.dp))
            Text("BLOCKED APPS", color = TextMutedLilac, fontWeight = FontWeight.Bold, fontSize = 12.sp, modifier = Modifier.padding(bottom = 8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = CardDarkPurple), border = BorderStroke(1.dp, BorderMutedViolet), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(imageVector = Icons.Outlined.Lock, contentDescription = "Locked", tint = BtnElectricPurple)
                        Spacer(modifier = Modifier.width(16.dp))
                        Text("${blockedApps.size} Apps currently blocked", color = TextWhite, fontWeight = FontWeight.Bold)
                    }
                    if (blockedApps.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(12.dp))
                        blockedApps.forEach { app ->
                            Text("• $app", color = TextMutedLilac, fontSize = 14.sp, modifier = Modifier.padding(start = 40.dp, bottom = 4.dp))
                        }
                    }
                }
            }
        }
    }
}

// --- 2. SESSION DASHBOARD ---
@Composable
fun SessionDashboardScreen(isActive: Boolean, fileName: String, studyTimeMins: Int, blockedCount: Int, onCreateSession: () -> Unit, onBack: () -> Unit, onUpdate: () -> Unit, onCancel: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(BgMidnight).padding(24.dp).padding(top = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack, modifier = Modifier.background(CardDarkPurple, shape = RoundedCornerShape(50))) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextWhite) }
            Spacer(modifier = Modifier.width(16.dp))
            Column {
                Text("CTRL INTERCEPT", color = BtnElectricPurple, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Text("Session Dashboard", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        if (!isActive) {
            Card(colors = CardDefaults.cardColors(containerColor = CardDarkPurple), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(48.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(modifier = Modifier.size(56.dp).background(BtnElectricPurple.copy(alpha=0.1f), shape = RoundedCornerShape(16.dp)), contentAlignment = Alignment.Center) {
                        Icon(Icons.Outlined.Lock, contentDescription = "Lock", tint = BtnElectricPurple, modifier = Modifier.size(28.dp))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("No Active Session", color = TextWhite, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Start a focus session to begin blocking distracting apps.", color = TextMutedLilac, fontSize = 12.sp, textAlign = TextAlign.Center)
                }
            }
            Spacer(modifier = Modifier.height(24.dp))
            Button(onClick = onCreateSession, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = BtnElectricPurple), shape = RoundedCornerShape(16.dp)) {
                Icon(Icons.Default.PlayArrow, contentDescription = "Play", tint = TextWhite)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Create Session", color = TextWhite, fontWeight = FontWeight.Bold)
            }
        } else {
            Card(colors = CardDefaults.cardColors(containerColor = BorderMutedViolet), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(8.dp).background(SuccessNeonGreen, shape = RoundedCornerShape(50)))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("ONGOING SESSION", color = TextMutedLilac, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(fileName.ifEmpty { "Study Material" }, color = TextWhite, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("$studyTimeMins min left  •  $blockedCount apps blocked", color = TextMutedLilac, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    LinearProgressIndicator(progress = { 0.3f }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)), color = BtnElectricPurple, trackColor = CardDarkPurple)
                }
            }

            Spacer(modifier = Modifier.height(32.dp))
            Text("ACTIONS", color = TextDarkGrayPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(bottom = 12.dp))

            Card(onClick = onUpdate, colors = CardDefaults.cardColors(containerColor = CardDarkPurple), border = BorderStroke(1.dp, BorderMutedViolet), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(40.dp).background(BtnElectricPurple.copy(alpha=0.2f), shape = RoundedCornerShape(50)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = BtnElectricPurple) }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Update Session", color = TextWhite, fontWeight = FontWeight.Bold)
                        Text("Pass a quiz first, then edit", color = TextMutedLilac, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = "Go", tint = TextDarkGrayPurple)
                }
            }

            Card(onClick = onCancel, colors = CardDefaults.cardColors(containerColor = Color(0xFF1A0B14)), border = BorderStroke(1.dp, Color(0xFF4A1C2C)), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(modifier = Modifier.size(40.dp).background(Color(0xFFE53935).copy(alpha=0.2f), shape = RoundedCornerShape(50)), contentAlignment = Alignment.Center) { Icon(Icons.Outlined.Delete, contentDescription = "Delete", tint = Color(0xFFE53935)) }
                    Spacer(modifier = Modifier.width(16.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Cancel Session", color = Color(0xFFFF8A80), fontWeight = FontWeight.Bold)
                        Text("Pass a quiz to confirm exit", color = TextMutedLilac, fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, contentDescription = "Go", tint = TextDarkGrayPurple)
                }
            }
        }
    }
}

// --- NEW: END SESSION SCREEN ---
@Composable
fun EndSessionScreen(minutesLeft: Int, onConfirm: () -> Unit, onCancel: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2A0D14), BgMidnight))).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(modifier = Modifier.size(80.dp).background(Color(0xFFE53935).copy(alpha=0.2f), RoundedCornerShape(24.dp)).border(1.dp, Color(0xFFE53935).copy(alpha=0.5f), RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Close, contentDescription = "End", tint = Color(0xFFE53935), modifier = Modifier.size(40.dp))
        }
        Spacer(modifier = Modifier.height(32.dp))
        Text("END SESSION?", color = Color(0xFFFF8A80), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text("You still have $minutesLeft minutes left.", color = TextWhite, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Ending will unblock all apps and clear your session.", color = TextMutedLilac, textAlign = TextAlign.Center, fontSize = 14.sp)

        Spacer(modifier = Modifier.height(48.dp))
        Button(onClick = onConfirm, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
            Text("End Session", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        OutlinedButton(onClick = onCancel, colors = ButtonDefaults.outlinedButtonColors(containerColor = Color(0xFF1B2C10).copy(alpha=0.3f)), border = BorderStroke(1.dp, SuccessNeonGreen.copy(alpha=0.5f)), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) {
            Text("Keep Studying", color = SuccessNeonGreen, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

// --- 3. INTERCEPT INPUT SCREEN ---
@Composable
fun InterceptInputScreen(fileName: String, onGenerateQuiz: (String) -> Unit) {
    var inputText by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().background(BgMidnight).padding(24.dp).padding(top = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {

        Text("Ctrl.", color = TextWhite, fontSize = 48.sp, fontWeight = FontWeight.Bold)

        Spacer(modifier = Modifier.height(32.dp))
        Text("CTRL INTERCEPTED", color = BtnElectricPurple, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text("What did you\njust read?", color = TextWhite, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Tell us where you are in\n${fileName.ifEmpty { "your document" }}", color = TextMutedLilac, textAlign = TextAlign.Center)

        Spacer(modifier = Modifier.height(32.dp))
        OutlinedTextField(
            value = inputText,
            onValueChange = { inputText = it },
            placeholder = { Text("e.g. Chapters 3-4 or Pages 38-55", color = TextDarkGrayPurple) },
            modifier = Modifier.fillMaxWidth(),
            colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = BtnElectricPurple, unfocusedBorderColor = BorderMutedViolet, unfocusedContainerColor = CardDarkPurple, focusedContainerColor = CardDarkPurple, focusedTextColor = TextWhite, unfocusedTextColor = TextWhite),
            shape = RoundedCornerShape(16.dp)
        )

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Chip("Chapter 3") { inputText = "Chapter 3" }
            Chip("Pages 42-58") { inputText = "Pages 42-58" }
            Chip("Ch. 3-4") { inputText = "Ch. 3-4" }
        }

        Spacer(modifier = Modifier.weight(1f))
        Card(colors = CardDefaults.cardColors(containerColor = Color.Transparent), border = BorderStroke(1.dp, CardDarkPurple), shape = RoundedCornerShape(12.dp)) {
            Row(modifier = Modifier.padding(16.dp)) {
                Icon(Icons.Outlined.Info, contentDescription = "Info", tint = TextDarkGrayPurple, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(12.dp))
                Text("Question count (5-25) scales with volume of material read. You need 50%+ to unlock.", color = TextDarkGrayPurple, fontSize = 12.sp)
            }
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = { onGenerateQuiz(inputText) }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = BtnElectricPurple), shape = RoundedCornerShape(16.dp)) {
            Text("Generate Quiz >", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
        }
    }
}

// --- 4. RAG QUIZ ENGINE & ANTI-CHEAT ---
data class MockQuestion(val text: String, val options: List<String>, val correctIndex: Int)
enum class QuizState { LOADING, ACTIVE, PASSED, FAILED }

@Composable
fun QuizScreen(topic: String, fileName: String, onPass: () -> Unit, onFailReturn: () -> Unit) {
    var quizState by remember { mutableStateOf(QuizState.LOADING) }
    var currentQIndex by remember { mutableIntStateOf(0) }
    var selectedOption by remember { mutableStateOf<Int?>(null) }
    var isChecked by remember { mutableStateOf(false) }
    var score by remember { mutableIntStateOf(0) }
    var showCheatWarning by remember { mutableStateOf(false) }
    val questions = remember { mutableStateListOf<QuizQuestion>() }

    LaunchedEffect(Unit) {
        try {
            val response = RetrofitClient.apiService.generateQuiz(topic, fileName, 5)
            questions.addAll(response)
            quizState = QuizState.ACTIVE
        } catch (e: Exception) {
            // Fallback for demo if network fails
            questions.addAll(listOf(
                QuizQuestion("Which organelle is primarily responsible for ATP synthesis?", listOf("Ribosome", "Mitochondria", "Golgi apparatus", "Endoplasmic reticulum"), "Mitochondria"),
                QuizQuestion("Which phase of mitosis involves the separation of sister chromatids?", listOf("Prophase", "Metaphase", "Anaphase", "Telophase"), "Anaphase")
            ))
            quizState = QuizState.ACTIVE
        }
    }

    BackHandler(enabled = quizState == QuizState.ACTIVE) { showCheatWarning = true }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_PAUSE && quizState == QuizState.ACTIVE) showCheatWarning = true }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    if (showCheatWarning) {
        Dialog(onDismissRequest = { }, properties = DialogProperties(dismissOnBackPress = false, dismissOnClickOutside = false)) {
            Card(colors = CardDefaults.cardColors(containerColor = CardDarkPurple), border = BorderStroke(1.dp, Color(0xFFE53935)), shape = RoundedCornerShape(24.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Outlined.Block, contentDescription = "No Entry", tint = Color(0xFFE53935), modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(16.dp))
                    Text("Do Not Cheat!", color = TextWhite, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("You cannot access the home screen, back button, or recent apps during a quiz. Complete the quiz honestly to earn your access.", color = TextMutedLilac, textAlign = TextAlign.Center, fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(onClick = { showCheatWarning = false }, modifier = Modifier.fillMaxWidth().height(48.dp), colors = ButtonDefaults.buttonColors(containerColor = BtnElectricPurple), shape = RoundedCornerShape(12.dp)) { Text("Continue Quiz", color = TextWhite, fontWeight = FontWeight.Bold) }
                    Spacer(modifier = Modifier.height(12.dp))
                    TextButton(onClick = onFailReturn) { Text("Exit without unlocking", color = TextDarkGrayPurple, fontSize = 12.sp) }
                }
            }
        }
    }

    if (quizState == QuizState.FAILED) {
        val percentage = (score.toFloat() / questions.size) * 100
        Column(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF2A0D14), BgMidnight))).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
            Box(modifier = Modifier.size(80.dp).background(Color(0xFFE53935).copy(alpha=0.2f), RoundedCornerShape(24.dp)).border(1.dp, Color(0xFFE53935).copy(alpha=0.5f), RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.Close, contentDescription = "Failed", tint = Color(0xFFE53935), modifier = Modifier.size(40.dp))
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text("QUIZ FAILED", color = Color(0xFFFF8A80), fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
            Spacer(modifier = Modifier.height(12.dp))
            Text("You only got ${percentage.toInt()}%", color = TextWhite, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
            Spacer(modifier = Modifier.height(16.dp))
            Text("$score/${questions.size} correct — you need at least 50% to unlock.\nTry again!", color = TextMutedLilac, textAlign = TextAlign.Center, fontSize = 14.sp)

            Spacer(modifier = Modifier.height(48.dp))
            Button(onClick = {
                score = 0; currentQIndex = 0; selectedOption = null; isChecked = false; quizState = QuizState.ACTIVE
            }, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = BtnElectricPurple), shape = RoundedCornerShape(16.dp)) {
                Text("Try Again", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
            Spacer(modifier = Modifier.height(16.dp))
            Button(onClick = onFailReturn, modifier = Modifier.fillMaxWidth().height(56.dp), colors = ButtonDefaults.buttonColors(containerColor = CardDarkPurple), shape = RoundedCornerShape(16.dp)) {
                Text("Back to Studying", color = TextMutedLilac, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }
        }
        return
    }

    Column(modifier = Modifier.fillMaxSize().background(BgMidnight).padding(24.dp).padding(top = 32.dp)) {
        if (quizState == QuizState.LOADING) {
            Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
                CircularProgressIndicator(color = BtnElectricPurple)
                Spacer(modifier = Modifier.height(16.dp))
                Text("AI is generating your quiz...", color = TextMutedLilac)
            }
        } else if (quizState == QuizState.ACTIVE && questions.isNotEmpty()) {
            val question = questions[currentQIndex]
            val progress = (currentQIndex + 1).toFloat() / questions.size

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("QUIZ TO UNLOCK", color = BtnElectricPurple, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    Text("Question ${currentQIndex + 1}", color = TextMutedLilac, fontSize = 12.sp)
                }
                Text("${currentQIndex + 1}/${questions.size}", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            }

            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(progress = { progress }, modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(50)), color = BtnElectricPurple, trackColor = CardDarkPurple)

            Spacer(modifier = Modifier.height(32.dp))
            Text(question.question, color = TextWhite, fontSize = 20.sp, lineHeight = 28.sp)

            Spacer(modifier = Modifier.height(32.dp))

            val letters = listOf("A", "B", "C", "D")
            question.options.forEachIndexed { index, text ->
                val isSelected = selectedOption == index
                val isCorrect = text == question.correctAnswer
                val showAsCorrect = isChecked && isCorrect
                val showAsWrong = isChecked && isSelected && !isCorrect

                val bgColor = when {
                    showAsCorrect -> SuccessNeonGreen.copy(alpha = 0.2f)
                    showAsWrong -> Color(0xFFE53935).copy(alpha = 0.2f)
                    isSelected -> BtnElectricPurple.copy(alpha = 0.2f)
                    else -> CardDarkPurple
                }
                val borderColor = when {
                    showAsCorrect -> SuccessNeonGreen
                    showAsWrong -> Color(0xFFE53935)
                    isSelected -> BtnElectricPurple
                    else -> BorderMutedViolet
                }

                Card(
                    onClick = { if (!isChecked) selectedOption = index },
                    colors = CardDefaults.cardColors(containerColor = bgColor),
                    border = BorderStroke(1.dp, borderColor),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp)
                ) {
                    Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(32.dp).background(if(showAsCorrect) SuccessNeonGreen else if(showAsWrong) Color(0xFFE53935) else if(isSelected) BtnElectricPurple else BorderMutedViolet, shape = RoundedCornerShape(50)), contentAlignment = Alignment.Center) {
                            Text(letters[index], color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(text, color = TextWhite, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))
            Button(
                onClick = {
                    if (!isChecked) {
                        isChecked = true
                        if (questions[currentQIndex].options[selectedOption!!] == questions[currentQIndex].correctAnswer) score++
                    } else {
                        if (currentQIndex < questions.size - 1) {
                            currentQIndex++
                            selectedOption = null
                            isChecked = false
                        } else {
                            val percentage = score.toDouble() / questions.size
                            if (percentage >= 0.5) {
                                quizState = QuizState.PASSED
                                onPass()
                            } else {
                                quizState = QuizState.FAILED
                            }
                        }
                    }
                },
                enabled = selectedOption != null,
                modifier = Modifier.fillMaxWidth().height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = BtnElectricPurple, disabledContainerColor = CardDarkPurple),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(if (!isChecked) "Submit Answer" else if (currentQIndex < questions.size - 1) "Next Question" else "See Results", color = if(selectedOption != null) TextWhite else TextDarkGrayPurple, fontWeight = FontWeight.Bold)
            }
        }
    }
}

// --- 5. UNLOCK SCREEN ---
@Composable
fun UnlockScreen(onOpenApp: () -> Unit, onBackToStudy: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF1B2C10), BgMidnight))).padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(modifier = Modifier.size(80.dp).background(SuccessNeonGreen.copy(alpha=0.2f), shape = RoundedCornerShape(24.dp)).border(2.dp, SuccessNeonGreen.copy(alpha=0.5f), RoundedCornerShape(24.dp)), contentAlignment = Alignment.Center) { Icon(Icons.Default.EmojiEvents, contentDescription = "Trophy", tint = SuccessNeonGreen, modifier = Modifier.size(40.dp)) }
        Spacer(modifier = Modifier.height(32.dp))
        Text("ACCESS GRANTED", color = SuccessNeonGreen, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.sp)
        Spacer(modifier = Modifier.height(12.dp))
        Text("You earned\n5 minutes.", color = TextWhite, style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center)
        Spacer(modifier = Modifier.height(16.dp))
        Text("Quiz passed. Timer starts when you open the app.", color = TextMutedLilac, fontSize = 14.sp)

        Spacer(modifier = Modifier.height(48.dp))
        Card(colors = CardDefaults.cardColors(containerColor = CardDarkPurple), border = BorderStroke(1.dp, BorderMutedViolet), shape = RoundedCornerShape(24.dp)) {
            Column(modifier = Modifier.padding(horizontal = 48.dp, vertical = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text("5:00", color = SuccessNeonGreen, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                Text("free time remaining", color = TextDarkGrayPurple, fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(48.dp))
        Button(onClick = onOpenApp, colors = ButtonDefaults.buttonColors(containerColor = SuccessNeonGreen), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) { Text("Open App", color = Color(0xFF0A1F05), fontWeight = FontWeight.Bold, fontSize = 16.sp) }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onBackToStudy, colors = ButtonDefaults.buttonColors(containerColor = CardDarkPurple), modifier = Modifier.fillMaxWidth().height(56.dp), shape = RoundedCornerShape(16.dp)) { Text("Back to Study Session", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    }
}

// --- 6. SETUP FLOW SCREENS ---
@Composable
fun CreateSessionStep1(onNext: (Int) -> Unit, onBack: () -> Unit) {
    var selected by remember { mutableStateOf("60m") }
    var customHours by remember { mutableIntStateOf(1) }
    var customMins by remember { mutableIntStateOf(0) }

    Column(modifier = Modifier.fillMaxSize().background(BgMidnight).padding(24.dp).padding(top = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack, modifier = Modifier.background(CardDarkPurple, RoundedCornerShape(50)).size(36.dp)) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextWhite, modifier = Modifier.size(18.dp))
            }
            Spacer(modifier = Modifier.width(12.dp))
            Text("Create Session", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.weight(1f))
            Text("1/4", color = TextMutedLilac, fontSize = 14.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.height(4.dp).weight(1f).background(BtnElectricPurple))
            Box(modifier = Modifier.height(4.dp).weight(1f).background(BorderMutedViolet))
            Box(modifier = Modifier.height(4.dp).weight(1f).background(BorderMutedViolet))
            Box(modifier = Modifier.height(4.dp).weight(1f).background(BorderMutedViolet))
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text("Study Duration", color = TextWhite, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("How long do you want to study?", color = TextDarkGrayPurple)

        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OptionCard("30 min", "", selected == "30m", { selected = "30m" }, Modifier.weight(1f))
            OptionCard("1 hour", "", selected == "60m", { selected = "60m" }, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OptionCard("2 hours", "", selected == "120m", { selected = "120m" }, Modifier.weight(1f))
            OptionCard("Up to me", "No time limit", selected == "none", { selected = "none" }, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth()) {
            OptionCard("Custom", "Set precise time", selected == "custom", { selected = "custom" }, Modifier.fillMaxWidth(0.48f))
        }

        if (selected == "custom") {
            Spacer(modifier = Modifier.height(32.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { customHours++ }) { Text("+", color = BtnElectricPurple, fontSize = 24.sp) }
                    Text(customHours.toString().padStart(2, '0'), color = TextWhite, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                    Text("HOURS", color = TextDarkGrayPurple, fontSize = 12.sp)
                    IconButton(onClick = { if (customHours > 0) customHours-- }) { Text("-", color = BtnElectricPurple, fontSize = 32.sp) }
                }
                Text(" : ", color = TextWhite, fontSize = 48.sp, modifier = Modifier.padding(horizontal = 24.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { customMins = (customMins + 5) % 60 }) { Text("+", color = BtnElectricPurple, fontSize = 24.sp) }
                    Text(customMins.toString().padStart(2, '0'), color = TextWhite, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                    Text("MINS", color = TextDarkGrayPurple, fontSize = 12.sp)
                    IconButton(onClick = { if (customMins >= 5) customMins -= 5 else customMins = 55 }) { Text("-", color = BtnElectricPurple, fontSize = 32.sp) }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = {
                val resolvedMinutes = when (selected) {
                    "30m" -> 30; "60m" -> 60; "120m" -> 120; else -> (customHours * 60) + customMins
                }
                onNext(resolvedMinutes)
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BtnElectricPurple),
            shape = RoundedCornerShape(16.dp)
        ) { Text("Continue >", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    }
}

@Composable
fun CreateSessionStep2(studyTimeMins: Int, onNext: (Int) -> Unit, onBack: () -> Unit) {
    var selected by remember { mutableStateOf("30m") }
    var customHours by remember { mutableIntStateOf(0) }
    var customMins by remember { mutableIntStateOf(15) }

    Column(modifier = Modifier.fillMaxSize().background(BgMidnight).padding(24.dp).padding(top = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack, modifier = Modifier.background(CardDarkPurple, RoundedCornerShape(50)).size(36.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextWhite, modifier = Modifier.size(18.dp)) }
            Spacer(modifier = Modifier.width(12.dp))
            Text("Create Session", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.weight(1f))
            Text("2/4", color = TextMutedLilac, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.height(4.dp).weight(1f).background(BtnElectricPurple)); Box(modifier = Modifier.height(4.dp).weight(1f).background(BtnElectricPurple)); Box(modifier = Modifier.height(4.dp).weight(1f).background(BorderMutedViolet)); Box(modifier = Modifier.height(4.dp).weight(1f).background(BorderMutedViolet))
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text("Break Duration", color = TextWhite, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("How long should your break be?", color = TextDarkGrayPurple)

        Spacer(modifier = Modifier.height(24.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            OptionCard("30 min", "", selected == "30m", { selected = "30m" }, Modifier.weight(1f))
            OptionCard("1 hour", "", selected == "60m", { selected = "60m" }, Modifier.weight(1f))
        }
        Spacer(modifier = Modifier.height(12.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            val aiCalc = (studyTimeMins / 4).coerceAtLeast(5)
            OptionCard("AI Decide", "~${aiCalc}m calculated", selected == "ai", { selected = "ai" }, Modifier.weight(1f))
            OptionCard("Custom", "Set precise time", selected == "custom", { selected = "custom" }, Modifier.weight(1f))
        }

        if (selected == "custom") {
            Spacer(modifier = Modifier.height(32.dp))
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { customHours++ }) { Text("+", color = BtnElectricPurple, fontSize = 24.sp) }
                    Text(customHours.toString().padStart(2, '0'), color = TextWhite, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                    Text("HOURS", color = TextDarkGrayPurple, fontSize = 12.sp)
                    IconButton(onClick = { if (customHours > 0) customHours-- }) { Text("-", color = BtnElectricPurple, fontSize = 32.sp) }
                }
                Text(" : ", color = TextWhite, fontSize = 48.sp, modifier = Modifier.padding(horizontal = 24.dp))
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    IconButton(onClick = { customMins = (customMins + 5) % 60 }) { Text("+", color = BtnElectricPurple, fontSize = 24.sp) }
                    Text(customMins.toString().padStart(2, '0'), color = TextWhite, fontSize = 48.sp, fontWeight = FontWeight.Bold)
                    Text("MINS", color = TextDarkGrayPurple, fontSize = 12.sp)
                    IconButton(onClick = { if (customMins >= 5) customMins -= 5 else customMins = 55 }) { Text("-", color = BtnElectricPurple, fontSize = 32.sp) }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = {
                val resolvedMinutes = when (selected) {
                    "30m" -> 30; "60m" -> 60; "ai" -> (studyTimeMins / 4).coerceAtLeast(5); else -> (customHours * 60) + customMins
                }
                onNext(resolvedMinutes)
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BtnElectricPurple),
            shape = RoundedCornerShape(16.dp)
        ) { Text("Continue >", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    }
}

@Composable
fun CreateSessionStep3(currentFileName: String, onFileSelected: (String, Uri) -> Unit, onNext: () -> Unit, onBack: () -> Unit) {
    val context = LocalContext.current
    var showError by remember { mutableStateOf(false) }

    val launcher = rememberLauncherForActivityResult(contract = ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (cursor.moveToFirst() && nameIndex != -1) {
                    onFileSelected(cursor.getString(nameIndex), uri)
                    showError = false
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().background(BgMidnight).padding(24.dp).padding(top = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack, modifier = Modifier.background(CardDarkPurple, RoundedCornerShape(50)).size(36.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextWhite, modifier = Modifier.size(18.dp)) }
            Spacer(modifier = Modifier.width(12.dp))
            Text("Create Session", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.weight(1f))
            Text("3/4", color = TextMutedLilac, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.height(4.dp).weight(1f).background(BtnElectricPurple)); Box(modifier = Modifier.height(4.dp).weight(1f).background(BtnElectricPurple)); Box(modifier = Modifier.height(4.dp).weight(1f).background(BtnElectricPurple)); Box(modifier = Modifier.height(4.dp).weight(1f).background(BorderMutedViolet))
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text("Study Material", color = TextWhite, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("What are you studying today?", color = TextDarkGrayPurple)

        Spacer(modifier = Modifier.height(24.dp))

        val outlineColor = if (showError) Color(0xFFE53935) else BorderMutedViolet
        OutlinedButton(
            onClick = { launcher.launch("application/pdf") },
            modifier = Modifier.fillMaxWidth().height(72.dp),
            border = BorderStroke(2.dp, outlineColor),
            shape = RoundedCornerShape(16.dp),
            colors = ButtonDefaults.outlinedButtonColors(containerColor = if (currentFileName.isNotEmpty()) BtnElectricPurple.copy(alpha = 0.1f) else Color.Transparent)
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(imageVector = Icons.Default.AddCircle, contentDescription = "Add", tint = if (showError) Color(0xFFE53935) else TextWhite, modifier = Modifier.size(32.dp))
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    text = currentFileName.ifEmpty { "Upload your notes, PDFs, or assignments" },
                    color = if (showError) Color(0xFFE53935) else TextMutedLilac,
                    fontSize = 14.sp
                )
            }
        }

        if (showError) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("⚠️ You must upload study material to continue.", color = Color(0xFFE53935), fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.weight(1f))
        Button(
            onClick = {
                if (currentFileName.isNotEmpty()) { onNext() } else { showError = true }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BtnElectricPurple),
            shape = RoundedCornerShape(16.dp)
        ) { Text("Continue >", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    }
}

@Composable
fun CreateSessionStep4(globalBlockedApps: MutableList<String>, onNext: () -> Unit, onBack: () -> Unit) {
    val apps = listOf("Google Chrome", "TikTok", "Instagram", "YouTube", "X (Twitter)", "Reddit", "Facebook", "Netflix")
    var showError by remember { mutableStateOf(false) }

    Column(modifier = Modifier.fillMaxSize().background(BgMidnight).padding(24.dp).padding(top = 32.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBack, modifier = Modifier.background(CardDarkPurple, RoundedCornerShape(50)).size(36.dp)) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextWhite, modifier = Modifier.size(18.dp)) }
            Spacer(modifier = Modifier.width(12.dp))
            Text("Create Session", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
            Spacer(modifier = Modifier.weight(1f))
            Text("4/4", color = TextMutedLilac, fontSize = 14.sp)
        }
        Spacer(modifier = Modifier.height(16.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(modifier = Modifier.height(4.dp).weight(1f).background(BtnElectricPurple)); Box(modifier = Modifier.height(4.dp).weight(1f).background(BtnElectricPurple)); Box(modifier = Modifier.height(4.dp).weight(1f).background(BtnElectricPurple)); Box(modifier = Modifier.height(4.dp).weight(1f).background(BtnElectricPurple))
        }

        Spacer(modifier = Modifier.height(32.dp))
        Text("Block Apps", color = TextWhite, style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(8.dp))
        Text("Choose which apps to intercept.", color = TextDarkGrayPurple)

        Spacer(modifier = Modifier.height(24.dp))
        Column(modifier = Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            apps.forEach { app ->
                Row(modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(app, color = TextWhite, fontWeight = if (app == "Google Chrome") FontWeight.Bold else FontWeight.Normal)
                    Checkbox(
                        checked = globalBlockedApps.contains(app),
                        onCheckedChange = { isChecked ->
                            if (isChecked) { if (!globalBlockedApps.contains(app)) globalBlockedApps.add(app) } else { globalBlockedApps.remove(app) }
                            showError = false
                        },
                        colors = CheckboxDefaults.colors(checkedColor = BtnElectricPurple)
                    )
                }
            }
        }

        if (showError) {
            Spacer(modifier = Modifier.height(8.dp))
            Text("⚠️ Please select at least one app to block.", color = Color(0xFFE53935), fontSize = 12.sp)
        }

        Spacer(modifier = Modifier.height(16.dp))
        Button(
            onClick = {
                if (globalBlockedApps.isNotEmpty()) { onNext() } else { showError = true }
            },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = BtnElectricPurple),
            shape = RoundedCornerShape(16.dp)
        ) { Text("Review Setup >", color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp) }
    }
}

@Composable
fun SessionOverviewScreen(studyTimeMins: Int, breakTimeMins: Int, blockedCount: Int, fileName: String, onStartSession: () -> Unit, onEditSetup: () -> Unit) {
    Box(modifier = Modifier.fillMaxSize().background(BgMidnight)) {
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.4f)))

        Card(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(8.dp),
            colors = CardDefaults.cardColors(containerColor = CardDarkPurple),
            border = BorderStroke(1.dp, BorderMutedViolet),
            shape = RoundedCornerShape(24.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(24.dp)) {
                Text("SESSION OVERVIEW", color = BtnElectricPurple, fontWeight = FontWeight.Bold, fontSize = 12.sp, letterSpacing = 1.sp)
                Spacer(modifier = Modifier.height(24.dp))

                val studyHours = studyTimeMins / 60
                val studyMins = studyTimeMins % 60
                val studyLabel = if (studyHours > 0) "${studyHours}h ${studyMins}m" else "$studyMins mins"

                OverviewRow(label = "Study Time", value = studyLabel)
                OverviewRow(label = "Break Time", value = "$breakTimeMins mins")
                OverviewRow(label = "Target", value = fileName.take(15) + "...")
                OverviewRow(label = "Blocked Apps", value = "$blockedCount apps targeted")

                Spacer(modifier = Modifier.height(32.dp))
                Button(onClick = onStartSession, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = BtnElectricPurple), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) { Text("Start Session", color = TextWhite, fontWeight = FontWeight.Bold) }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = onEditSetup, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.outlinedButtonColors(containerColor = Color.Transparent), border = BorderStroke(1.dp, BorderMutedViolet), shape = RoundedCornerShape(12.dp), contentPadding = PaddingValues(vertical = 16.dp)) { Text("Edit Setup", color = TextMutedLilac, fontWeight = FontWeight.Medium) }
            }
        }
    }
}

// --- HELPER COMPONENTS ---
@Composable
fun OverviewRow(label: String, value: String) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 10.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = TextDarkGrayPurple, fontSize = 14.sp)
        Text(value, color = TextWhite, fontSize = 14.sp, fontWeight = FontWeight.Bold)
    }
}

@Composable
fun StudyMaterialCard(title: String, subtitle: String, isActive: Boolean, onClick: () -> Unit = {}) {
    val bgColor = if (isActive) BtnElectricPurple.copy(alpha = 0.1f) else CardDarkPurple
    val borderColor = if (isActive) BtnElectricPurple else BorderMutedViolet
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = bgColor), border = BorderStroke(1.dp, borderColor), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(40.dp).background(Color.White.copy(alpha = 0.1f), shape = RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) { Icon(imageVector = Icons.Outlined.Description, contentDescription = "File", tint = if(isActive) BtnElectricPurple else TextDarkGrayPurple) }
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, color = TextWhite, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(subtitle, color = if (isActive) BtnElectricPurple else TextDarkGrayPurple, fontSize = 12.sp)
            }
            if (isActive) { Box(modifier = Modifier.size(8.dp).background(SuccessNeonGreen, shape = RoundedCornerShape(50))) }
        }
    }
}

@Composable
fun OptionCard(title: String, subtitle: String, isSelected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val bgColor = if (isSelected) BtnElectricPurple.copy(alpha = 0.2f) else CardDarkPurple
    val borderColor = if (isSelected) BtnElectricPurple else BorderMutedViolet
    Card(onClick = onClick, colors = CardDefaults.cardColors(containerColor = bgColor), border = BorderStroke(1.dp, borderColor), shape = RoundedCornerShape(12.dp), modifier = modifier.height(80.dp)) {
        Column(modifier = Modifier.padding(12.dp).fillMaxSize(), verticalArrangement = Arrangement.Center) {
            Text(title, color = TextWhite, fontWeight = FontWeight.Bold)
            if (subtitle.isNotEmpty()) { Text(subtitle, color = TextDarkGrayPurple, fontSize = 12.sp) }
        }
    }
}

@Composable
fun Chip(text: String, onClick: () -> Unit) {
    Box(modifier = Modifier.clickable { onClick() }.border(1.dp, BorderMutedViolet, RoundedCornerShape(50)).background(CardDarkPurple, RoundedCornerShape(50)).padding(horizontal = 16.dp, vertical = 8.dp)) {
        Text(text, color = TextMutedLilac, fontSize = 12.sp)
    }
}

fun Modifier.dashedBorder(color: Color, strokeWidth: Dp, cornerRadius: Dp) = this.drawBehind {
    drawRoundRect(
        color = color,
        style = Stroke(width = strokeWidth.toPx(), pathEffect = PathEffect.dashPathEffect(floatArrayOf(15f, 15f), 0f)),
        cornerRadius = CornerRadius(cornerRadius.toPx())
    )
}
