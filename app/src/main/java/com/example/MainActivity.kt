package com.example

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.automirrored.filled.VolumeMute
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueryStats
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Vibration
import androidx.activity.compose.BackHandler
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.EmeraldSuccessContainer
import com.example.ui.theme.IndigoListening
import com.example.ui.theme.IndigoListeningContainer
import com.example.ui.theme.MyApplicationTheme
import com.example.ui.theme.SaffronLight
import com.example.ui.theme.SaffronPrimary

enum class AppScreenTab {
    REMINDER,
    JAP_COUNTER
}

class MainActivity : ComponentActivity() {

    private val viewModel: VoiceReminderViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                var currentTab by remember { mutableStateOf(AppScreenTab.REMINDER) }

                BackHandler(enabled = currentTab != AppScreenTab.REMINDER) {
                    currentTab = AppScreenTab.REMINDER
                }

                Scaffold(
                    contentWindowInsets = androidx.compose.foundation.layout.WindowInsets.systemBars,
                    bottomBar = {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface,
                            tonalElevation = 6.dp
                        ) {
                            NavigationBarItem(
                                selected = currentTab == AppScreenTab.REMINDER,
                                onClick = { currentTab = AppScreenTab.REMINDER },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.Schedule,
                                        contentDescription = "Reminder"
                                    )
                                },
                                label = { Text("Reminder") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = SaffronPrimary,
                                    selectedTextColor = SaffronPrimary,
                                    indicatorColor = SaffronPrimary.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier.testTag("tab_reminder")
                            )

                            NavigationBarItem(
                                selected = currentTab == AppScreenTab.JAP_COUNTER,
                                onClick = { currentTab = AppScreenTab.JAP_COUNTER },
                                icon = {
                                    Icon(
                                        imageVector = Icons.Default.TouchApp,
                                        contentDescription = "Jap Counter"
                                    )
                                },
                                label = { Text("Jap Counter") },
                                colors = NavigationBarItemDefaults.colors(
                                    selectedIconColor = SaffronPrimary,
                                    selectedTextColor = SaffronPrimary,
                                    indicatorColor = SaffronPrimary.copy(alpha = 0.15f)
                                ),
                                modifier = Modifier.testTag("tab_jap_counter")
                            )
                        }
                    }
                ) { innerPadding ->
                    when (currentTab) {
                        AppScreenTab.REMINDER -> {
                            RadhaReminderScreen(
                                viewModel = viewModel,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            )
                        }
                        AppScreenTab.JAP_COUNTER -> {
                            NaamJapCounterScreen(
                                viewModel = viewModel,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(innerPadding)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun RadhaReminderScreen(
    viewModel: VoiceReminderViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val status by viewModel.serviceStatus.collectAsStateWithLifecycle()
    val reminderAudio by viewModel.reminderAudio.collectAsStateWithLifecycle()
    val confirmationAudio by viewModel.confirmationAudio.collectAsStateWithLifecycle()
    val previewPlayingUri by viewModel.previewPlayingUri.collectAsStateWithLifecycle()
    val diagnostics by viewModel.diagnostics.collectAsStateWithLifecycle()

    val cycleIntervalMinutes by viewModel.cycleIntervalMinutes.collectAsStateWithLifecycle()
    val listeningWindowSeconds by viewModel.listeningWindowSeconds.collectAsStateWithLifecycle()
    val autoStopMinutes by viewModel.autoStopMinutes.collectAsStateWithLifecycle()
    val muteMicBeep by viewModel.muteMicBeep.collectAsStateWithLifecycle()
    val vibrateOnRecognition by viewModel.vibrateOnRecognition.collectAsStateWithLifecycle()
    val extraKeywords by viewModel.extraKeywords.collectAsStateWithLifecycle()
    val todayStats by viewModel.todayStats.collectAsStateWithLifecycle()
    val totalStats by viewModel.totalStats.collectAsStateWithLifecycle()
    val isVoskReady by viewModel.isVoskReady.collectAsStateWithLifecycle()
    val isImportingModel by viewModel.isImportingModel.collectAsStateWithLifecycle()
    val modelDownloadProgress by viewModel.modelDownloadProgress.collectAsStateWithLifecycle()
    val requireModelPrompt by viewModel.requireModelPrompt.collectAsStateWithLifecycle()
    val requireNamePrompt by viewModel.requireNamePrompt.collectAsStateWithLifecycle()

    var showDiagnostics by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showBatteryGuideDialog by remember { mutableStateOf(false) }
    var showResetStatsDialog by remember { mutableStateOf(false) }
    var showRemoveModelDialog by remember { mutableStateOf(false) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
        val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
            if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                viewModel.refreshDiagnostics()
                viewModel.refreshStats()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (requireNamePrompt) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissNamePrompt() },
            title = { Text("Pehle naam add karein") },
            text = {
                Text("Is app me koi default naam nahi hai. Settings me \"Names to listen for\" me apna naam add karein (Devanagari me likhna best hai), fir Start karein.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissNamePrompt()
                        showSettings = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary)
                ) {
                    Text("Naam add karein")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissNamePrompt() }) {
                    Text("Baad me")
                }
            }
        )
    }

    if (requireModelPrompt) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissModelPrompt() },
            icon = {
                Icon(
                    imageVector = Icons.Default.CloudDownload,
                    contentDescription = null,
                    tint = SaffronPrimary,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = { Text("Offline Hindi Model Required") },
            text = {
                Text("To guarantee 100% zero-beep continuous voice listening without Google interruptions, the Vosk Hindi model (~42 MB) must be downloaded before starting.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.dismissModelPrompt()
                        viewModel.startInAppModelDownload { success ->
                            if (success) {
                                Toast.makeText(context, "Hindi model ready! You can now start the cycle.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary)
                ) {
                    Text("Download Model (~42 MB)")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissModelPrompt() }) {
                    Text("Cancel")
                }
            }
        )
    }

    // SAF Audio Pickers
    val reminderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.setReminderAudio(uri)
            Toast.makeText(context, "Reminder audio selected", Toast.LENGTH_SHORT).show()
        }
    }

    val confirmationPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.setConfirmationAudio(uri)
            Toast.makeText(context, "Confirmation audio selected", Toast.LENGTH_SHORT).show()
        }
    }

    // Vosk Offline Model ZIP Picker
    val voskModelZipPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importVoskModelZip(uri) { success ->
                if (success) {
                    Toast.makeText(context, "Offline Vosk model loaded! Google mic bypassed.", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(context, "Failed to unpack model ZIP. Verify valid Vosk model archive.", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // Permission Request Launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { grants ->
        val micGranted = grants[Manifest.permission.RECORD_AUDIO] == true
        if (micGranted) {
            viewModel.refreshDiagnostics()
            if (reminderAudio != null && confirmationAudio != null && !status.isServiceRunning) {
                viewModel.startService(context)
            }
        } else {
            Toast.makeText(
                context,
                "Microphone permission is required to listen for your name",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshDiagnostics()
        viewModel.refreshStats()
    }

    if (showBatteryGuideDialog) {
        BatteryOptimizationGuideDialog(
            onDismiss = { showBatteryGuideDialog = false },
            onOpenSettings = { openBatterySettings(context) }
        )
    }

    if (showResetStatsDialog) {
        AlertDialog(
            onDismissRequest = { showResetStatsDialog = false },
            title = { Text("Reset Recognition Stats?") },
            text = { Text("This will reset both today's count and total all-time recognition count to 0.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetStats()
                        showResetStatsDialog = false
                        Toast.makeText(context, "Stats reset", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetStatsDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showRemoveModelDialog) {
        AlertDialog(
            onDismissRequest = { showRemoveModelDialog = false },
            title = { Text("Remove Offline Vosk Model?") },
            text = { Text("The offline model files will be deleted. The app will smoothly revert to Google Speech Services with mute-beep protection.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeVoskModel()
                        showRemoveModelDialog = false
                        Toast.makeText(context, "Offline model deleted", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Remove Model")
                }
            },
            dismissButton = {
                TextButton(onClick = { showRemoveModelDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Spacer(modifier = Modifier.height(12.dp))
                DevotionalHeader()
            }

            // Stats Card (Today & All-Time Counts)
            item {
                StatsCard(
                    todayCount = todayStats,
                    totalCount = totalStats,
                    onResetClick = { showResetStatsDialog = true }
                )
            }

            // Screen-Off & Background Optimization Advisory Banner
            item {
                BatteryOptimizationBanner(
                    onOpenGuide = { showBatteryGuideDialog = true }
                )
            }

            // Offline Vosk Speech Engine Card (Google-Bypassed Offline Listening)
            item {
                OfflineVoskCard(
                    isVoskReady = isVoskReady,
                    isImportingModel = isImportingModel,
                    downloadProgress = modelDownloadProgress,
                    onDownloadInAppClick = {
                        viewModel.startInAppModelDownload { success ->
                            if (success) {
                                Toast.makeText(context, "Vosk Hindi model downloaded & ready! Zero beep offline mode active.", Toast.LENGTH_LONG).show()
                            } else {
                                Toast.makeText(context, "Download failed. Please check internet connection.", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    onCancelDownloadClick = {
                        viewModel.cancelModelDownload()
                    },
                    onImportLocalZipClick = {
                        voskModelZipPickerLauncher.launch(
                            arrayOf("application/zip", "application/octet-stream", "*/*")
                        )
                    },
                    onRemoveClick = { showRemoveModelDialog = true }
                )
            }

            // Real-Time Dynamic Status Monitor Card
            item {
                CycleStatusCard(
                    status = status,
                    isVoskReady = isVoskReady,
                    onSkipWait = { viewModel.skipWaitAndRemindNow(context) },
                    onClearError = { viewModel.clearError() },
                    modifier = Modifier.testTag("cycle_status_card")
                )
            }

            // Audio Selection Cards
            item {
                Text(
                    text = "Required Audio Files",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
                Text(
                    text = "Pick custom audio files from your device. Both are required before starting the cycle.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            item {
                AudioPickerCard(
                    title = "1. Reminder Audio (~5s)",
                    subtitle = "Plays at the beginning of each cycle before listening starts",
                    audio = reminderAudio,
                    isPlayingPreview = previewPlayingUri == reminderAudio?.uriString,
                    onPickClick = { reminderPickerLauncher.launch(arrayOf("audio/*")) },
                    onPreviewClick = { reminderAudio?.let { viewModel.togglePreview(context, it) } },
                    enabled = !status.isServiceRunning,
                    cardTag = "reminder_audio_card",
                    pickButtonTag = "select_reminder_button"
                )
            }

            item {
                AudioPickerCard(
                    title = "2. Confirmation Audio",
                    subtitle = "Plays immediately after your name is recognized",
                    audio = confirmationAudio,
                    isPlayingPreview = previewPlayingUri == confirmationAudio?.uriString,
                    onPickClick = { confirmationPickerLauncher.launch(arrayOf("audio/*")) },
                    onPreviewClick = { confirmationAudio?.let { viewModel.togglePreview(context, it) } },
                    enabled = !status.isServiceRunning,
                    cardTag = "confirmation_audio_card",
                    pickButtonTag = "select_confirmation_button"
                )
            }

            // Start & Stop Controls
            item {
                ControlsSection(
                    isServiceRunning = status.isServiceRunning,
                    canStart = reminderAudio != null && confirmationAudio != null,
                    onStartClick = {
                        val hasMic = ContextCompat.checkSelfPermission(
                            context,
                            Manifest.permission.RECORD_AUDIO
                        ) == PackageManager.PERMISSION_GRANTED

                        val permissionsToRequest = mutableListOf(Manifest.permission.RECORD_AUDIO)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                        }

                        if (!hasMic) {
                            permissionLauncher.launch(permissionsToRequest.toTypedArray())
                        } else {
                            viewModel.startService(context)
                        }
                    },
                    onStopClick = {
                        viewModel.stopService(context)
                    }
                )
            }

            // Settings Card (Cycle Interval, Listening Window, Auto-Stop, Extra Keywords, Mute Beep, Vibrate)
            item {
                SettingsCard(
                    isExpanded = showSettings,
                    onToggleExpand = { showSettings = !showSettings },
                    isServiceRunning = status.isServiceRunning,
                    cycleIntervalMinutes = cycleIntervalMinutes,
                    onSelectCycleInterval = { viewModel.setCycleInterval(it) },
                    listeningWindowSeconds = listeningWindowSeconds,
                    onSelectListeningWindow = { viewModel.setListeningWindow(it) },
                    autoStopMinutes = autoStopMinutes,
                    onSelectAutoStop = { viewModel.setAutoStop(it) },
                    muteMicBeep = muteMicBeep,
                    onToggleMuteMicBeep = { viewModel.setMuteMicBeep(it) },
                    vibrateOnRecognition = vibrateOnRecognition,
                    onToggleVibrate = { viewModel.setVibrateOnRecognition(it) },
                    extraKeywords = extraKeywords,
                    onAddKeyword = { viewModel.addKeyword(it) },
                    onRemoveKeyword = { viewModel.removeKeyword(it) }
                )
            }

            // Real-Device Diagnostics & Offline Test Section
            item {
                DiagnosticsAccordion(
                    diagnostics = diagnostics,
                    isExpanded = showDiagnostics,
                    onToggleExpand = { showDiagnostics = !showDiagnostics },
                    onRefresh = { viewModel.refreshDiagnostics() },
                    onRequestPermission = {
                        val permissions = mutableListOf(Manifest.permission.RECORD_AUDIO)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
                        }
                        permissionLauncher.launch(permissions.toTypedArray())
                    },
                    onOpenBatteryGuide = { showBatteryGuideDialog = true }
                )
            }

            item {
                Spacer(modifier = Modifier.height(32.dp))
            }
        }
    }
}

@Composable
fun DevotionalHeader() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(54.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(SaffronLight, SaffronPrimary)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Mic,
                contentDescription = "Radha Reminder Icon",
                tint = Color.White,
                modifier = Modifier.size(30.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = "Radha Reminder",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = "Continuous Voice Listening Cycle (Hindi)",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun StatsCard(
    todayCount: Int,
    totalCount: Int,
    onResetClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.QueryStats,
                    contentDescription = null,
                    tint = SaffronPrimary,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column {
                    Text(
                        text = "Recognition Stats",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Today: $todayCount • Total: $totalCount times",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onResetClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = "Reset Stats",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
fun CycleStatusCard(
    status: VoiceReminderStatus,
    isVoskReady: Boolean = false,
    onSkipWait: () -> Unit = {},
    onClearError: () -> Unit,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    val cardColor by animateColorAsState(
        targetValue = when (status.phase) {
            ReminderPhase.IDLE -> MaterialTheme.colorScheme.surfaceVariant
            ReminderPhase.PLAYING_REMINDER -> MaterialTheme.colorScheme.secondaryContainer
            ReminderPhase.LISTENING_SILENT -> IndigoListeningContainer
            ReminderPhase.RADHA_RECOGNIZED -> EmeraldSuccessContainer
            ReminderPhase.PLAYING_CONFIRMATION -> MaterialTheme.colorScheme.secondaryContainer
            ReminderPhase.WAITING_NEXT_INTERVAL -> SaffronPrimary.copy(alpha = 0.12f)
            ReminderPhase.CYCLE_TIMEOUT -> MaterialTheme.colorScheme.surfaceVariant
            ReminderPhase.ERROR -> MaterialTheme.colorScheme.errorContainer
        },
        label = "cardColor"
    )

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (status.phase == ReminderPhase.LISTENING_SILENT) {
                    Modifier.border(
                        2.dp,
                        IndigoListening.copy(alpha = 0.5f),
                        RoundedCornerShape(16.dp)
                    )
                } else if (status.phase == ReminderPhase.RADHA_RECOGNIZED) {
                    Modifier.border(
                        2.dp,
                        EmeraldSuccess,
                        RoundedCornerShape(16.dp)
                    )
                } else if (status.phase == ReminderPhase.ERROR) {
                    Modifier.border(
                        2.dp,
                        MaterialTheme.colorScheme.error,
                        RoundedCornerShape(16.dp)
                    )
                } else {
                    Modifier
                }
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "STATUS MONITOR",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                if (status.isServiceRunning) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(if (status.phase == ReminderPhase.LISTENING_SILENT) IndigoListening else SaffronPrimary)
                        )
                        Text(
                            text = "Cycle #${status.cycleCount}",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Main Status Icon & Text
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(
                        when (status.phase) {
                            ReminderPhase.IDLE -> MaterialTheme.colorScheme.surface
                            ReminderPhase.PLAYING_REMINDER -> SaffronPrimary
                            ReminderPhase.LISTENING_SILENT -> IndigoListening
                            ReminderPhase.RADHA_RECOGNIZED -> EmeraldSuccess
                            ReminderPhase.PLAYING_CONFIRMATION -> SaffronPrimary
                            ReminderPhase.WAITING_NEXT_INTERVAL -> SaffronPrimary
                            ReminderPhase.CYCLE_TIMEOUT -> MaterialTheme.colorScheme.surface
                            ReminderPhase.ERROR -> MaterialTheme.colorScheme.error
                        }
                    )
                    .then(
                        if (status.phase == ReminderPhase.LISTENING_SILENT) {
                            Modifier.scale(pulseScale)
                        } else {
                            Modifier
                        }
                    ),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (status.phase) {
                        ReminderPhase.IDLE -> Icons.Default.PlayArrow
                        ReminderPhase.PLAYING_REMINDER -> Icons.AutoMirrored.Filled.VolumeUp
                        ReminderPhase.LISTENING_SILENT -> Icons.Default.Mic
                        ReminderPhase.RADHA_RECOGNIZED -> Icons.Default.CheckCircle
                        ReminderPhase.PLAYING_CONFIRMATION -> Icons.Default.Audiotrack
                        ReminderPhase.WAITING_NEXT_INTERVAL -> Icons.Default.HourglassTop
                        ReminderPhase.CYCLE_TIMEOUT -> Icons.Default.Refresh
                        ReminderPhase.ERROR -> Icons.Default.Error
                    },
                    contentDescription = status.phaseDisplayName,
                    tint = Color.White,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = status.phaseDisplayName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = when (status.phase) {
                    ReminderPhase.LISTENING_SILENT -> IndigoListening
                    ReminderPhase.RADHA_RECOGNIZED -> EmeraldSuccess
                    ReminderPhase.ERROR -> MaterialTheme.colorScheme.error
                    else -> MaterialTheme.colorScheme.onSurface
                },
                textAlign = TextAlign.Center
            )

            // Dynamic phase detail information
            when (status.phase) {
                ReminderPhase.IDLE -> {
                    Text(
                        text = "Select both audio files and press Start Cycle.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                ReminderPhase.PLAYING_REMINDER -> {
                    Text(
                        text = "Playing: ${status.currentAudioTitle}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = "Microphone is OFF until audio finishes.",
                        style = MaterialTheme.typography.labelSmall,
                        color = SaffronPrimary,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                ReminderPhase.LISTENING_SILENT -> {
                    Spacer(modifier = Modifier.height(12.dp))
                    val totalSec = status.totalListeningWindowSeconds.coerceAtLeast(1)
                    val progress = ((totalSec - status.secondsRemaining).toFloat() / totalSec).coerceIn(0f, 1f)
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = IndigoListening,
                        trackColor = IndigoListening.copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (isVoskReady) "Offline Vosk Active • ZERO Mic Beep • Single 15s Session" else "Silent Listening • Google Mic Beep Muted",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = IndigoListening,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = if (isVoskReady) "Offline: listening only for the names you added" else "Listening for the names you added",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                ReminderPhase.RADHA_RECOGNIZED -> {
                    Text(
                        text = "Match detected: \"${status.detectedText}\"",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = EmeraldSuccess,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = "Session recognized: ${status.recognizedCount} times",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                ReminderPhase.PLAYING_CONFIRMATION -> {
                    Text(
                        text = "Playing: ${status.currentAudioTitle}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                    Text(
                        text = "Reminder starts immediately after confirmation finishes",
                        style = MaterialTheme.typography.labelSmall,
                        color = SaffronPrimary
                    )
                }
                ReminderPhase.WAITING_NEXT_INTERVAL -> {
                    val secLeft = status.nextCycleSecondsRemaining
                    val totalSec = status.totalIntervalSeconds.coerceAtLeast(1)
                    val progress = ((totalSec - secLeft).toFloat() / totalSec).coerceIn(0f, 1f)
                    val m = secLeft / 60
                    val s = secLeft % 60

                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier
                            .fillMaxWidth(0.85f)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = SaffronPrimary,
                        trackColor = SaffronPrimary.copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Next reminder in ${m}m ${String.format("%02d", s)}s • Mic is sleeping",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = SaffronPrimary,
                        textAlign = TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(10.dp))
                    Button(
                        onClick = onSkipWait,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary)
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Remind Now (Skip Wait)", fontSize = 12.sp)
                    }
                }
                ReminderPhase.CYCLE_TIMEOUT -> {
                    Text(
                        text = "Listening window ended. Restarting reminder...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                ReminderPhase.ERROR -> {
                    Text(
                        text = status.errorMessage.ifEmpty { "Unexpected service error occurred." },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.error,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onClearError,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                    ) {
                        Text("Dismiss Error")
                    }
                }
            }

            // Auto-stop countdown display
            if (status.autoStopSecondsRemaining > 0) {
                Spacer(modifier = Modifier.height(10.dp))
                val min = status.autoStopSecondsRemaining / 60
                val sec = status.autoStopSecondsRemaining % 60
                Text(
                    text = String.format("Auto-stop in %d:%02d", min, sec),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun AudioPickerCard(
    title: String,
    subtitle: String,
    audio: SelectedAudio?,
    isPlayingPreview: Boolean,
    onPickClick: () -> Unit,
    onPreviewClick: () -> Unit,
    enabled: Boolean,
    cardTag: String,
    pickButtonTag: String
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(cardTag),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            if (audio == null) {
                OutlinedButton(
                    onClick = onPickClick,
                    enabled = enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(pickButtonTag),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Audiotrack,
                        contentDescription = "Pick audio",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Choose Audio File")
                }
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Audiotrack,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(12.dp))

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = audio.displayName,
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (audio.durationMs > 0) {
                            Text(
                                text = "Duration: ${audio.formattedDuration}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Test Preview Button
                    IconButton(
                        onClick = onPreviewClick,
                        modifier = Modifier.testTag("${pickButtonTag}_preview")
                    ) {
                        Icon(
                            imageVector = if (isPlayingPreview) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = if (isPlayingPreview) "Stop preview" else "Play preview",
                            tint = if (isPlayingPreview) SaffronPrimary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    // Change Audio Button
                    IconButton(
                        onClick = onPickClick,
                        enabled = enabled,
                        modifier = Modifier.testTag("${pickButtonTag}_change")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Change audio file",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ControlsSection(
    isServiceRunning: Boolean,
    canStart: Boolean,
    onStartClick: () -> Unit,
    onStopClick: () -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!isServiceRunning) {
            Button(
                onClick = onStartClick,
                enabled = canStart,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("start_cycle_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SaffronPrimary,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (canStart) "Start Automatic Cycle" else "Select Both Audio Files to Start",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        } else {
            Button(
                onClick = onStopClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .testTag("stop_cycle_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Stop,
                    contentDescription = null,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Stop Cycle & Release Mic",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsCard(
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    isServiceRunning: Boolean,
    cycleIntervalMinutes: Int,
    onSelectCycleInterval: (Int) -> Unit,
    listeningWindowSeconds: Int,
    onSelectListeningWindow: (Int) -> Unit,
    autoStopMinutes: Int,
    onSelectAutoStop: (Int) -> Unit,
    muteMicBeep: Boolean,
    onToggleMuteMicBeep: (Boolean) -> Unit,
    vibrateOnRecognition: Boolean,
    onToggleVibrate: (Boolean) -> Unit,
    extraKeywords: Set<String>,
    onAddKeyword: (String) -> Unit,
    onRemoveKeyword: (String) -> Unit
) {
    var newKeywordText by remember { mutableStateOf("") }

    val intervalOptions = listOf(
        0 to "Immediate (0s)",
        1 to "1 min",
        2 to "2 min",
        3 to "3 min",
        4 to "4 min (Default)",
        5 to "5 min",
        10 to "10 min",
        15 to "15 min",
        30 to "30 min"
    )

    val windowOptions = listOf(10, 15, 20, 30, 45, 60)
    val autoStopOptions = listOf(
        0 to "Never",
        15 to "15m",
        30 to "30m",
        60 to "1h",
        120 to "2h",
        240 to "4h"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("settings_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = SaffronPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Settings & Customization",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand settings"
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // 1. Cycle Interval / Next Time Delay
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Schedule,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Next Time / Cycle Interval",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Har kitne minutes ke interval par naam bolna hai (Default: 4 min). Waiting ke dauran mic sleep karta hai jisse battery bachti hai.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            intervalOptions.forEach { (mins, label) ->
                                FilterChip(
                                    selected = cycleIntervalMinutes == mins,
                                    onClick = { if (!isServiceRunning) onSelectCycleInterval(mins) },
                                    label = { Text(label) },
                                    enabled = !isServiceRunning,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SaffronPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // 2. Listening Window Duration
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Listening Window Duration",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "How long the microphone listens silently in each cycle (Default: 15s).",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            windowOptions.forEach { sec ->
                                FilterChip(
                                    selected = listeningWindowSeconds == sec,
                                    onClick = { if (!isServiceRunning) onSelectListeningWindow(sec) },
                                    label = { Text("${sec}s") },
                                    enabled = !isServiceRunning,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SaffronPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // 2. Auto-Stop Timer
                    Column {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Timer,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Auto-Stop Timer",
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Text(
                            text = "Automatically stops the reminder service after selected time.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            autoStopOptions.forEach { (mins, label) ->
                                FilterChip(
                                    selected = autoStopMinutes == mins,
                                    onClick = { if (!isServiceRunning) onSelectAutoStop(mins) },
                                    label = { Text(label) },
                                    enabled = !isServiceRunning,
                                    colors = FilterChipDefaults.filterChipColors(
                                        selectedContainerColor = SaffronPrimary,
                                        selectedLabelColor = Color.White
                                    )
                                )
                            }
                        }
                    }

                    // 3. Extra Custom Keywords
                    Column {
                        Text(
                            text = "Names to listen for",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Koi default naam nahi hai. Apna naam yahan add karein (best: Devanagari). Sirf pura naam bolne par hi count hoga; aadha ya galat naam count nahi hoga.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = newKeywordText,
                                onValueChange = { newKeywordText = it },
                                placeholder = { Text("Apna naam likhein") },
                                singleLine = true,
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(8.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Button(
                                onClick = {
                                    if (newKeywordText.isNotBlank()) {
                                        onAddKeyword(newKeywordText.trim())
                                        newKeywordText = ""
                                    }
                                },
                                shape = RoundedCornerShape(8.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Add,
                                    contentDescription = "Add keyword"
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add")
                            }
                        }

                        if (extraKeywords.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(8.dp))
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                extraKeywords.forEach { word ->
                                    Card(
                                        shape = RoundedCornerShape(16.dp),
                                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(text = word, style = MaterialTheme.typography.bodySmall)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Remove $word",
                                                modifier = Modifier
                                                    .size(16.dp)
                                                    .clickable { onRemoveKeyword(word) },
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // 4. Mute Mic Beep Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.VolumeMute,
                                contentDescription = null,
                                tint = SaffronPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Mute Google Mic Beep",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Silences Google's start-sound so mic opens in 100% silence.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = muteMicBeep,
                            onCheckedChange = onToggleMuteMicBeep
                        )
                    }

                    // 5. Vibrate on Recognition Switch
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Vibration,
                                contentDescription = null,
                                tint = SaffronPrimary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "Vibrate on Recognition",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Text(
                                    text = "Short haptic buzz when your name is detected.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Switch(
                            checked = vibrateOnRecognition,
                            onCheckedChange = onToggleVibrate
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticsAccordion(
    diagnostics: List<DiagnosticItem>,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    onRefresh: () -> Unit,
    onRequestPermission: () -> Unit,
    onOpenBatteryGuide: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("diagnostics_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Real-Device Diagnostics & Tests",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold
                    )
                }

                IconButton(onClick = onToggleExpand) {
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Expand diagnostics"
                    )
                }
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = fadeIn(),
                exit = fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Verifies background execution, offline speech recognition support, and permission states across real Android devices.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    diagnostics.forEach { item ->
                        DiagnosticItemRow(
                            item = item,
                            onRequestPermission = onRequestPermission,
                            onOpenBatteryGuide = onOpenBatteryGuide
                        )
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    OutlinedButton(
                        onClick = onRefresh,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("refresh_diagnostics_button"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Re-check Diagnostics")
                    }
                }
            }
        }
    }
}

@Composable
fun DiagnosticItemRow(
    item: DiagnosticItem,
    onRequestPermission: () -> Unit,
    onOpenBatteryGuide: () -> Unit
) {
    val statusColor = when (item.status) {
        DiagnosticStatus.PASSED -> EmeraldSuccess
        DiagnosticStatus.WARNING -> MaterialTheme.colorScheme.secondary
        DiagnosticStatus.FAILED -> MaterialTheme.colorScheme.error
        DiagnosticStatus.PENDING -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                statusColor.copy(alpha = 0.08f),
                RoundedCornerShape(8.dp)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = when (item.status) {
                DiagnosticStatus.PASSED -> Icons.Default.CheckCircle
                DiagnosticStatus.WARNING -> Icons.Default.Warning
                DiagnosticStatus.FAILED -> Icons.Default.MicOff
                DiagnosticStatus.PENDING -> Icons.Default.Refresh
            },
            contentDescription = null,
            tint = statusColor,
            modifier = Modifier.size(20.dp)
        )

        Spacer(modifier = Modifier.width(12.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = item.title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = item.detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        if (item.id == "permissions" && item.status != DiagnosticStatus.PASSED) {
            Spacer(modifier = Modifier.width(8.dp))
            Button(
                onClick = onRequestPermission,
                shape = RoundedCornerShape(6.dp),
                contentPadding = ButtonDefaults.ContentPadding
            ) {
                Text(text = "Grant", fontSize = 12.sp)
            }
        } else if (item.id == "screen_off") {
            Spacer(modifier = Modifier.width(8.dp))
            OutlinedButton(
                onClick = onOpenBatteryGuide,
                shape = RoundedCornerShape(6.dp),
                contentPadding = ButtonDefaults.ContentPadding
            ) {
                Text(text = "Guide", fontSize = 12.sp)
            }
        }
    }
}

@Composable
fun BatteryOptimizationBanner(
    onOpenGuide: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("battery_optimization_banner"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
                    .background(SaffronPrimary.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.BatteryChargingFull,
                    contentDescription = null,
                    tint = SaffronPrimary,
                    modifier = Modifier.size(22.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Screen-Off Background Setup",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Ensure uninterrupted voice listening when screen is locked",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.width(8.dp))

            Button(
                onClick = onOpenGuide,
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = SaffronPrimary,
                    contentColor = Color.White
                ),
                contentPadding = ButtonDefaults.ContentPadding
            ) {
                Text(text = "Guide", fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
fun BatteryOptimizationGuideDialog(
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit
) {
    val scrollState = rememberScrollState()

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.BatteryChargingFull,
                contentDescription = null,
                tint = SaffronPrimary,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "Uninterrupted Background Guide",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(scrollState),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Android power managers can restrict microphone listening when your screen is locked. To ensure Radha Reminder runs continuously in the background, set battery usage to Unrestricted:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                BrandInstructionCard(
                    brandTitle = "Standard Android / Pixel / Motorola",
                    steps = listOf(
                        "1. Tap 'Open Battery Settings' below",
                        "2. Select 'Radha Reminder'",
                        "3. Set App battery usage to 'Unrestricted'"
                    )
                )

                BrandInstructionCard(
                    brandTitle = "Samsung (One UI)",
                    steps = listOf(
                        "1. Open Settings ➔ Apps ➔ Radha Reminder",
                        "2. Tap 'Battery' ➔ Choose 'Unrestricted'",
                        "3. In Device Care ➔ Battery ➔ Never Sleeping Apps, add Radha Reminder"
                    )
                )

                BrandInstructionCard(
                    brandTitle = "Xiaomi / Redmi / POCO (MIUI / HyperOS)",
                    steps = listOf(
                        "1. Open Settings ➔ Apps ➔ Manage Apps ➔ Radha Reminder",
                        "2. Turn ON 'Autostart'",
                        "3. In Battery Saver, select 'No restrictions'"
                    )
                )

                BrandInstructionCard(
                    brandTitle = "Vivo / Oppo / Realme / OnePlus",
                    steps = listOf(
                        "1. Open Settings ➔ Battery ➔ App battery management",
                        "2. Find Radha Reminder",
                        "3. Enable 'Allow background activity' and 'Auto-launch'"
                    )
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onOpenSettings,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SaffronPrimary,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.OpenInNew,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text("Open Battery Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Got It")
            }
        }
    )
}

@Composable
fun BrandInstructionCard(
    brandTitle: String,
    steps: List<String>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(10.dp)
        ) {
            Text(
                text = brandTitle,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold,
                color = SaffronPrimary
            )
            Spacer(modifier = Modifier.height(4.dp))
            steps.forEach { step ->
                Text(
                    text = step,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
    }
}

fun openBatterySettings(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        } else {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
    } catch (e: Exception) {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            context.startActivity(intent)
        } catch (e2: Exception) {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.parse("package:${context.packageName}")
            }
            context.startActivity(intent)
        }
    }
}

@Composable
fun OfflineVoskCard(
    isVoskReady: Boolean,
    isImportingModel: Boolean,
    downloadProgress: ModelDownloadProgress,
    onDownloadInAppClick: () -> Unit,
    onCancelDownloadClick: () -> Unit,
    onImportLocalZipClick: () -> Unit,
    onRemoveClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("offline_vosk_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(
                            if (isVoskReady) EmeraldSuccessContainer else SaffronPrimary.copy(alpha = 0.15f)
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isVoskReady) Icons.Default.CheckCircle else Icons.Default.CloudDownload,
                        contentDescription = null,
                        tint = if (isVoskReady) EmeraldSuccess else SaffronPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }

                Spacer(modifier = Modifier.width(12.dp))

                Column {
                    Text(
                        text = "Offline Recognition Engine (Vosk)",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = if (isVoskReady) "Active • Google Mic Bypassed (Zero Beep)" else "Optional • In-App Download Available",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isVoskReady) EmeraldSuccess else MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            if (isVoskReady) {
                Text(
                    text = "• Google recognizer is completely bypassed; ZERO mic beep sound.\n" +
                           "• Single continuous 15s session without repeated restarts.\n" +
                           "• Offline keyword spotter detects only the names you add.\n" +
                           "• Preloads in background while reminder audio plays (~5s) for instant listening.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )

                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = onDownloadInAppClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Re-download", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onImportLocalZipClick,
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FolderOpen,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = "Local ZIP", fontSize = 12.sp)
                    }

                    OutlinedButton(
                        onClick = onRemoveClick,
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Delete,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(text = "Remove", fontSize = 12.sp)
                    }
                }
            } else if (downloadProgress.isDownloading) {
                // In-App Downloading State
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant,
                            RoundedCornerShape(8.dp)
                        )
                        .padding(14.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = downloadProgress.statusText.ifEmpty { "Downloading Hindi Model..." },
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.weight(1f)
                        )
                        TextButton(
                            onClick = onCancelDownloadClick
                        ) {
                            Text("Cancel", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    LinearProgressIndicator(
                        progress = { downloadProgress.progress },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = SaffronPrimary,
                        trackColor = SaffronPrimary.copy(alpha = 0.2f)
                    )

                    Spacer(modifier = Modifier.height(6.dp))

                    Text(
                        text = "Direct in-app download from Alphacephei • Automatically extracts into app",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else if (isImportingModel) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.Center
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Unpacking model ZIP...",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            } else {
                Text(
                    text = "Bina kisi browser ke ek hi tap mein app ke andar hi Vosk Hindi model download karein. Download hote hi model automatically import aur activate ho jayega.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    lineHeight = 18.sp
                )

                if (downloadProgress.error != null) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = downloadProgress.error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Primary 1-Tap In-App Download Button
                Button(
                    onClick = onDownloadInAppClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .testTag("download_model_button"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SaffronPrimary,
                        contentColor = Color.White
                    )
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDownload,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Download Hindi Model (~42 MB)",
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "No browser needed • 1-tap download & automatic import",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Secondary Local ZIP Import Button
                OutlinedButton(
                    onClick = onImportLocalZipClick,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Or Select Model ZIP from Storage", fontSize = 12.sp)
                }
            }
        }
    }
}
