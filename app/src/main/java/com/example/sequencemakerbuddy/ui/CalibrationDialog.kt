package com.example.sequencemakerbuddy.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sequencemakerbuddy.calibration.CalibrationEngine
import com.example.sequencemakerbuddy.player.SequencePlayerViewModel

/**
 * Calibration wizard dialog. Walks the user through:
 *  1. Audio phase   — listen for beeps and tap when heard (first beep = warm-up, ignore it).
 *  2. Visual phase  — watch for flashes and tap when seen (first flash = warm-up, ignore it).
 *  3. Results       — review computed delay, optionally test it via Verify, save as a preset.
 *  4. Verification  — playback synchronized beep+flash with the chosen delay to confirm sync.
 *
 * The warm-up stimulus is explicitly called out in the UI so the user can
 * mentally lock onto the rhythm without panicking about a missed tap.
 */
@Composable
fun CalibrationDialog(
    viewModel: SequencePlayerViewModel,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val phase = viewModel.calibration.phase.value

    val tapModifier = Modifier
        .fillMaxWidth()
        .height(120.dp)
        .padding(vertical = 8.dp)
        .clip(RoundedCornerShape(12.dp))

    AlertDialog(
        onDismissRequest = {
            // Don't allow dismissing in the middle of an audio/visual measurement
            // phase via tap-outside — those phases are interactive and need explicit
            // cancel. Verify runs from the main screen, never inside the dialog.
            if (phase != CalibrationEngine.Phase.RUNNING_AUDIO &&
                phase != CalibrationEngine.Phase.RUNNING_VISUAL
            ) {
                onDismiss()
            }
        },
        title = {
            Text(
                text = "🎯 Calibrate Audio Sync",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                when (phase) {
                    CalibrationEngine.Phase.IDLE,
                    CalibrationEngine.Phase.READY_AUDIO -> AudioPhaseIntro(viewModel)
                    CalibrationEngine.Phase.RUNNING_AUDIO -> AudioPhaseRunning(
                        viewModel = viewModel,
                        tapModifier = tapModifier
                    )
                    CalibrationEngine.Phase.READY_VISUAL -> VisualPhaseIntro(viewModel)
                    CalibrationEngine.Phase.RUNNING_VISUAL -> VisualPhaseRunning(
                        viewModel = viewModel,
                        tapModifier = tapModifier
                    )
                    CalibrationEngine.Phase.DONE -> ResultsPhase(viewModel)
                    // RUNNING_VERIFY is driven from the main screen, never seen here.
                    CalibrationEngine.Phase.RUNNING_VERIFY -> ResultsPhase(viewModel)
                }
            }
        },
        confirmButton = {
            when (phase) {
                CalibrationEngine.Phase.IDLE,
                CalibrationEngine.Phase.READY_AUDIO -> {
                    Button(onClick = { viewModel.calibration.startAudioPhase(context) }) {
                        Text("▶ Start Audio Phase")
                    }
                }
                CalibrationEngine.Phase.READY_VISUAL -> {
                    Button(onClick = { viewModel.calibration.startVisualPhase() }) {
                        Text("▶ Start Visual Phase")
                    }
                }
                CalibrationEngine.Phase.DONE -> {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
                else -> { /* no confirm button while running */ }
            }
        },
        dismissButton = {
            when (phase) {
                CalibrationEngine.Phase.RUNNING_AUDIO,
                CalibrationEngine.Phase.RUNNING_VISUAL -> {
                    TextButton(onClick = { viewModel.calibration.cancel() }) {
                        Text("Cancel")
                    }
                }
                CalibrationEngine.Phase.DONE -> { /* close handled by confirm */ }
                else -> {
                    TextButton(onClick = onDismiss) { Text("Close") }
                }
            }
        }
    )
}

@Composable
private fun AudioPhaseIntro(viewModel: SequencePlayerViewModel) {
    Column {
        Text(
            "Phase 1 of 2 — Audio",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "You'll hear ${CalibrationEngine.TOTAL_STIMULI} short beeps. " +
                "Tap the big button the moment you HEAR each beep.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        WarmupBanner(stimulusName = "beep")
        Spacer(Modifier.height(8.dp))
        Text(
            "Tip: this measures whatever audio path is active right now " +
                "(phone speaker, Bluetooth headphones, etc.). Make sure your " +
                "intended output device is connected before starting.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(12.dp))
        BallCalibrationPanel(viewModel)
    }
}

@Composable
private fun AudioPhaseRunning(
    viewModel: SequencePlayerViewModel,
    tapModifier: Modifier
) {
    val delivered = viewModel.calibration.stimuliDelivered.intValue
    val taps = viewModel.calibration.tapCount.intValue
    val isWarmup = delivered <= 1   // beep #1 is warm-up

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "🔊 LISTEN — tap when you HEAR each beep",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))

        // Live status line: warmup vs counted, plus tap count.
        val statusText = when {
            delivered == 0 -> "Waiting for first beep…"
            isWarmup -> "Beep $delivered / ${CalibrationEngine.TOTAL_STIMULI}  •  WARM-UP — don't tap"
            else -> "Beep $delivered / ${CalibrationEngine.TOTAL_STIMULI}  •  taps: $taps"
        }
        Text(
            text = statusText,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = if (isWarmup) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))
        Box(
            modifier = tapModifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable { viewModel.calibration.registerTap() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "TAP WHEN YOU HEAR A BEEP",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun VisualPhaseIntro(viewModel: SequencePlayerViewModel) {
    Column {
        Text(
            "Phase 1 done ✓",
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp,
            color = Color(0xFF4CAF50)
        )
        Text(
            "Audio perceived latency: ${viewModel.calibration.audioPerceivedMs.intValue} ms",
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Phase 2 of 2 — Visual",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "You'll see ${CalibrationEngine.TOTAL_STIMULI} bright flashes. " +
                "Tap the big button the moment you SEE each flash.",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(8.dp))
        WarmupBanner(stimulusName = "flash")
        Spacer(Modifier.height(8.dp))
        Text(
            "Watch whatever you'll actually be looking at while playing — " +
                "the on-screen flash square, or the real LTX balls if they're " +
                "connected and uploaded. Whichever you choose, name your " +
                "preset accordingly afterwards.",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.outline
        )
        Spacer(Modifier.height(12.dp))
        BallCalibrationPanel(viewModel)
    }
}

/**
 * Mini panel with two buttons:
 *
 *   📤 Upload Calibration PRG  — uploads a flash-pattern PRG to all
 *      connected balls so they can participate in the visual phase.
 *
 *   🔔 Test Start Signal       — sends the same PLAY UDP frame that
 *      normal playback uses, so the user can confirm the balls react
 *      and play the calibration PRG correctly BEFORE running the
 *      visual phase.
 *
 * Once "Start Visual Phase" is pressed, the engine will fire the same
 * start signal automatically at T=0 of the visual schedule.
 */
@Composable
private fun BallCalibrationPanel(viewModel: SequencePlayerViewModel) {
    val ballSlots by viewModel.ballManager.ballSlots.collectAsState()
    val isUploading by viewModel.ballManager.isUploading.collectAsState()
    val connectedCount = ballSlots.count { it != null }
    val status = viewModel.calibrationBallStatus.value
    val uploaded = viewModel.calibrationPrgUploaded.value

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(10.dp)
    ) {
        Text(
            "🟣 LTX Balls",
            fontWeight = FontWeight.Bold,
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(2.dp))
        Text(
            text = if (connectedCount == 0) {
                "No balls connected. Optional — calibrate against the phone " +
                    "screen flash and name the preset accordingly."
            } else {
                "$connectedCount ball(s) connected. Upload the calibration PRG " +
                    "and test the start signal so the balls flash during the " +
                    "visual phase."
            },
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        if (connectedCount > 0) {
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.uploadCalibrationToBalls() },
                    enabled = !isUploading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (uploaded) "📤 Re-upload PRG" else "📤 Upload PRG",
                        fontSize = 12.sp
                    )
                }
                OutlinedButton(
                    onClick = { viewModel.testCalibrationStartSignal() },
                    enabled = !isUploading,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("🔔 Test Start", fontSize = 12.sp)
                }
            }
            if (status.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = status,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    color = if (uploaded) Color(0xFF4CAF50)
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun VisualPhaseRunning(
    viewModel: SequencePlayerViewModel,
    tapModifier: Modifier
) {
    val delivered = viewModel.calibration.stimuliDelivered.intValue
    val taps = viewModel.calibration.tapCount.intValue
    val flashOn = viewModel.calibration.flashOn.value
    val isWarmup = delivered <= 1

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            "👁 WATCH — tap when you SEE a flash",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))

        val statusText = when {
            delivered == 0 -> "Waiting for first flash…"
            isWarmup -> "Flash $delivered / ${CalibrationEngine.TOTAL_STIMULI}  •  WARM-UP — don't tap"
            else -> "Flash $delivered / ${CalibrationEngine.TOTAL_STIMULI}  •  taps: $taps"
        }
        Text(
            text = statusText,
            fontSize = 13.sp,
            fontFamily = FontFamily.Monospace,
            color = if (isWarmup) MaterialTheme.colorScheme.tertiary
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(8.dp))
        FlashSquare(flashOn = flashOn)
        Spacer(Modifier.height(4.dp))

        Box(
            modifier = tapModifier
                .background(MaterialTheme.colorScheme.primaryContainer)
                .clickable { viewModel.calibration.registerTap() },
            contentAlignment = Alignment.Center
        ) {
            Text(
                "TAP WHEN YOU SEE A FLASH",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    }
}

@Composable
private fun ResultsPhase(viewModel: SequencePlayerViewModel) {
    val context = LocalContext.current
    var presetName by remember { mutableStateOf("") }
    var saved by remember { mutableStateOf(false) }

    val audioMs = viewModel.calibration.audioPerceivedMs.intValue
    val visualMs = viewModel.calibration.visualPerceivedMs.intValue
    val delaySec = viewModel.calibration.computedDelaySeconds.value

    Column {
        Text(
            "Calibration complete ✓",
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp,
            color = Color(0xFF4CAF50)
        )
        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Audio perceived:", fontSize = 13.sp)
            Text(
                "$audioMs ms",
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("Visual perceived:", fontSize = 13.sp)
            Text(
                "$visualMs ms",
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Spacer(Modifier.height(12.dp))

        Text(
            "Recommended delay:",
            fontSize = 13.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = formatDelay(delaySec),
            fontSize = 32.sp,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(16.dp))

        // ---- Save preset ----
        Text(
            "Save as preset:",
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(4.dp))
        OutlinedTextField(
            value = presetName,
            onValueChange = { presetName = it; saved = false },
            label = { Text("e.g. AirPods Pro, Phone speaker, JBL + balls") },
            singleLine = true,
            enabled = !saved,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))

        if (saved) {
            Text(
                "✓ Saved & applied. Close this and use 🔁 Verify on the main screen " +
                    "to fine-tune with the slider.",
                fontSize = 12.sp,
                color = Color(0xFF4CAF50)
            )
        } else {
            Button(
                onClick = {
                    if (presetName.isNotBlank()) {
                        viewModel.saveCalibrationPreset(context, presetName)
                        saved = true
                    }
                },
                enabled = presetName.isNotBlank(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.primary
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("💾 Save Preset")
            }
        }

        Spacer(Modifier.height(8.dp))

        OutlinedButton(
            onClick = { viewModel.calibration.armAudioPhase() },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("↻ Run Calibration Again", fontSize = 13.sp)
        }
    }
}

@Composable
private fun WarmupBanner(stimulusName: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.tertiaryContainer)
            .padding(8.dp)
    ) {
        Text(
            text = "⚠ The FIRST $stimulusName is a warm-up — DON'T tap on it. " +
                "Just listen/watch. Start tapping from the SECOND $stimulusName " +
                "onward (5 taps total).",
            fontSize = 12.sp,
            color = MaterialTheme.colorScheme.onTertiaryContainer
        )
    }
}

/**
 * Re-usable flash indicator: a 80dp-tall full-width box that turns bright
 * white when [flashOn] is true. Used both by the calibration dialog's
 * visual phase and by the main-screen verification inline UI.
 *
 * Package-visible (not private) so DelayControls can also render it.
 */
@Composable
internal fun FlashSquare(flashOn: Boolean) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(80.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(if (flashOn) Color.White else Color(0xFF111111))
            .border(
                width = 2.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(12.dp)
            ),
        contentAlignment = Alignment.Center
    ) {
        Text(
            if (flashOn) "⚡ FLASH" else "watching…",
            color = if (flashOn) Color.Black else MaterialTheme.colorScheme.outline,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
    }
}
