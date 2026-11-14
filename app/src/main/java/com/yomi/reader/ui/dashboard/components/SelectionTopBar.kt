package com.yomi.reader.ui.dashboard.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Deselect
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val YomiPurple = Color(0xFF8C43FF)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectionTopBar(
    selectedCount: Int,
    isAllSelected: Boolean,
    onCloseSelection: () -> Unit,
    onToggleSelectAll: () -> Unit,
    onShareSelected: () -> Unit,
    onDownloadSelected: (() -> Unit)? = null,
    onDeleteSelected: (() -> Unit)?,
    onMoveSelected: (() -> Unit)?,
    onStarSelected: () -> Unit,
    onRemoveFromFolder: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    TopAppBar(
        navigationIcon = {
            IconButton(onClick = onCloseSelection) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "Exit selection mode",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        title = {
            Text(
                text = "$selectedCount selected",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                fontSize = 18.sp,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        actions = {
            // Select All / Deselect All
            IconButton(onClick = onToggleSelectAll) {
                Icon(
                    imageVector = if (isAllSelected) Icons.Default.Deselect else Icons.Default.SelectAll,
                    contentDescription = if (isAllSelected) "Deselect all" else "Select all",
                    tint = YomiPurple
                )
            }

            // Share
            IconButton(onClick = onShareSelected) {
                Icon(
                    imageVector = Icons.Default.Share,
                    contentDescription = "Share selected",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Download selected to device
            if (onDownloadSelected != null) {
                IconButton(onClick = onDownloadSelected) {
                    Icon(
                        imageVector = Icons.Default.FileDownload,
                        contentDescription = "Download selected",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Move to Folder
            if (onMoveSelected != null) {
                IconButton(onClick = onMoveSelected) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.DriveFileMove,
                        contentDescription = "Move selected to folder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Remove from Folder (when inside a custom folder)
            if (onRemoveFromFolder != null) {
                IconButton(onClick = onRemoveFromFolder) {
                    Icon(
                        imageVector = Icons.Default.FolderOff,
                        contentDescription = "Remove selected from folder",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Add to Favorites
            IconButton(onClick = onStarSelected) {
                Icon(
                    imageVector = Icons.Default.Favorite,
                    contentDescription = "Add selected to Favorites",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            // Delete
            if (onDeleteSelected != null) {
                IconButton(onClick = onDeleteSelected) {
                    Icon(
                        imageVector = Icons.Default.DeleteOutline,
                        contentDescription = "Delete selected",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        modifier = modifier
    )
}
