package com.yomi.reader.ui.dashboard.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yomi.reader.domain.model.DocumentItem
import com.yomi.reader.ui.theme.yomiDriveAccentColor
import com.yomi.reader.ui.theme.yomiDriveButtonContainerColor
import com.yomi.reader.ui.theme.yomiDriveGreenAccentColor
import com.yomi.reader.ui.theme.yomiDriveGreenSurfaceColor
import com.yomi.reader.ui.theme.yomiDriveGreenTitleColor
import com.yomi.reader.ui.theme.yomiDriveOnSurfaceColor
import com.yomi.reader.ui.theme.yomiDriveSubtleTextColor
import com.yomi.reader.ui.theme.yomiDriveSurfaceColor

@Composable
internal fun ImportDocumentChooserDialog(
    connectedDriveFolderName: String?,
    hasConnectedDriveFolder: Boolean,
    isSignedIn: Boolean = true,
    onPickDriveFile: () -> Unit,
    onOpenConnectedDriveFolder: () -> Unit,
    onChangeConnectedDriveFolder: () -> Unit,
    onOpenDriveFolderLink: () -> Unit = {},
    onImportLocalDocument: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add or Open Document",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            val driveBg = yomiDriveSurfaceColor()
            val driveOnBg = yomiDriveOnSurfaceColor()
            val driveAccent = yomiDriveAccentColor()
            val driveSubtle = yomiDriveSubtleTextColor()
            val greenBg = yomiDriveGreenSurfaceColor()
            val greenTitle = yomiDriveGreenTitleColor()
            val greenAccent = yomiDriveGreenAccentColor()

            val driveCardBg = if (isSignedIn) {
                driveBg
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            }
            val driveTitleColor = if (isSignedIn) driveOnBg else MaterialTheme.colorScheme.onSurfaceVariant
            val driveTintColor = if (isSignedIn) driveAccent else MaterialTheme.colorScheme.onSurfaceVariant
            val driveSubtitleColor = if (isSignedIn) driveSubtle else MaterialTheme.colorScheme.onSurfaceVariant
            val folderCardBg = if (isSignedIn) {
                greenBg
            } else {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            }
            val folderTitleColor = if (isSignedIn) greenTitle else MaterialTheme.colorScheme.onSurfaceVariant
            val folderTintColor = if (isSignedIn) greenAccent else MaterialTheme.colorScheme.onSurfaceVariant

            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = driveCardBg,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onPickDriveFile()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.CloudDone,
                            contentDescription = null,
                            tint = driveTintColor,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Pick File from Google Drive",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = driveTitleColor
                            )
                            Text(
                                text = if (isSignedIn) {
                                    "Select Drive files directly into Yomi"
                                } else {
                                    "Sign in required to use Google Drive"
                                },
                                fontSize = 12.sp,
                                color = driveSubtitleColor
                            )
                        }
                    }
                }

                if (hasConnectedDriveFolder) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = folderCardBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                onOpenConnectedDriveFolder()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderShared,
                                contentDescription = null,
                                tint = folderTintColor,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Open Drive Folder",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = folderTitleColor
                                )
                                Text(
                                    text = if (isSignedIn) {
                                        "Browse ${connectedDriveFolderName ?: "connected folder"} (read-safe) & save local copies"
                                    } else {
                                        "Sign in required to browse Drive folders"
                                    },
                                    fontSize = 12.sp,
                                    color = driveSubtitleColor
                                )
                            }
                            if (isSignedIn) {
                                IconButton(
                                    onClick = {
                                        onDismiss()
                                        onChangeConnectedDriveFolder()
                                    },
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.SwapHoriz,
                                        contentDescription = "Change connected folder",
                                        tint = folderTintColor,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = folderCardBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onDismiss()
                                onChangeConnectedDriveFolder()
                            }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderShared,
                                contentDescription = null,
                                tint = folderTintColor,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Connect Drive Folder",
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                    color = folderTitleColor
                                )
                                Text(
                                    text = if (isSignedIn) {
                                        "Browse Drive read-only & open local copies in Yomi"
                                    } else {
                                        "Sign in required to connect Google Drive"
                                    },
                                    fontSize = 12.sp,
                                    color = driveSubtitleColor
                                )
                            }
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = if (isSignedIn) driveBg.copy(alpha = 0.85f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onOpenDriveFolderLink()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddLink,
                            contentDescription = null,
                            tint = driveTintColor,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Paste Drive Folder Link",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = driveTitleColor
                            )
                            Text(
                                text = if (isSignedIn) {
                                    "Open any shared Google Drive folder link in Yomi"
                                } else {
                                    "Sign in required to open Drive folder links"
                                },
                                fontSize = 12.sp,
                                color = driveSubtitleColor
                            )
                        }
                    }
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onDismiss()
                            onImportLocalDocument()
                        }
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Import & Convert Document",
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "PDF, Word, Excel, or PPT (auto-converts to PDF)",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun UnsupportedFormatDialog(
    fileName: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(36.dp)
            )
        },
        title = {
            Text(
                text = "File Format Not Supported",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "The file \"$fileName\" cannot be opened.\n\nYomi is a PDF reader and converter. We support PDF, Word (.docx), Excel (.xlsx), and PowerPoint (.pptx).",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(onClick = onDismiss) {
                Text("OK")
            }
        }
    )
}

@Composable
internal fun RenameDocumentDialog(
    document: DocumentItem,
    onConfirmRename: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var newTitleInput by remember(document.id) {
        mutableStateOf(document.title.removeSuffix(".pdf"))
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.Edit,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Rename Document",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column {
                Text(
                    text = "Enter a new name for this PDF:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedTextField(
                    value = newTitleInput,
                    onValueChange = { newTitleInput = it },
                    label = { Text("Document Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = newTitleInput.trim()
                    if (trimmed.isNotEmpty()) {
                        onConfirmRename(trimmed)
                    }
                    onDismiss()
                },
                enabled = newTitleInput.isNotBlank()
            ) {
                Text("Rename")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun CreateCustomFolderDialog(
    existingFolderCount: Int = 0,
    onConfirmCreate: (name: String, colorKey: String?, iconKey: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var folderNameInput by remember { mutableStateOf("") }
    var chosenColorKey by remember(existingFolderCount) {
        mutableStateOf(FolderAppearanceCatalog.nextSuggestedColorKey(existingFolderCount))
    }
    var chosenIconKey by remember { mutableStateOf("folder") }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.CreateNewFolder,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(32.dp)
            )
        },
        title = {
            Text(
                text = "Create New Folder",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "Organize your PDFs into custom folders:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = folderNameInput,
                    onValueChange = { folderNameInput = it },
                    label = { Text("Folder Name") },
                    placeholder = { Text("e.g. Work, Study, Receipts") },
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
                    val trimmed = folderNameInput.trim()
                    if (trimmed.isNotEmpty()) {
                        onConfirmCreate(trimmed, chosenColorKey, chosenIconKey)
                    }
                    onDismiss()
                },
                enabled = folderNameInput.isNotBlank()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun AddFilesToFolderDialog(
    targetFolder: String,
    allDocuments: List<DocumentItem>,
    onConfirmAdd: (Set<Long>) -> Unit,
    onRequestImportPdf: () -> Unit,
    onDismiss: () -> Unit
) {
    val candidates = remember(allDocuments, targetFolder) {
        allDocuments.filter { it.parentPath != targetFolder }
    }
    var filterText by remember { mutableStateOf("") }
    var pickedIds by remember { mutableStateOf(setOf<Long>()) }
    val filteredCandidates = remember(candidates, filterText) {
        if (filterText.isBlank()) candidates
        else candidates.filter { it.title.contains(filterText.trim(), ignoreCase = true) }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Add PDFs to \"$targetFolder\"",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                if (candidates.isEmpty()) {
                    Text(
                        text = "All PDFs in your library are already inside \"$targetFolder\", or your library has no other files yet.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    OutlinedTextField(
                        value = filterText,
                        onValueChange = { filterText = it },
                        placeholder = { Text("Filter library PDFs...") },
                        leadingIcon = { Icon(Icons.Default.Search, contentDescription = null, modifier = Modifier.size(18.dp)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(filteredCandidates, key = { it.id }) { doc ->
                            val isChecked = doc.id in pickedIds
                            Surface(
                                onClick = {
                                    pickedIds = if (isChecked) pickedIds - doc.id else pickedIds + doc.id
                                },
                                shape = RoundedCornerShape(12.dp),
                                color = if (isChecked) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f) else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { checked ->
                                            pickedIds = if (checked) pickedIds + doc.id else pickedIds - doc.id
                                        }
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = doc.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = doc.formattedSize,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (candidates.isNotEmpty()) {
                Button(
                    onClick = {
                        if (pickedIds.isNotEmpty()) {
                            onConfirmAdd(pickedIds)
                        }
                        onDismiss()
                    },
                    enabled = pickedIds.isNotEmpty()
                ) {
                    Text(if (pickedIds.isEmpty()) "Add" else "Add (${pickedIds.size})")
                }
            } else {
                Button(
                    onClick = {
                        onDismiss()
                        onRequestImportPdf()
                    }
                ) {
                    Text("Import PDF")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun RenameCustomFolderDialog(
    targetFolder: String,
    onConfirmRename: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var renamedInput by remember(targetFolder) { mutableStateOf(targetFolder) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Rename Folder") },
        text = {
            OutlinedTextField(
                value = renamedInput,
                onValueChange = { renamedInput = it },
                label = { Text("Folder Name") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = renamedInput.trim()
                    if (trimmed.isNotBlank() && trimmed != targetFolder) {
                        onConfirmRename(trimmed)
                    }
                    onDismiss()
                },
                enabled = renamedInput.isNotBlank()
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

@Composable
internal fun RemoveCustomFolderDialog(
    targetFolder: String,
    onConfirmRemove: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove \"$targetFolder\"?") },
        text = {
            Text("This removes the \"$targetFolder\" folder. Your PDF files will remain safely in All Files.")
        },
        confirmButton = {
            Button(
                onClick = {
                    onConfirmRemove()
                    onDismiss()
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
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun ConversionProgressDialog(
    documentTitle: String,
    progressPercent: Int
) {
    val animatedProgress by animateFloatAsState(
        targetValue = (progressPercent / 100f).coerceIn(0f, 1f),
        animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
        label = "DashboardConversionProgress"
    )
    AlertDialog(
        onDismissRequest = {},
        title = {
            Text(
                text = "Converting to PDF (${progressPercent}%)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(76.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        progress = { animatedProgress.coerceAtLeast(0.04f) },
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.primaryContainer,
                        strokeWidth = 3.5.dp,
                        strokeCap = StrokeCap.Round,
                        modifier = Modifier.fillMaxSize()
                    )
                    Surface(
                        shape = CircleShape,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f),
                        modifier = Modifier.size(56.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.PictureAsPdf,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(14.dp))
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primaryContainer,
                    strokeCap = StrokeCap.Round,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = "${progressPercent}%",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Converting \"$documentTitle\" into a PDF.",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        },
        confirmButton = {}
    )
}

@Composable
internal fun DuplicateImportDialog(
    existingTitle: String,
    suggestedTitle: String,
    onImportCopy: () -> Unit,
    onOpenExisting: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "File Already Imported",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "\"$existingTitle\" is already in your library.\n\nWould you like to import a copy as \"$suggestedTitle\", or open the existing file?",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(onClick = onImportCopy) {
                Text("Import Anyway")
            }
        },
        dismissButton = {
            TextButton(onClick = onOpenExisting) {
                Text("Open Existing")
            }
        }
    )
}

@Composable
internal fun MoveToFolderDialog(
    title: String,
    currentFolderPath: String?,
    customFolders: Collection<String>,
    customFolderStyles: Map<String, CustomFolderStyle> = emptyMap(),
    onSelectFolder: (folderPath: String) -> Unit,
    onCreateAndMove: (folderName: String, colorKey: String?, iconKey: String?) -> Unit,
    onDismiss: () -> Unit
) {
    var showNewFolderInput by remember { mutableStateOf(false) }
    var newFolderName by remember { mutableStateOf("") }
    var chosenColorKey by remember(customFolders.size) {
        mutableStateOf(FolderAppearanceCatalog.nextSuggestedColorKey(customFolders.size))
    }
    var chosenIconKey by remember { mutableStateOf("folder") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Select destination folder:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    onClick = { onSelectFolder("") },
                    shape = RoundedCornerShape(10.dp),
                    color = if (currentFolderPath == "") {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(10.dp))
                        Text("Root / No Folder", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }

                customFolders.forEach { folderName ->
                    val isCurrent = currentFolderPath == folderName
                    val appearance = FolderAppearanceCatalog.resolve(folderName, customFolderStyles)
                    Surface(
                        onClick = { onSelectFolder(folderName) },
                        shape = RoundedCornerShape(10.dp),
                        color = if (isCurrent) {
                            appearance.containerColor
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = appearance.icon,
                                contentDescription = null,
                                tint = appearance.accentColor,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(folderName, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }

                if (showNewFolderInput) {
                    Spacer(modifier = Modifier.height(4.dp))
                    OutlinedTextField(
                        value = newFolderName,
                        onValueChange = { newFolderName = it },
                        label = { Text("New Folder Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    FolderAppearanceEditor(
                        previewName = newFolderName,
                        selectedColorKey = chosenColorKey,
                        selectedIconKey = chosenIconKey,
                        onSelectColorKey = { chosenColorKey = it },
                        onSelectIconKey = { chosenIconKey = it }
                    )
                    Button(
                        onClick = {
                            val trimmed = newFolderName.trim()
                            if (trimmed.isNotBlank()) {
                                onCreateAndMove(trimmed, chosenColorKey, chosenIconKey)
                            }
                        },
                        enabled = newFolderName.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Create & Move")
                    }
                } else {
                    TextButton(
                        onClick = { showNewFolderInput = true },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("New Folder")
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun BatchDeleteDocumentsDialog(
    selectedCount: Int,
    onConfirmDelete: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Delete $selectedCount Document(s)?",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Are you sure you want to remove the selected documents from Yomi? This action cannot be undone.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmDelete,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = MaterialTheme.colorScheme.onError
                )
            ) {
                Text("Delete")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun DriveSignInRequiredDialog(
    onSignIn: () -> Unit,
    onContinueWithoutDrive: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onContinueWithoutDrive,
        icon = {
            Icon(
                imageVector = Icons.Default.CloudOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary
            )
        },
        title = {
            Text(
                text = "Sign In to Use Google Drive",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Text(
                text = "Google Drive folders require a signed-in account connected to Google Drive. Sign in now to connect and browse your Drive workspace, or continue using Yomi with your local files without Google Drive.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onSignIn,
                colors = ButtonDefaults.buttonColors(
                    containerColor = yomiDriveButtonContainerColor(),
                    contentColor = Color.White
                )
            ) {
                Text("Sign In")
            }
        },
        dismissButton = {
            TextButton(onClick = onContinueWithoutDrive) {
                Text("Continue Without Drive")
            }
        }
    )
}
