package com.example.sequencemakerbuddy.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sequencemakerbuddy.player.SequencePlayerViewModel
import com.example.sequencemakerbuddy.settings.SettingsManager

/**
 * Main player screen with 3 simulated balls, playback controls, audio selection, and delay.
 * The UI is intentionally greyscale so the ball colors stand out.
 */
@Composable
fun PlayerScreen(viewModel: SequencePlayerViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Initialize settings on first composition
    LaunchedEffect(Unit) {
        viewModel.initSettings(context)
        if (viewModel.folderConfigured.value) {
            viewModel.refreshFileList(context)
        }
        if (viewModel.audioFolderConfigured.value) {
            viewModel.refreshAudioFileList(context)
        }
    }

    // Folder picker for .smbuddy sequences
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.setFolder(context, it)
            viewModel.showSettings.value = false
        }
    }

    // Folder picker for audio files
    val audioFolderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.setAudioFolder(context, it)
        }
    }

    // Settings dialog
    if (viewModel.showSettings.value) {
        SettingsDialog(
            folderConfigured = viewModel.folderConfigured.value,
            currentFolder = SettingsManager(context).getSmbuddyFolderUri(),
            audioFolderConfigured = viewModel.audioFolderConfigured.value,
            currentAudioFolder = SettingsManager(context).getAudioFolderUri(),
            onPickFolder = { folderPicker.launch(null) },
            onPickAudioFolder = { audioFolderPicker.launch(null) },
            onDismiss = { viewModel.showSettings.value = false }
        )
    }

    // File browser dialog
    if (viewModel.showFileBrowser.value) {
        FileBrowserDialog(
            files = viewModel.smbuddyFiles.value,
            onFileSelected = { entry ->
                viewModel.showFileBrowser.value = false
                viewModel.loadBundle(context, entry.uri)
            },
            onDismiss = { viewModel.showFileBrowser.value = false }
        )
    }

    // Audio browser dialog
    if (viewModel.showAudioBrowser.value) {
        AudioBrowserDialog(
            files = viewModel.audioFiles.value,
            onFileSelected = { entry ->
                viewModel.showAudioBrowser.value = false
                viewModel.loadAudioFromUri(context, entry.uri)
            },
            onDismiss = { viewModel.showAudioBrowser.value = false }
        )
    }

    // Add delay label dialog
    if (viewModel.showAddLabelDialog.value) {
        AddDelayLabelDialog(
            currentDelay = viewModel.delaySeconds.floatValue,
            onConfirm = { name ->
                viewModel.addDelayLabel(context, name)
                viewModel.dismissAddLabelDialog()
            },
            onDismiss = { viewModel.dismissAddLabelDialog() }
        )
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar: Title + Settings gear
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Sequence Maker Buddy",
                color = MaterialTheme.colorScheme.onBackground,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold
            )
            IconButton(onClick = { viewModel.showSettings.value = true }) {
                Text(
                    text = "⚙",
                    fontSize = 24.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        // Project name
        Text(
            text = viewModel.projectName.value,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- Open Sequence + Open Audio buttons ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
        ) {
            OutlinedButton(
                onClick = {
                    if (viewModel.folderConfigured.value) {
                        viewModel.refreshFileList(context)
                        viewModel.showFileBrowser.value = true
                    } else {
                        viewModel.showSettings.value = true
                    }
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onBackground
                )
            ) {
                Text(
                    text = if (viewModel.sequenceLoaded.value) "✓ Sequence" else "Open Sequence"
                )
            }

            OutlinedButton(
                onClick = {
                    if (viewModel.audioFolderConfigured.value) {
                        viewModel.refreshAudioFileList(context)
                        viewModel.showAudioBrowser.value = true
                    } else {
                        viewModel.showSettings.value = true
                    }
                },
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onBackground
                )
            ) {
                val audioLabel = if (viewModel.audioLoaded.value) {
                    "✓ Audio"
                } else {
                    "Open Audio"
                }
                Text(text = audioLabel)
            }
        }

        // Audio file name (if loaded)
        viewModel.audioFileName.value?.let { name ->
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "🎵 $name",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- 3 Simulated Balls (these stay in COLOR) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            BallCircle(
                color = Color(viewModel.ballColor1.intValue),
                label = "Ball 1"
            )
            BallCircle(
                color = Color(viewModel.ballColor2.intValue),
                label = "Ball 2"
            )
            BallCircle(
                color = Color(viewModel.ballColor3.intValue),
                label = "Ball 3"
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // --- Delay Controls ---
        DelayControls(
            delaySeconds = viewModel.delaySeconds.floatValue,
            delayLabels = viewModel.delayLabels.value,
            onDelayChange = { viewModel.setDelay(context, it) },
            onIncrement = { viewModel.incrementDelay(context) },
            onDecrement = { viewModel.decrementDelay(context) },
            onApplyLabel = { viewModel.applyDelayLabel(context, it) },
            onAddLabel = { viewModel.showAddLabelDialog() },
            onRemoveLabel = { viewModel.removeDelayLabel(context, it) },
            enabled = viewModel.sequenceLoaded.value
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- Time display ---
        val timeMs = viewModel.currentTimeMs.intValue
        val totalMs = viewModel.totalDurationMs.intValue
        val seconds = timeMs / 1000
        val millis = (timeMs % 1000) / 10
        Text(
            text = String.format("%d:%02d.%02d", seconds / 60, seconds % 60, millis),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 36.sp,
            fontWeight = FontWeight.Light,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(8.dp))

        // --- Time slider ---
        val isSeeking = remember { mutableFloatStateOf(-1f) }
        Slider(
            value = if (isSeeking.floatValue >= 0f) isSeeking.floatValue
                    else timeMs.toFloat(),
            onValueChange = { newValue ->
                isSeeking.floatValue = newValue
            },
            onValueChangeFinished = {
                viewModel.seekTo(isSeeking.floatValue.toInt())
                isSeeking.floatValue = -1f
            },
            valueRange = 0f..totalMs.toFloat().coerceAtLeast(1f),
            enabled = viewModel.sequenceLoaded.value,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onBackground,
                activeTrackColor = MaterialTheme.colorScheme.onBackground,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        // Total duration label
        val totalSeconds = totalMs / 1000
        val totalMillis = (totalMs % 1000) / 10
        Text(
            text = String.format(
                "%d:%02d.%02d",
                totalSeconds / 60, totalSeconds % 60, totalMillis
            ),
            color = MaterialTheme.colorScheme.outline,
            fontSize = 12.sp,
            modifier = Modifier
                .fillMaxWidth()
                .padding(end = 12.dp),
            textAlign = TextAlign.End
        )

        Spacer(modifier = Modifier.height(16.dp))

        // --- Playback controls ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
        ) {
            Button(
                onClick = { viewModel.stop() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    contentColor = MaterialTheme.colorScheme.onSurface
                ),
                enabled = viewModel.sequenceLoaded.value
            ) {
                Text("■ Stop", fontSize = 16.sp)
            }

            if (viewModel.isPlaying.value) {
                Button(
                    onClick = { viewModel.pause() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text("❚❚ Pause", fontSize = 16.sp)
                }
            } else {
                Button(
                    onClick = { viewModel.play() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onBackground,
                        contentColor = MaterialTheme.colorScheme.background
                    ),
                    enabled = viewModel.sequenceLoaded.value
                ) {
                    Text("▶ Play", fontSize = 16.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Status info
        val statusText = if (!viewModel.folderConfigured.value) {
            "Tap ⚙ to set your .smbuddy folder"
        } else if (!viewModel.sequenceLoaded.value) {
            "Tap Open Sequence to load a .smbuddy file"
        } else if (viewModel.audioLoaded.value) {
            val delayStr = formatDelay(viewModel.delaySeconds.floatValue)
            "Sequence + audio loaded (delay $delayStr) — ready!"
        } else {
            "Sequence loaded (no audio)"
        }
        Text(
            text = statusText,
            color = MaterialTheme.colorScheme.outline,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(8.dp))
    }
}

/**
 * Settings dialog for configuring the .smbuddy folder and audio folder locations.
 */
@Composable
fun SettingsDialog(
    folderConfigured: Boolean,
    currentFolder: String?,
    audioFolderConfigured: Boolean,
    currentAudioFolder: String?,
    onPickFolder: () -> Unit,
    onPickAudioFolder: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Settings",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                // .smbuddy folder section
                Text(
                    text = ".smbuddy Folder",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (folderConfigured && currentFolder != null) {
                    Text(
                        text = "Folder set ✓",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "No folder configured",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onPickFolder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (folderConfigured) "Change Folder" else "Select Folder")
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Audio folder section
                Text(
                    text = "Audio Folder",
                    fontWeight = FontWeight.Medium,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                if (audioFolderConfigured && currentAudioFolder != null) {
                    Text(
                        text = "Folder set ✓",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "No folder configured",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onPickAudioFolder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (audioFolderConfigured) "Change Audio Folder" else "Select Audio Folder")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

/**
 * A single simulated ball rendered as a colored circle with a glow effect.
 * This is the ONE element that stays in full color.
 */
@Composable
fun BallCircle(color: Color, label: String) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(100.dp)
                .shadow(
                    elevation = 16.dp,
                    shape = CircleShape,
                    ambientColor = color,
                    spotColor = color
                )
                .clip(CircleShape)
                .background(color)
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp
        )
    }
}
