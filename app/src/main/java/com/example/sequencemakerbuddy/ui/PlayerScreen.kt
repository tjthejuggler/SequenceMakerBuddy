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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
 * Main player screen with 3 simulated balls and playback controls.
 * The UI is intentionally greyscale so the ball colors stand out.
 *
 * Instead of separate import buttons, uses a settings-configured folder
 * and a file browser popup to select .smbuddy bundles.
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
    }

    // Folder picker for settings
    val folderPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        uri?.let {
            viewModel.setFolder(context, it)
            viewModel.showSettings.value = false
        }
    }

    // Settings dialog
    if (viewModel.showSettings.value) {
        SettingsDialog(
            folderConfigured = viewModel.folderConfigured.value,
            currentFolder = SettingsManager(context).getSmbuddyFolderUri(),
            onPickFolder = { folderPicker.launch(null) },
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
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

        // --- Open Sequence button ---
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
                text = if (viewModel.sequenceLoaded.value) "✓ Open Sequence" else "Open Sequence"
            )
        }

        Spacer(modifier = Modifier.height(32.dp))

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

        Spacer(modifier = Modifier.height(32.dp))

        // --- Time display ---
        val timeMs = viewModel.currentTimeMs.intValue
        val seconds = timeMs / 1000
        val millis = (timeMs % 1000) / 10
        Text(
            text = String.format("%d:%02d.%02d", seconds / 60, seconds % 60, millis),
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 36.sp,
            fontWeight = FontWeight.Light,
            fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(24.dp))

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
                Text("⏹ Stop", fontSize = 16.sp)
            }

            if (viewModel.isPlaying.value) {
                Button(
                    onClick = { viewModel.pause() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.onSurface,
                        contentColor = MaterialTheme.colorScheme.surface
                    )
                ) {
                    Text("⏸ Pause", fontSize = 16.sp)
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

        Spacer(modifier = Modifier.weight(1f))

        // Status info
        val statusText = if (!viewModel.folderConfigured.value) {
            "Tap ⚙ to set your .smbuddy folder"
        } else if (!viewModel.sequenceLoaded.value) {
            "Tap \"Open Sequence\" to load a .smbuddy file"
        } else if (viewModel.audioLoaded.value) {
            "Sequence + audio loaded — ready to play!"
        } else {
            "Sequence loaded (no audio in bundle)"
        }
        Text(
            text = statusText,
            color = MaterialTheme.colorScheme.outline,
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Settings dialog for configuring the .smbuddy folder location.
 */
@Composable
fun SettingsDialog(
    folderConfigured: Boolean,
    currentFolder: String?,
    onPickFolder: () -> Unit,
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
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onPickFolder,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(if (folderConfigured) "Change Folder" else "Select Folder")
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
