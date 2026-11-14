package com.yomi.reader.ui.dashboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yomi.reader.domain.model.DocumentItem
import com.yomi.reader.ui.theme.yomiDriveAccentColor
import com.yomi.reader.ui.theme.yomiDriveBorderColor
import com.yomi.reader.ui.theme.yomiDriveButtonContainerColor
import com.yomi.reader.ui.theme.yomiDriveGreenAccentColor
import com.yomi.reader.ui.theme.yomiDriveOnSurfaceColor
import com.yomi.reader.ui.theme.yomiDriveSubtleTextColor
import com.yomi.reader.ui.theme.yomiDriveSurfaceColor
import com.yomi.reader.domain.model.DocumentType
import com.yomi.reader.domain.model.DriveFileItem
import com.yomi.reader.domain.model.SavedDriveLink
import com.yomi.reader.ui.theme.ExcelBadgeContainer
import com.yomi.reader.ui.theme.ExcelBadgeContent
import com.yomi.reader.ui.theme.PdfBadgeContainer
import com.yomi.reader.ui.theme.PdfBadgeContent
import com.yomi.reader.ui.theme.PptBadgeContainer
import com.yomi.reader.ui.theme.PptBadgeContent
import com.yomi.reader.ui.theme.WordBadgeContainer
import com.yomi.reader.ui.theme.WordBadgeContent
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val DriveDateFormatter = object : ThreadLocal<SimpleDateFormat>() {
    override fun initialValue(): SimpleDateFormat = SimpleDateFormat("MMM d", Locale.getDefault())
}

private fun formatDriveFileSize(bytes: Long): String {
    if (bytes <= 0L) return "Drive file"
    val units = arrayOf("B", "KB", "MB", "GB")
    val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt().coerceIn(0, units.size - 1)
    return "${DecimalFormat("#,##0.#").format(bytes / Math.pow(1024.0, digitGroups.toDouble()))} ${units[digitGroups]}"
}

/**
 * Compact header notice inside a connected Drive folder emphasizing read-safe browsing
 * and offering quick append-only actions (Add Folder / Upload PDF).
 */
@Composable
internal fun DriveReadOnlyNoticeBanner(
    isLinkMode: Boolean = false,
    onUploadPdf: () -> Unit,
    onCreateSubfolder: () -> Unit,
    onPasteDriveLink: () -> Unit = {},
    onOpenInDriveApp: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val driveBg = yomiDriveSurfaceColor()
    val driveOnBg = yomiDriveOnSurfaceColor()
    val driveAccent = yomiDriveAccentColor()
    val driveBtnBg = yomiDriveButtonContainerColor()
    val driveBorder = yomiDriveBorderColor()
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(14.dp),
        color = driveBg.copy(alpha = 0.9f),
        border = BorderStroke(1.dp, driveBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = if (isLinkMode) Icons.Default.Link else Icons.Default.Lock,
                    contentDescription = null,
                    tint = driveAccent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = if (isLinkMode) {
                        "Shared Drive link • Tap files to download or convert local Yomi copies"
                    } else {
                        "Protected Drive view • Files save as local copies in Yomi"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    color = driveOnBg,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (isLinkMode) {
                    Surface(
                        onClick = onOpenInDriveApp,
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, driveBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.FolderOpen,
                                contentDescription = null,
                                tint = driveAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Open in Drive",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = driveOnBg
                            )
                        }
                    }

                    Surface(
                        onClick = onPasteDriveLink,
                        shape = RoundedCornerShape(10.dp),
                        color = driveBtnBg,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddLink,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Add / Switch Link",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                } else {
                    Surface(
                        onClick = onCreateSubfolder,
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.surface,
                        border = BorderStroke(1.dp, driveBorder),
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CreateNewFolder,
                                contentDescription = null,
                                tint = driveAccent,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "New Folder",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = driveOnBg
                            )
                        }
                    }

                    Surface(
                        onClick = onUploadPdf,
                        shape = RoundedCornerShape(10.dp),
                        color = driveBtnBg,
                        modifier = Modifier.weight(1f)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = "Upload PDF",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun DriveFileCard(
    driveFile: DriveFileItem,
    isSavedLocally: Boolean,
    onOpenAsLocalCopy: () -> Unit,
    onSaveFreshCopy: () -> Unit,
    onShareLocalCopy: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    val (badgeBg, badgeFg, shortLabel) = when (driveFile.type) {
        DocumentType.PDF -> Triple(PdfBadgeContainer, PdfBadgeContent, "PDF")
        DocumentType.WORD -> Triple(WordBadgeContainer, WordBadgeContent, "DOC")
        DocumentType.EXCEL -> Triple(ExcelBadgeContainer, ExcelBadgeContent, "XLS")
        DocumentType.POWERPOINT -> Triple(PptBadgeContainer, PptBadgeContent, "PPT")
        DocumentType.UNKNOWN -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "FILE"
        )
    }

    val shortDate = remember(driveFile.lastModified) {
        if (driveFile.lastModified <= 0L) "" else {
            DriveDateFormatter.get()?.format(Date(driveFile.lastModified)).orEmpty()
        }
    }
    val actionHint = when {
        isSavedLocally -> "Saved in Yomi"
        driveFile.type == DocumentType.PDF -> "Tap to download & open"
        else -> "Tap to convert to PDF"
    }
    val metaText = buildList {
        add(formatDriveFileSize(driveFile.sizeBytes))
        if (shortDate.isNotBlank()) add(shortDate)
        add(actionHint)
    }.joinToString(" • ")

    Card(
        onClick = onOpenAsLocalCopy,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(badgeBg),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = shortLabel,
                    color = badgeFg,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = driveFile.name,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(3.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    if (isSavedLocally) {
                        Icon(
                            imageVector = Icons.Default.CheckCircle,
                            contentDescription = "Saved in Yomi",
                            tint = yomiDriveGreenAccentColor(),
                            modifier = Modifier.size(13.dp)
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                    Text(
                        text = metaText,
                        style = MaterialTheme.typography.bodySmall,
                        color = if (isSavedLocally) yomiDriveGreenAccentColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box {
                IconButton(
                    onClick = { showMenu = true },
                    modifier = Modifier.size(34.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Drive file options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                        modifier = Modifier.size(19.dp)
                    )
                }

                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                when {
                                    isSavedLocally -> "Open Yomi Copy"
                                    driveFile.type == DocumentType.PDF -> "Download & Open in Yomi"
                                    else -> "Convert & Open in Yomi"
                                }
                            )
                        },
                        leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                        onClick = {
                            showMenu = false
                            onOpenAsLocalCopy()
                        }
                    )

                    if (isSavedLocally) {
                        DropdownMenuItem(
                            text = { Text("Save Fresh Copy to Yomi") },
                            leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onSaveFreshCopy()
                            }
                        )
                    }

                    if (onShareLocalCopy != null) {
                        DropdownMenuItem(
                            text = { Text("Share") },
                            leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onShareLocalCopy()
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun DriveFileGridCard(
    driveFile: DriveFileItem,
    localCopyUri: String?,
    columnCount: Int,
    onOpenAsLocalCopy: () -> Unit,
    onSaveFreshCopy: () -> Unit,
    onShareLocalCopy: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val isUltraCompact = columnCount >= 4
    val isCompact = columnCount == 3
    val isSavedLocally = !localCopyUri.isNullOrBlank()
    val (badgeBg, badgeFg, shortLabel) = when (driveFile.type) {
        DocumentType.PDF -> Triple(PdfBadgeContainer, PdfBadgeContent, "PDF")
        DocumentType.WORD -> Triple(WordBadgeContainer, WordBadgeContent, "W")
        DocumentType.EXCEL -> Triple(ExcelBadgeContainer, ExcelBadgeContent, "X")
        DocumentType.POWERPOINT -> Triple(PptBadgeContainer, PptBadgeContent, "P")
        DocumentType.UNKNOWN -> Triple(
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
            "F"
        )
    }

    Card(
        onClick = onOpenAsLocalCopy,
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(if (isUltraCompact) 7.dp else if (isCompact) 9.dp else 11.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(if (isUltraCompact) 22.dp else 26.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = shortLabel,
                        color = badgeFg,
                        fontWeight = FontWeight.Bold,
                        fontSize = if (isUltraCompact) 9.sp else 10.sp
                    )
                }

                Spacer(modifier = Modifier.width(6.dp))

                if (!isUltraCompact) {
                    Text(
                        text = driveFile.name,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                Box {
                    IconButton(
                        onClick = { showMenu = true },
                        modifier = Modifier.size(if (isUltraCompact) 22.dp else 26.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Drive file options",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(if (isUltraCompact) 14.dp else 16.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showMenu,
                        onDismissRequest = { showMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(if (isSavedLocally) "Open Yomi Copy" else "Save & Open in Yomi") },
                            leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                            onClick = {
                                showMenu = false
                                onOpenAsLocalCopy()
                            }
                        )
                        if (isSavedLocally) {
                            DropdownMenuItem(
                                text = { Text("Save Fresh Copy") },
                                leadingIcon = { Icon(Icons.Default.PictureAsPdf, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onSaveFreshCopy()
                                }
                            )
                        }
                        if (onShareLocalCopy != null) {
                            DropdownMenuItem(
                                text = { Text("Share") },
                                leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                onClick = {
                                    showMenu = false
                                    onShareLocalCopy()
                                }
                            )
                        }
                    }
                }
            }

            if (isUltraCompact) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = driveFile.name,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 10.5.sp,
                    lineHeight = 13.sp
                )
            }

            Spacer(modifier = Modifier.height(if (isUltraCompact) 6.dp else 8.dp))

            if (localCopyUri != null) {
                PdfMiniaturePageTile(
                    uriString = localCopyUri,
                    lastModified = driveFile.lastModified,
                    isPdf = true,
                    contentDescription = driveFile.name,
                    fallbackLabel = shortLabel,
                    fallbackBadgeBg = badgeBg,
                    fallbackBadgeFg = badgeFg,
                    cornerRadius = 8.dp,
                    shadowElevation = 1.5.dp,
                    badgeFontSize = if (isUltraCompact) 9.sp else 11.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(if (isUltraCompact) 0.84f else if (isCompact) 0.88f else 0.98f)
                )
            } else {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = badgeBg.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(if (isUltraCompact) 0.84f else if (isCompact) 0.88f else 0.98f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.FileDownload,
                            contentDescription = null,
                            tint = badgeFg,
                            modifier = Modifier.size(if (isUltraCompact) 20.dp else 26.dp)
                        )
                        if (!isUltraCompact) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = if (driveFile.type == DocumentType.PDF) "Tap to save" else "Convert PDF",
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Medium,
                                color = badgeFg
                            )
                        }
                    }
                }
            }

            if (!isUltraCompact) {
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = if (isSavedLocally) Icons.Default.CheckCircle else Icons.Default.Lock,
                        contentDescription = null,
                        tint = if (isSavedLocally) yomiDriveGreenAccentColor() else yomiDriveAccentColor(),
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = if (isSavedLocally) "In Yomi" else formatDriveFileSize(driveFile.sizeBytes),
                        fontSize = 10.5.sp,
                        color = if (isSavedLocally) yomiDriveGreenAccentColor() else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }
}

@Composable
internal fun AddToDriveFolderDialog(
    folderDisplayName: String,
    isLinkMode: Boolean = false,
    onOpenDriveLink: () -> Unit = {},
    onUploadPdfFromYomi: () -> Unit,
    onUploadPdfFromDevice: () -> Unit,
    onCreateSubfolder: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isLinkMode) "Drive Portal Actions" else "Add to \"$folderDisplayName\"",
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = if (isLinkMode) {
                        "Shared folder links open read-only in Yomi. Paste another folder link anytime — existing Drive files stay protected."
                    } else {
                        "Only adding new folders or uploading PDF files is permitted. Existing Drive files remain protected."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Surface(
                    onClick = {
                        onDismiss()
                        onOpenDriveLink()
                    },
                    shape = RoundedCornerShape(12.dp),
                    color = yomiDriveSurfaceColor(),
                    border = BorderStroke(1.dp, yomiDriveBorderColor())
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AddLink, contentDescription = null, tint = yomiDriveAccentColor())
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text("Paste Shared Drive Folder Link", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = yomiDriveOnSurfaceColor())
                            Text("Open any shared Google Drive folder link in Yomi", fontSize = 12.sp, color = yomiDriveSubtleTextColor())
                        }
                    }
                }

                if (!isLinkMode) {
                    Surface(
                        onClick = {
                            onDismiss()
                            onUploadPdfFromYomi()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CloudUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Upload PDF from Yomi Library", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Pick an existing PDF in your Yomi app", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Surface(
                        onClick = {
                            onDismiss()
                            onUploadPdfFromDevice()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Upload PDF from Device", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Choose a .pdf file from device storage", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }

                    Surface(
                        onClick = {
                            onDismiss()
                            onCreateSubfolder()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Default.CreateNewFolder, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text("Create New Subfolder", fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                                Text("Add a new folder inside \"$folderDisplayName\"", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
internal fun CreateDriveSubfolderDialog(
    parentFolderName: String,
    onConfirmCreate: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var folderName by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New Drive Subfolder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Create a subfolder inside \"$parentFolderName\":",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = folderName,
                    onValueChange = { folderName = it },
                    label = { Text("Folder name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val trimmed = folderName.trim()
                    if (trimmed.isNotEmpty()) {
                        onConfirmCreate(trimmed)
                        onDismiss()
                    }
                },
                enabled = folderName.trim().isNotEmpty()
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
internal fun UploadYomiPdfToDriveDialog(
    targetFolderName: String,
    localPdfs: List<DocumentItem>,
    onPickFromDevice: () -> Unit,
    onSelectPdf: (DocumentItem) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Upload PDF to \"$targetFolderName\"") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = {
                        onDismiss()
                        onPickFromDevice()
                    },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pick PDF from Device Storage")
                }

                HorizontalDivider()

                if (localPdfs.isEmpty()) {
                    Text(
                        text = "No local PDFs found in your Yomi library yet.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "Or select from your Yomi Library (${localPdfs.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 260.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(localPdfs, key = { it.id }) { doc ->
                            Surface(
                                onClick = {
                                    onDismiss()
                                    onSelectPdf(doc)
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.PictureAsPdf,
                                        contentDescription = null,
                                        tint = PdfBadgeContent,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(10.dp))
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
                                    Icon(
                                        imageVector = Icons.Default.Add,
                                        contentDescription = "Upload",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
internal fun PasteDriveFolderLinkDialog(
    recentLinks: List<SavedDriveLink>,
    initialLink: String = "",
    isLoading: Boolean = false,
    errorMessage: String? = null,
    restrictedFallbackUrl: String? = null,
    onSubmitLink: (String) -> Unit,
    onSelectSavedLink: (SavedDriveLink) -> Unit,
    onRemoveSavedLink: (String) -> Unit,
    onOpenInDriveApp: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var linkText by remember(initialLink) { mutableStateOf(initialLink) }
    val clipboard = LocalClipboardManager.current

    AlertDialog(
        onDismissRequest = { if (!isLoading) onDismiss() },
        icon = {
            Icon(
                imageVector = Icons.Default.AddLink,
                contentDescription = null,
                tint = Color(0xFF1A73E8)
            )
        },
        title = { Text("Open Drive Folder Link", fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "Paste a shared Google Drive folder URL to browse subfolders and download/convert files into Yomi without altering Drive.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                OutlinedTextField(
                    value = linkText,
                    onValueChange = { linkText = it },
                    enabled = !isLoading,
                    label = { Text("Google Drive folder link") },
                    placeholder = { Text("https://drive.google.com/drive/folders/...") },
                    singleLine = true,
                    trailingIcon = {
                        IconButton(
                            onClick = {
                                clipboard.getText()?.text?.trim()?.takeIf { it.isNotEmpty() }?.let {
                                    linkText = it
                                }
                            },
                            enabled = !isLoading
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = "Paste from clipboard",
                                tint = Color(0xFF1A73E8)
                            )
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                if (isLoading) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(vertical = 4.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        Text(
                            text = "Loading shared Google Drive folder...",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (!errorMessage.isNullOrBlank()) {
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.6f)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = errorMessage,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onErrorContainer
                            )
                            if (!restrictedFallbackUrl.isNullOrBlank()) {
                                OutlinedButton(
                                    onClick = { onOpenInDriveApp(restrictedFallbackUrl) },
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(15.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Open in Drive App", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }

                if (recentLinks.isNotEmpty()) {
                    HorizontalDivider()
                    Text(
                        text = "Saved Folder Links",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(recentLinks, key = { it.folderId }) { saved ->
                            Surface(
                                onClick = { if (!isLoading) onSelectSavedLink(saved) },
                                shape = RoundedCornerShape(10.dp),
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 7.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Link,
                                        contentDescription = null,
                                        tint = Color(0xFF1A73E8),
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = saved.name,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = saved.url,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    IconButton(
                                        onClick = { onRemoveSavedLink(saved.folderId) },
                                        enabled = !isLoading,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.DeleteOutline,
                                            contentDescription = "Remove saved link",
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
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
            Button(
                onClick = { onSubmitLink(linkText.trim()) },
                enabled = !isLoading && linkText.trim().isNotEmpty()
            ) {
                Text("Open Folder")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isLoading) {
                Text("Cancel")
            }
        }
    )
}

@Composable
internal fun UnlinkDriveFolderDialog(
    folderName: String,
    onConfirmUnlink: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Default.LinkOff,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
        },
        title = { Text("Unlink Drive Folder?", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                text = "\"$folderName\" will be unlinked from Yomi. Your original Google Drive folder and any local copies already saved in Yomi will not be deleted.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirmUnlink,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.error,
                    contentColor = Color.White
                )
            ) {
                Text("Unlink")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
