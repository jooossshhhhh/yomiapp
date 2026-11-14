package com.yomi.reader.ui.dashboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yomi.reader.ui.theme.isYomiDarkTheme
import com.yomi.reader.ui.theme.yomiDriveAccentColor
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.yomi.reader.data.local.PreferencesManager
import com.yomi.reader.domain.model.DocumentCategory
import com.yomi.reader.domain.model.DocumentItem
import com.yomi.reader.ui.components.YomiLogo
import com.yomi.reader.util.DocumentConverter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun YomiQuickActionsBottomSheet(
    onDismiss: () -> Unit,
    allDocuments: List<DocumentItem>,
    onFilterStarred: () -> Unit,
    onCategorySelected: (DocumentCategory) -> Unit,
    onDocumentCreatedOrConverted: (DocumentItem) -> Unit = {},
    customFolders: Set<String> = emptySet(),
    selectedFolder: String? = null,
    onSelectFolder: (String?) -> Unit = {},
    onCreateFolder: (String) -> Unit = {},
    onDeleteFolder: (String) -> Unit = {},
    onOpenDownloads: () -> Unit = {},
    onTriggerImport: () -> Unit = {},
    onTriggerImagesToPdf: () -> Unit = {},
    onTriggerSplitPdf: () -> Unit = {},
    onTriggerScan: () -> Unit = {},
    isStarredActive: Boolean = false,
    isDownloadsActive: Boolean = false,
    isDriveActive: Boolean = false,
    isSignedIn: Boolean = true,
    onNavigateHome: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    val starredCount = remember(allDocuments) { allDocuments.count { it.isFavorite } }
    var convertedFilesCount by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        val count = withContext(Dispatchers.IO) {
            DocumentConverter.getAllConvertedFiles(context).size
        }
        convertedFilesCount = count
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState())
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                YomiLogo(size = 42.dp, shapeRadius = 12.dp)
                Spacer(modifier = Modifier.width(12.dp))
                Column {
                    Text(
                        text = "Yomi Quick Suite",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "PDF Workspace & Quick Tools",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Section 1: Quick Navigation Actions
            Text(
                text = "QUICK NAVIGATION",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            val isDark = isYomiDarkTheme()
            val convertedTint = if (isDark) Color(0xFFD8B4FE) else Color(0xFF8E24AA)
            val driveTint = yomiDriveAccentColor()
            val importPdfTint = if (isDark) Color(0xFFF87171) else Color(0xFFE53935)
            val imagesToPdfTint = if (isDark) Color(0xFFA78BFA) else Color(0xFF5E35B1)
            val splitPdfTint = if (isDark) Color(0xFF34D399) else Color(0xFF00897B)

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                QuickActionCard(
                    title = "Favorites",
                    subtitle = "$starredCount ${if (starredCount == 1) "item" else "items"}",
                    icon = Icons.Default.Favorite,
                    tint = MaterialTheme.colorScheme.primary,
                    isActive = isStarredActive,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = {
                        onDismiss()
                        if (isStarredActive) {
                            onNavigateHome()
                        } else {
                            onFilterStarred()
                        }
                    }
                )

                QuickActionCard(
                    title = "Converted Files",
                    subtitle = "$convertedFilesCount ${if (convertedFilesCount == 1) "file" else "files"}",
                    icon = Icons.Default.FileDownload,
                    tint = convertedTint,
                    isActive = isDownloadsActive,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight(),
                    onClick = {
                        onDismiss()
                        if (isDownloadsActive) {
                            onNavigateHome()
                        } else {
                            onOpenDownloads()
                        }
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            QuickActionCard(
                title = "Google Drive Workspace",
                subtitle = if (isSignedIn) "Folders, files & cloud sync" else "Sign in required to use Google Drive",
                icon = if (isSignedIn) Icons.Default.CloudDone else Icons.Default.CloudOff,
                tint = if (isSignedIn) driveTint else MaterialTheme.colorScheme.onSurfaceVariant,
                isActive = isDriveActive && isSignedIn,
                modifier = Modifier.fillMaxWidth(),
                onClick = {
                    onDismiss()
                    if (isDriveActive && isSignedIn) {
                        onNavigateHome()
                    } else {
                        onCategorySelected(DocumentCategory.GOOGLE_DRIVE)
                    }
                }
            )

            Spacer(modifier = Modifier.height(18.dp))

            // Section 2: PDF Management & Tools
            Text(
                text = "PDF TOOLS & ACTIONS",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.height(8.dp))

            ConversionActionTile(
                title = "Import & Convert Document",
                description = "Import PDF, Word, Excel, or PPT (auto-converts to PDF)",
                icon = Icons.Default.PictureAsPdf,
                tint = importPdfTint,
                onClick = {
                    onDismiss()
                    onTriggerImport()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ConversionActionTile(
                title = "Images to PDF",
                description = "Combine gallery images into a multi-page PDF",
                icon = Icons.Default.AddPhotoAlternate,
                tint = imagesToPdfTint,
                onClick = {
                    onDismiss()
                    onTriggerImagesToPdf()
                }
            )

            Spacer(modifier = Modifier.height(8.dp))

            ConversionActionTile(
                title = "Split Pages",
                description = "Extract specific pages or a range into a new PDF",
                icon = Icons.Default.ContentCut,
                tint = splitPdfTint,
                onClick = {
                    onDismiss()
                    onTriggerSplitPdf()
                }
            )

            Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun QuickActionCard(
    title: String,
    subtitle: String,
    icon: ImageVector,
    tint: Color,
    isActive: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (isActive) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
        border = if (isActive) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
        shadowElevation = if (isActive) 0.dp else 1.dp,
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .padding(12.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(tint.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = tint,
                    modifier = Modifier.size(20.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    color = if (isActive) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun ConversionActionTile(
    title: String,
    description: String,
    icon: ImageVector,
    tint: Color,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(tint.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
