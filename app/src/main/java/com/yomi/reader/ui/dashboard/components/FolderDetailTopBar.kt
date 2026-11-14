package com.yomi.reader.ui.dashboard.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircleOutline
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yomi.reader.domain.model.DocumentSortOrder

/**
 * Dedicated top header for an opened Folder page (custom folder or Google Drive folder).
 * Replaces the global DashboardTopBar with:
 * - Row 1: Back navigation, prominent Folder Name & item count, Sort dropdown, Grid/List toggle, and Folder Tools overflow menu
 * - Row 2: In-folder search pill positioned directly beneath the folder header label
 */
@Composable
fun FolderDetailTopBar(
    folderName: String,
    subtitle: String,
    isDriveFolder: Boolean,
    isDriveConnected: Boolean = true,
    isDriveLinkMode: Boolean = false,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    currentSortOrder: DocumentSortOrder,
    onSortOrderChange: (DocumentSortOrder) -> Unit,
    isGridView: Boolean,
    onToggleViewMode: () -> Unit,
    hasFiles: Boolean,
    onBack: () -> Unit,
    onAddFilesToFolder: () -> Unit,
    onEnterSelectionMode: () -> Unit,
    onRenameFolder: () -> Unit,
    onRemoveFolder: () -> Unit,
    onSyncDrive: () -> Unit,
    onConnectDriveFolder: () -> Unit = {},
    onOpenDriveApp: () -> Unit = {},
    onCreateDriveSubfolder: () -> Unit = {},
    onUploadPdfToDrive: () -> Unit = {},
    onPasteDriveLink: () -> Unit = {},
    folderIcon: ImageVector = Icons.Default.Folder,
    folderAccentColor: Color = MaterialTheme.colorScheme.primary,
    folderContainerColor: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
    onCustomizeFolder: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }
    var showMoreMenu by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Row 1: Back button, Folder Title + Count, View/Sort Controls, and Folder Tools Menu
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onBack,
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back to Folders",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.width(6.dp))

            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(folderContainerColor),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = folderIcon,
                        contentDescription = null,
                        tint = folderAccentColor,
                        modifier = Modifier.size(19.dp)
                    )
                }
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = folderName,
                        style = MaterialTheme.typography.titleLarge,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            if (isDriveFolder) {
                IconButton(
                    onClick = onPasteDriveLink,
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.AddLink,
                        contentDescription = "Paste Drive folder link",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(21.dp)
                    )
                }
            }

            // Sort dropdown button
            Box {
                IconButton(
                    onClick = { showSortMenu = true },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Sort,
                        contentDescription = "Sort folder files",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(21.dp)
                    )
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    listOf(
                        DocumentSortOrder.DATE_DESC to "Date opened",
                        DocumentSortOrder.NAME_ASC to "Title (A–Z)",
                        DocumentSortOrder.NAME_DESC to "Title (Z–A)",
                        DocumentSortOrder.SIZE_DESC to "File size",
                        DocumentSortOrder.DATE_ASC to "Oldest opened"
                    ).forEach { (order, label) ->
                        val isSelected = order == currentSortOrder
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = label,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            trailingIcon = {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            onClick = {
                                onSortOrderChange(order)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }

            // Grid / List View Toggle
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onToggleViewMode() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGridView) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
                    contentDescription = if (isGridView) "Switch to list view" else "Switch to grid view",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }

            // Folder Management Overflow Menu
            Box {
                IconButton(
                    onClick = { showMoreMenu = true },
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Folder tools",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(21.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMoreMenu,
                    onDismissRequest = { showMoreMenu = false }
                ) {
                    if (!isDriveFolder) {
                        DropdownMenuItem(
                            text = { Text("Add PDFs to folder") },
                            leadingIcon = { Icon(Icons.Default.Add, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onAddFilesToFolder()
                            }
                        )
                    } else {
                        DropdownMenuItem(
                            text = { Text("Paste Drive folder link") },
                            leadingIcon = { Icon(Icons.Default.AddLink, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onPasteDriveLink()
                            }
                        )
                        if (isDriveConnected && !isDriveLinkMode) {
                            DropdownMenuItem(
                                text = { Text("Upload PDF to Drive") },
                                leadingIcon = { Icon(Icons.Default.CloudUpload, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    onUploadPdfToDrive()
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("New Subfolder") },
                                leadingIcon = { Icon(Icons.Default.CreateNewFolder, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    onCreateDriveSubfolder()
                                }
                            )
                        }
                        if (isDriveConnected) {
                            DropdownMenuItem(
                                text = { Text("Refresh Drive folder") },
                                leadingIcon = { Icon(Icons.Default.Refresh, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    onSyncDrive()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(if (isDriveConnected) "Connect device folder" else "Connect Drive folder") },
                            leadingIcon = { Icon(Icons.Default.CloudSync, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onConnectDriveFolder()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Open Drive app") },
                            leadingIcon = { Icon(Icons.Default.FolderOpen, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onOpenDriveApp()
                            }
                        )
                    }

                    if (!isDriveFolder && hasFiles) {
                        DropdownMenuItem(
                            text = { Text("Select files") },
                            leadingIcon = { Icon(Icons.Default.CheckCircleOutline, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onEnterSelectionMode()
                            }
                        )
                    }

                    if (!isDriveFolder) {
                        HorizontalDivider()
                        if (onCustomizeFolder != null) {
                            DropdownMenuItem(
                                text = { Text("Customize color & icon") },
                                leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null) },
                                onClick = {
                                    showMoreMenu = false
                                    onCustomizeFolder()
                                }
                            )
                        }
                        DropdownMenuItem(
                            text = { Text("Rename folder") },
                            leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                            onClick = {
                                showMoreMenu = false
                                onRenameFolder()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("Remove folder", color = MaterialTheme.colorScheme.error) },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            },
                            onClick = {
                                showMoreMenu = false
                                onRemoveFolder()
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Row 2: Search Bar directly underneath the Folder Name label
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(46.dp),
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f),
            tonalElevation = 1.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Search in $folderName",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(19.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Box(
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.CenterStart
                ) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = "Search in $folderName...",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(
                            color = MaterialTheme.colorScheme.onSurface,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Normal
                        ),
                        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                AnimatedVisibility(
                    visible = searchQuery.isNotEmpty(),
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    IconButton(
                        onClick = { onSearchQueryChange("") },
                        modifier = Modifier.size(30.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Clear,
                            contentDescription = "Clear search",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}


