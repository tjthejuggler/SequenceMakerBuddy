package com.example.sequencemakerbuddy.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.sequencemakerbuddy.player.SmbuddyFileEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class SortMode { NAME, DATE }

/**
 * Dialog that shows a scrollable list of .smbuddy files from the configured folder.
 * Sortable by name or date created.
 */
@Composable
fun FileBrowserDialog(
    files: List<SmbuddyFileEntry>,
    onFileSelected: (SmbuddyFileEntry) -> Unit,
    onDismiss: () -> Unit
) {
    val sortMode = remember { mutableStateOf(SortMode.NAME) }

    val sortedFiles = when (sortMode.value) {
        SortMode.NAME -> files.sortedBy { it.name.lowercase() }
        SortMode.DATE -> files.sortedByDescending { it.lastModified }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Select Sequence",
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        text = {
            Column {
                // Sort controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Sort by:",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SortButton(
                        label = "Name",
                        isActive = sortMode.value == SortMode.NAME,
                        onClick = { sortMode.value = SortMode.NAME }
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    SortButton(
                        label = "Date",
                        isActive = sortMode.value == SortMode.DATE,
                        onClick = { sortMode.value = SortMode.DATE }
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                if (sortedFiles.isEmpty()) {
                    Spacer(modifier = Modifier.height(24.dp))
                    Text(
                        text = "No .smbuddy files found in folder",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp,
                        modifier = Modifier.align(Alignment.CenterHorizontally)
                    )
                } else {
                    // Scrollable file list
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 400.dp)
                    ) {
                        items(sortedFiles) { file ->
                            FileListItem(
                                file = file,
                                onClick = { onFileSelected(file) }
                            )
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun SortButton(label: String, isActive: Boolean, onClick: () -> Unit) {
    TextButton(onClick = onClick) {
        Text(
            text = label,
            fontWeight = if (isActive) FontWeight.Bold else FontWeight.Normal,
            color = if (isActive) MaterialTheme.colorScheme.onBackground
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp
        )
    }
}

@Composable
private fun FileListItem(file: SmbuddyFileEntry, onClick: () -> Unit) {
    val dateFormat = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()) }
    val displayName = file.name.removeSuffix(".smbuddy")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp, horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = displayName,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (file.lastModified > 0) {
                Text(
                    text = dateFormat.format(Date(file.lastModified)),
                    fontSize = 11.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
