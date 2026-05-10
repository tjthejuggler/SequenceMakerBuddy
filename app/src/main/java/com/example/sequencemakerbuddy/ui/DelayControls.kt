package com.example.sequencemakerbuddy.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sequencemakerbuddy.model.DelayLabel

/**
 * Delay controls: slider from -10 to +10 seconds, increment/decrement buttons,
 * and a dropdown to apply saved delay labels.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DelayControls(
    delaySeconds: Float,
    delayLabels: List<DelayLabel>,
    onDelayChange: (Float) -> Unit,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
    onApplyLabel: (String) -> Unit,
    onAddLabel: () -> Unit,
    onRemoveLabel: (String) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Label
        Text(
            text = "Audio Delay",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )

        Spacer(modifier = Modifier.height(4.dp))

        // Current delay value display
        val sign = if (delaySeconds >= 0) "+" else ""
        Text(
            text = "$sign${String.format("%.2f", delaySeconds)}s",
            color = MaterialTheme.colorScheme.onBackground,
            fontSize = 24.sp,
            fontWeight = FontWeight.Light,
            fontFamily = FontFamily.Monospace
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Slider: -10 to +10, 0 in the middle
        Slider(
            value = delaySeconds,
            onValueChange = onDelayChange,
            valueRange = -10f..10f,
            enabled = enabled,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.onBackground,
                activeTrackColor = MaterialTheme.colorScheme.onBackground,
                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
            )
        )

        // Slider labels
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("-10s", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
            Text("0", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
            Text("+10s", fontSize = 10.sp, color = MaterialTheme.colorScheme.outline)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Increment/Decrement buttons
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedButton(
                onClick = onDecrement,
                enabled = enabled && delaySeconds > -10f,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onBackground
                )
            ) {
                Text("−0.05s", fontSize = 13.sp)
            }

            OutlinedButton(
                onClick = onIncrement,
                enabled = enabled && delaySeconds < 10f,
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.onBackground
                )
            ) {
                Text("+0.05s", fontSize = 13.sp)
            }
        }

        // Delay labels dropdown (only show if there are labels)
        if (delayLabels.isNotEmpty()) {
            Spacer(modifier = Modifier.height(12.dp))

            var labelsExpanded by remember { mutableStateOf(false) }

            ExposedDropdownMenuBox(
                expanded = labelsExpanded,
                onExpandedChange = { labelsExpanded = it && enabled }
            ) {
                OutlinedTextField(
                    value = "Apply saved delay…",
                    onValueChange = {},
                    readOnly = true,
                    enabled = enabled,
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = labelsExpanded)
                    },
                    modifier = Modifier
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                )

                ExposedDropdownMenu(
                    expanded = labelsExpanded,
                    onDismissRequest = { labelsExpanded = false }
                ) {
                    delayLabels.forEach { label ->
                        DropdownMenuItem(
                            text = {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "${label.name} (${formatDelay(label.delaySeconds)})",
                                        fontSize = 14.sp
                                    )
                                    TextButton(
                                        onClick = {
                                            onRemoveLabel(label.name)
                                        }
                                    ) {
                                        Text("✕", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                                    }
                                }
                            },
                            onClick = {
                                onApplyLabel(label.name)
                                labelsExpanded = false
                            }
                        )
                    }
                }
            }
        }

        // Add label button
        Spacer(modifier = Modifier.height(4.dp))
        OutlinedButton(
            onClick = onAddLabel,
            enabled = enabled,
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            ),
            modifier = Modifier.padding(horizontal = 8.dp)
        ) {
            Text("💾 Save delay as label", fontSize = 12.sp)
        }
    }
}

/**
 * Add delay label dialog.
 */
@Composable
fun AddDelayLabelDialog(
    currentDelay: Float,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var labelName by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Save Delay Label",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                Text(
                    text = "Current delay: ${formatDelay(currentDelay)}",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = labelName,
                    onValueChange = { labelName = it },
                    label = { Text("Label name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (labelName.isNotBlank()) onConfirm(labelName) },
                enabled = labelName.isNotBlank()
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

/** Format a delay value for display, e.g. "+1.50s" or "-0.05s" */
fun formatDelay(seconds: Float): String {
    val sign = if (seconds >= 0) "+" else ""
    return "$sign${String.format("%.2f", seconds)}s"
}
