package com.yomi.reader.ui.dashboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yomi.reader.domain.model.DocumentItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun CustomFoldersSection(
    customFolders: Set<String>,
    customFolderStyles: Map<String, CustomFolderStyle> = emptyMap(),
    selectedFolder: String?,
    allDocuments: List<DocumentItem>,
    onSelectFolder: (String?) -> Unit,
    onCreateFolder: (name: String, colorKey: String?, iconKey: String?) -> Unit,
    onDeleteFolder: (String) -> Unit,
    onRenameFolder: (String, String) -> Unit = { _, _ -> },
    onUpdateFolderStyle: (name: String, colorKey: String, iconKey: String) -> Unit = { _, _, _ -> },
    filterQuery: String = "",
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    var showCreateDialog by remember { mutableStateOf(false) }
    var folderToManage by remember { mutableStateOf<String?>(null) }
    var folderToRename by remember { mutableStateOf<String?>(null) }
    var folderToCustomize by remember { mutableStateOf<String?>(null) }
    var folderToConfirmDelete by remember { mutableStateOf<String?>(null) }

    val visibleFolders = remember(customFolders, filterQuery) {
        val list = customFolders.toList()
        if (filterQuery.isBlank()) list else list.filter { it.contains(filterQuery.trim(), ignoreCase = true) }
    }

    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "My Folders",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                }
                Text(
                    text = "Tap to open folder • Long press to manage",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            TextButton(onClick = { showCreateDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text("New Folder")
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        if (visibleFolders.isEmpty()) {
            Surface(
                shape = RoundedCornerShape(18.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 28.dp, horizontal = 16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                        modifier = Modifier.size(34.dp)
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = if (filterQuery.isBlank()) "No folders yet" else "No folders match \"$filterQuery\"",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    if (filterQuery.isBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        TextButton(onClick = { showCreateDialog = true }) {
                            Text("+ Create your first folder")
                        }
                    }
                }
            }
        } else {
            // Spacious 2-column Folder Grid
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                visibleFolders.chunked(2).forEach { rowFolders ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowFolders.forEach { folderName ->
                            val isSelected = selectedFolder == folderName
                            val fileCount = allDocuments.count { it.parentPath == folderName }
                            val appearance = FolderAppearanceCatalog.resolve(folderName, customFolderStyles)

                            Surface(
                                shape = RoundedCornerShape(20.dp),
                                color = if (isSelected) appearance.containerColor else MaterialTheme.colorScheme.surface,
                                shadowElevation = if (isSelected) 2.5.dp else 1.5.dp,
                                border = if (isSelected) {
                                    BorderStroke(1.5.dp, appearance.accentColor)
                                } else {
                                    BorderStroke(1.dp, appearance.accentColor.copy(alpha = 0.28f))
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .height(118.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .clip(RoundedCornerShape(20.dp))
                                        .combinedClickable(
                                            onClick = { onSelectFolder(folderName) },
                                            onLongClick = {
                                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                folderToManage = folderName
                                            }
                                        )
                                        .padding(horizontal = 16.dp, vertical = 14.dp)
                                ) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(44.dp)
                                                    .clip(RoundedCornerShape(13.dp))
                                                    .background(
                                                        if (isSelected) {
                                                            MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                                        } else {
                                                            appearance.containerColor
                                                        }
                                                    ),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Icon(
                                                    imageVector = appearance.icon,
                                                    contentDescription = folderName,
                                                    tint = appearance.accentColor,
                                                    modifier = Modifier.size(24.dp)
                                                )
                                            }

                                            Surface(
                                                shape = RoundedCornerShape(50),
                                                color = if (isSelected) {
                                                    MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                                                } else {
                                                    appearance.containerColor.copy(alpha = 0.7f)
                                                }
                                            ) {
                                                Text(
                                                    text = "$fileCount",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    fontWeight = FontWeight.SemiBold,
                                                    color = appearance.accentColor,
                                                    modifier = Modifier.padding(horizontal = 9.dp, vertical = 4.dp)
                                                )
                                            }
                                        }

                                        Column {
                                            Text(
                                                text = folderName,
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(
                                                text = "$fileCount PDF${if (fileCount != 1) "s" else ""}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        if (rowFolders.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }

    // Long-press Manage / Customize / Remove Folder Dialog
    folderToManage?.let { targetFolder ->
        val count = allDocuments.count { it.parentPath == targetFolder }
        val appearance = FolderAppearanceCatalog.resolve(targetFolder, customFolderStyles)
        AlertDialog(
            onDismissRequest = { folderToManage = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(appearance.containerColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = appearance.icon,
                            contentDescription = null,
                            tint = appearance.accentColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = targetFolder,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "$count file${if (count != 1) "s" else ""}",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    FolderManageActionRow(
                        icon = Icons.Default.FolderOpen,
                        title = "Open Folder",
                        subtitle = "Show only files inside \"$targetFolder\"",
                        tint = appearance.accentColor,
                        onClick = {
                            folderToManage = null
                            onSelectFolder(targetFolder)
                        }
                    )
                    FolderManageActionRow(
                        icon = Icons.Default.Palette,
                        title = "Customize Color & Icon",
                        subtitle = "Change this folder's icon or color",
                        tint = appearance.accentColor,
                        onClick = {
                            folderToManage = null
                            folderToCustomize = targetFolder
                        }
                    )
                    FolderManageActionRow(
                        icon = Icons.Default.Edit,
                        title = "Rename Folder",
                        subtitle = "Change the folder name",
                        tint = MaterialTheme.colorScheme.primary,
                        onClick = {
                            folderToManage = null
                            folderToRename = targetFolder
                        }
                    )
                    FolderManageActionRow(
                        icon = Icons.Default.DeleteOutline,
                        title = "Remove Folder",
                        subtitle = "Delete folder (keeps files in All Files)",
                        tint = MaterialTheme.colorScheme.error,
                        onClick = {
                            folderToManage = null
                            folderToConfirmDelete = targetFolder
                        }
                    )
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { folderToManage = null }) {
                    Text("Close")
                }
            }
        )
    }

    // Customize Folder Color & Icon Dialog
    folderToCustomize?.let { targetFolder ->
        CustomizeFolderStyleDialog(
            folderName = targetFolder,
            customFolderStyles = customFolderStyles,
            onSave = { colorKey, iconKey ->
                onUpdateFolderStyle(targetFolder, colorKey, iconKey)
                folderToCustomize = null
            },
            onDismiss = { folderToCustomize = null }
        )
    }

    // Rename Folder Dialog
    folderToRename?.let { targetFolder ->
        var newNameInput by remember(targetFolder) { mutableStateOf(targetFolder) }
        AlertDialog(
            onDismissRequest = { folderToRename = null },
            title = { Text("Rename Folder") },
            text = {
                OutlinedTextField(
                    value = newNameInput,
                    onValueChange = { newNameInput = it },
                    label = { Text("Folder Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val trimmed = newNameInput.trim()
                        if (trimmed.isNotBlank() && trimmed != targetFolder) {
                            onRenameFolder(targetFolder, trimmed)
                        }
                        folderToRename = null
                    },
                    enabled = newNameInput.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToRename = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Confirm Remove Folder Dialog
    folderToConfirmDelete?.let { targetFolder ->
        AlertDialog(
            onDismissRequest = { folderToConfirmDelete = null },
            title = { Text("Remove \"$targetFolder\"?") },
            text = {
                Text("This will remove the \"$targetFolder\" folder. Your PDF files will remain safely available in All Files.")
            },
            confirmButton = {
                Button(
                    onClick = {
                        onDeleteFolder(targetFolder)
                        folderToConfirmDelete = null
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = Color.White
                    )
                ) {
                    Text("Remove")
                }
            },
            dismissButton = {
                TextButton(onClick = { folderToConfirmDelete = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Create New Folder Dialog
    if (showCreateDialog) {
        var folderNameInput by remember { mutableStateOf("") }
        var chosenColorKey by remember(customFolders.size) {
            mutableStateOf(FolderAppearanceCatalog.nextSuggestedColorKey(customFolders.size))
        }
        var chosenIconKey by remember { mutableStateOf("folder") }

        AlertDialog(
            onDismissRequest = { showCreateDialog = false },
            title = { Text("Create New Folder") },
            text = {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    OutlinedTextField(
                        value = folderNameInput,
                        onValueChange = { folderNameInput = it },
                        label = { Text("Folder Name") },
                        placeholder = { Text("e.g. Research, Receipts, Project") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FolderAppearanceEditor(
                        previewName = folderNameInput,
                        selectedColorKey = chosenColorKey,
                        selectedIconKey = chosenIconKey,
                        onSelectColorKey = { chosenColorKey = it },
                        onSelectIconKey = { chosenIconKey = it }
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (folderNameInput.isNotBlank()) {
                            onCreateFolder(folderNameInput.trim(), chosenColorKey, chosenIconKey)
                            showCreateDialog = false
                        }
                    },
                    enabled = folderNameInput.isNotBlank()
                ) {
                    Text("Create")
                }
            },
            dismissButton = {
                TextButton(onClick = { showCreateDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
private fun FolderManageActionRow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    subtitle: String,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (tint == MaterialTheme.colorScheme.error) tint else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
internal fun CustomizeFolderStyleDialog(
    folderName: String,
    customFolderStyles: Map<String, CustomFolderStyle>,
    onSave: (colorKey: String, iconKey: String) -> Unit,
    onDismiss: () -> Unit
) {
    val initial = FolderAppearanceCatalog.resolve(folderName, customFolderStyles)
    var chosenColorKey by remember(folderName) { mutableStateOf(initial.colorKey) }
    var chosenIconKey by remember(folderName) { mutableStateOf(initial.iconKey) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Customize \"$folderName\"") },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState())) {
                FolderAppearanceEditor(
                    previewName = folderName,
                    selectedColorKey = chosenColorKey,
                    selectedIconKey = chosenIconKey,
                    onSelectColorKey = { chosenColorKey = it },
                    onSelectIconKey = { chosenIconKey = it }
                )
            }
        },
        confirmButton = {
            Button(onClick = { onSave(chosenColorKey, chosenIconKey) }) {
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
