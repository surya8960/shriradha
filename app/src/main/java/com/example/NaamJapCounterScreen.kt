package com.example

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.ui.theme.EmeraldSuccess
import com.example.ui.theme.EmeraldSuccessContainer
import com.example.ui.theme.IndigoListening
import com.example.ui.theme.IndigoListeningContainer
import com.example.ui.theme.SaffronDark
import com.example.ui.theme.SaffronLight
import com.example.ui.theme.SaffronPrimary
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun NaamJapCounterScreen(
    viewModel: VoiceReminderViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val japState by viewModel.japState.collectAsStateWithLifecycle()
    val isVoskReady by viewModel.isVoskReady.collectAsStateWithLifecycle()

    var showResetMalaDialog by remember { mutableStateOf(false) }
    var showResetAllDialog by remember { mutableStateOf(false) }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.startVoiceJap(context)
        }
    }

    val scaleAnim = remember { Animatable(1f) }
    LaunchedEffect(japState.currentBead, japState.completedMalas) {
        scaleAnim.animateTo(1.12f, animationSpec = tween(120, easing = FastOutSlowInEasing))
        scaleAnim.animateTo(1f, animationSpec = tween(160, easing = FastOutSlowInEasing))
    }

    if (showResetMalaDialog) {
        AlertDialog(
            onDismissRequest = { showResetMalaDialog = false },
            title = { Text("Reset Current Mala?") },
            text = { Text("The current bead count will reset to 0 / 108. Completed malas and lifetime chants will remain unchanged.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetCurrentMala()
                        showResetMalaDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset Mala")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetMalaDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (showResetAllDialog) {
        AlertDialog(
            onDismissRequest = { showResetAllDialog = false },
            title = { Text("Reset All Jap Stats?") },
            text = { Text("This will reset current beads, completed malas, and lifetime chants back to 0. This cannot be undone.") },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.resetAllJap()
                        showResetAllDialog = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Reset All")
                }
            },
            dismissButton = {
                TextButton(onClick = { showResetAllDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    val scrollState = rememberScrollState()

    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scrollState)
                .padding(horizontal = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(modifier = Modifier.height(12.dp))

            // Header Section
            JapHeader(
                isVoiceActive = japState.isVoiceActive,
                isVoskReady = isVoskReady
            )

            // Silent Mode Indicator Badge
            SilentChantingBadge()

            // Custom Sacred Holy Name / Mantra Input (Blank by default - user enters their holy name)
            CustomHolyNameInputCard(
                names = japState.names,
                enabled = !japState.isVoiceActive,
                onAddName = { viewModel.addJapName(it) },
                onRemoveName = { viewModel.removeJapName(it) }
            )

            // Visual 108 Mala Beads Circle Component
            Box(
                modifier = Modifier
                    .size(280.dp)
                    .testTag("mala_beads_visual"),
                contentAlignment = Alignment.Center
            ) {
                MalaBeadsCircleCanvas(
                    currentBead = japState.currentBead,
                    totalBeads = 108,
                    isVoiceActive = japState.isVoiceActive,
                    isAnimating = japState.isIncrementAnimating,
                    modifier = Modifier.fillMaxSize()
                )

                // Central Counter Display
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.scale(scaleAnim.value)
                ) {
                    Text(
                        text = "${japState.currentBead}",
                        fontSize = 44.sp,
                        fontWeight = FontWeight.ExtraBold,
                        color = SaffronDark
                    )
                    Text(
                        text = "of 108 Beads",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (japState.targetName.isNotBlank()) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = japState.targetName,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = SaffronPrimary
                        )
                    }
                    if (japState.completedMalas > 0) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "${japState.completedMalas} ${if (japState.completedMalas == 1) "Mala" else "Malas"} Done",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldSuccess
                        )
                    }
                }
            }

            // Stats Summary Card (Completed Malas, Goal, Lifetime Chants)
            JapStatsCard(
                completedMalas = japState.completedMalas,
                targetGoal = japState.targetMalaGoal,
                lifetimeChants = japState.lifetimeChants,
                currentBead = japState.currentBead
            )

            // Voice Chanting Control Card
            VoiceJapCard(
                isVoiceActive = japState.isVoiceActive,
                targetName = japState.targetName,
                isBackgroundActive = japState.isBackgroundActive,
                statusText = japState.statusText,
                lastChantedWord = japState.lastChantedWord,
                isVoskReady = isVoskReady,
                onToggleVoice = {
                    if (japState.isVoiceActive) {
                        viewModel.stopVoiceJap(context)
                    } else {
                        if (japState.names.isEmpty()) {
                            viewModel.showJapNameRequired()
                        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            viewModel.startVoiceJap(context)
                        } else {
                            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    }
                }
            )

            // Target Goal Selector Chips
            MalaGoalSelector(
                currentGoal = japState.targetMalaGoal,
                onSelectGoal = { viewModel.setTargetMalaGoal(it) }
            )

            // Reset Options
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedButton(
                    onClick = { showResetMalaDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("reset_current_mala_button"),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset Mala (0)", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { showResetAllDialog = true },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("reset_all_jap_button"),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reset All", fontSize = 12.sp)
                }
            }

            Spacer(modifier = Modifier.height(28.dp))
        }
    }
}

@Composable
fun JapHeader(
    isVoiceActive: Boolean,
    isVoskReady: Boolean
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(
                    Brush.radialGradient(
                        colors = listOf(SaffronLight, SaffronPrimary)
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.TouchApp,
                contentDescription = "Naam Jap Counter",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column {
            Text(
                text = "Naam Jap Counter",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = if (isVoiceActive) "Voice Chanting Active • Strict Exact Match" else "108-Bead Mala Meditation",
                style = MaterialTheme.typography.bodySmall,
                color = if (isVoiceActive) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant,
                fontWeight = if (isVoiceActive) FontWeight.Bold else FontWeight.Normal
            )
        }
    }
}

@Composable
fun SilentChantingBadge() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.VolumeOff,
                contentDescription = null,
                tint = SaffronPrimary,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Silent Chanting: Zero chimes or beeps during meditation. Visual count only.",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun MalaBeadsCircleCanvas(
    currentBead: Int,
    totalBeads: Int,
    isVoiceActive: Boolean,
    isAnimating: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "halo")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 0.8f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Canvas(modifier = modifier) {
        val center = Offset(size.width / 2f, size.height / 2f)
        val radius = min(size.width, size.height) / 2f - 18.dp.toPx()

        // Draw track ring
        drawCircle(
            color = Color.LightGray.copy(alpha = 0.25f),
            radius = radius,
            center = center,
            style = Stroke(width = 2.dp.toPx())
        )

        // Draw 108 beads around the perimeter
        for (i in 0 until totalBeads) {
            // Angle starting from top (-90 deg) clockwise
            val angle = (-PI / 2.0) + (2.0 * PI * i / totalBeads)
            val beadX = center.x + radius * cos(angle).toFloat()
            val beadY = center.y + radius * sin(angle).toFloat()

            val isCurrent = i == currentBead
            val isChanted = i < currentBead
            val isMeru = i == 0 // Head Meru bead

            val beadRadius = when {
                isMeru -> 6.5.dp.toPx()
                isCurrent -> 5.5.dp.toPx()
                else -> 3.2.dp.toPx()
            }

            val beadColor = when {
                isCurrent -> SaffronDark
                isChanted -> SaffronPrimary
                isMeru -> SaffronLight
                else -> Color.Gray.copy(alpha = 0.35f)
            }

            // Glow aura for current bead
            if (isCurrent) {
                drawCircle(
                    color = SaffronPrimary.copy(alpha = if (isVoiceActive) pulseAlpha else 0.45f),
                    radius = beadRadius * 2.2f,
                    center = Offset(beadX, beadY)
                )
            }

            drawCircle(
                color = beadColor,
                radius = beadRadius,
                center = Offset(beadX, beadY)
            )
        }
    }
}

@Composable
fun JapStatsCard(
    completedMalas: Int,
    targetGoal: Int,
    lifetimeChants: Long,
    currentBead: Int
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("jap_stats_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = "Mala Progress",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$completedMalas of $targetGoal Malas (${(completedMalas * 108) + currentBead} chants)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "Lifetime Chants",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "$lifetimeChants",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = EmeraldSuccess
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            val progress = (completedMalas.toFloat() / targetGoal.coerceAtLeast(1)).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = SaffronPrimary,
                trackColor = SaffronPrimary.copy(alpha = 0.15f)
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CustomHolyNameInputCard(
    names: List<String>,
    enabled: Boolean,
    onAddName: (String) -> Unit,
    onRemoveName: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var newName by remember { mutableStateOf("") }
    fun submit() {
        if (newName.isNotBlank()) {
            onAddName(newName.trim())
            newName = ""
        }
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("custom_holy_name_card"),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = null,
                    tint = SaffronPrimary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Naam / Mantra for Jap",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = if (enabled) {
                    "Koi default naam nahi hai. Apna naam add karein (Devanagari me likhna best hai). Sirf pura naam bolne par hi count hoga; aadha ya galat naam count nahi hoga."
                } else {
                    "Naam badalne ke liye pehle Voice Jap pause karein."
                },
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    modifier = Modifier
                        .weight(1f)
                        .testTag("custom_holy_name_input"),
                    placeholder = { Text("Apna naam likhein") },
                    singleLine = true,
                    enabled = enabled,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { submit() }),
                    shape = RoundedCornerShape(10.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = { submit() },
                    enabled = enabled && newName.isNotBlank(),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = SaffronPrimary),
                    modifier = Modifier.testTag("add_holy_name_button")
                ) {
                    Text("Add")
                }
            }
            if (names.isNotEmpty()) {
                Spacer(modifier = Modifier.height(10.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    names.forEach { name ->
                        Card(
                            shape = RoundedCornerShape(16.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(text = name, style = MaterialTheme.typography.bodyMedium)
                                if (enabled) {
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Remove $name",
                                        modifier = Modifier
                                            .size(18.dp)
                                            .clickable { onRemoveName(name) },
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun VoiceJapCard(
    isVoiceActive: Boolean,
    targetName: String,
    isBackgroundActive: Boolean,
    statusText: String,
    lastChantedWord: String,
    isVoskReady: Boolean,
    onToggleVoice: () -> Unit
) {
    val cardColor by animateColorAsState(
        targetValue = if (isVoiceActive) IndigoListeningContainer else MaterialTheme.colorScheme.surface,
        label = "voiceCardColor"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (isVoiceActive) {
                    Modifier.border(2.dp, IndigoListening, RoundedCornerShape(14.dp))
                } else {
                    Modifier
                }
            )
            .testTag("voice_jap_card"),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(
                                if (isVoiceActive) IndigoListening else MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isVoiceActive) Icons.Default.Mic else Icons.Default.MicOff,
                            contentDescription = null,
                            tint = if (isVoiceActive) Color.White else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(
                            text = if (targetName.isNotBlank()) "Voice Jap: $targetName" else "Voice Jap Recognition",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isBackgroundActive) {
                                "Foreground Service • Screen-off chanting enabled"
                            } else if (isVoskReady) {
                                "Offline Vosk active • Exact speech match"
                            } else {
                                "Hindi recognizer • Exact speech match"
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = if (isBackgroundActive) EmeraldSuccess else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                if (isVoiceActive) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(IndigoListening)
                        )
                        Text(
                            text = "Listening",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = IndigoListening
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = if (isVoiceActive) IndigoListening else MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (lastChantedWord.isNotEmpty()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Last Recognized: \"$lastChantedWord\"",
                    style = MaterialTheme.typography.labelSmall,
                    color = EmeraldSuccess,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(14.dp))

            Button(
                onClick = onToggleVoice,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .testTag("toggle_voice_jap_button"),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (isVoiceActive) MaterialTheme.colorScheme.error else SaffronPrimary,
                    contentColor = Color.White
                )
            ) {
                Icon(
                    imageVector = if (isVoiceActive) Icons.Default.Pause else Icons.Default.Mic,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = if (isVoiceActive) "Pause Voice Jap" else if (targetName.isBlank()) "Add Holy Name to Start" else "Start Voice Jap ($targetName)",
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MalaGoalSelector(
    currentGoal: Int,
    onSelectGoal: (Int) -> Unit
) {
    val goals = listOf(1, 4, 16, 64, 108)

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Flag,
                contentDescription = null,
                tint = SaffronPrimary,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Target Mala Goal",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            goals.forEach { g ->
                FilterChip(
                    selected = currentGoal == g,
                    onClick = { onSelectGoal(g) },
                    label = { Text("$g ${if (g == 1) "Mala" else "Malas"}") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = SaffronPrimary,
                        selectedLabelColor = Color.White
                    ),
                    modifier = Modifier.testTag("goal_chip_$g")
                )
            }
        }
    }
}
