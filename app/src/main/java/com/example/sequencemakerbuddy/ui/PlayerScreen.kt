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
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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

/**
 * Main player screen with 3 simulated balls and playback controls.
 */
@Composable
fun PlayerScreen(viewModel: SequencePlayerViewModel, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // File picker for .smbuddy sequence files
    val sequencePicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadSequence(context, it) }
    }

    // File picker for audio files
    val audioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        uri?.let { viewModel.loadAudio(context, it) }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFF1A1A2E))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Title
        Text(
            text = "Sequence Maker Buddy",
            color = Color.White,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Project name
        Text(
            text = viewModel.projectName.value,
            color = Color(0xFFAAAAAA),
            fontSize = 14.sp
        )

        Spacer(modifier = Modifier.height(24.dp))

        // --- Load buttons ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
        ) {
            OutlinedButton(
                onClick = { sequencePicker.launch(arrayOf("application/json", "application/octet-stream", "*/*")) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00D4FF))
            ) {
                Text(
                    text = if (viewModel.sequenceLoaded.value) "✓ Sequence" else "Load Sequence"
                )
            }

            OutlinedButton(
                onClick = { audioPicker.launch(arrayOf("audio/*")) },
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFF00D4FF))
            ) {
                Text(
                    text = if (viewModel.audioLoaded.value) "✓ Audio" else "Load Audio"
                )
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // --- 3 Simulated Balls ---
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
            color = Color.White,
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
                    containerColor = Color(0xFF444466)
                ),
                enabled = viewModel.sequenceLoaded.value
            ) {
                Text("⏹ Stop", fontSize = 16.sp)
            }

            if (viewModel.isPlaying.value) {
                Button(
                    onClick = { viewModel.pause() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFFFF6B35)
                    )
                ) {
                    Text("⏸ Pause", fontSize = 16.sp)
                }
            } else {
                Button(
                    onClick = { viewModel.play() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF00C853)
                    ),
                    enabled = viewModel.sequenceLoaded.value
                ) {
                    Text("▶ Play", fontSize = 16.sp)
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Status info
        Text(
            text = "Load a .smbuddy file and audio to start",
            color = Color(0xFF666688),
            fontSize = 12.sp,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * A single simulated ball rendered as a colored circle with a glow effect.
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
            color = Color(0xFFAAAAAA),
            fontSize = 12.sp
        )
    }
}
