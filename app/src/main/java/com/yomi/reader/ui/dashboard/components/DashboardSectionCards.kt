package com.yomi.reader.ui.dashboard.components

import android.Manifest
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.PictureAsPdf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Star
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import com.yomi.reader.ui.theme.DocsAiPillMint
import com.yomi.reader.ui.theme.DocsFabLime
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import android.provider.OpenableColumns
import com.yomi.reader.domain.model.DocumentType
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextOverflow
import com.yomi.reader.ui.components.YomiExpressiveLoadingIndicator
import com.yomi.reader.ui.dashboard.components.CustomFoldersSection
import com.yomi.reader.ui.dashboard.components.FolderGridCard
import com.yomi.reader.ui.dashboard.components.ImagesToPdfDialog
import com.yomi.reader.ui.dashboard.components.SplitPdfDialog
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yomi.reader.auth.GoogleUser
import com.yomi.reader.domain.model.DocumentCategory
import com.yomi.reader.domain.model.DocumentItem
import com.yomi.reader.ui.dashboard.components.CenterFabBottomBar
import com.yomi.reader.ui.dashboard.components.DashboardTopBar
import com.yomi.reader.ui.dashboard.components.DocumentCard
import com.yomi.reader.ui.dashboard.components.DocumentGridCard
import com.yomi.reader.ui.dashboard.components.FolderCard
import com.yomi.reader.ui.dashboard.components.FolderDetailTopBar
import com.yomi.reader.ui.dashboard.components.HomeUtilityBar
import com.yomi.reader.ui.dashboard.components.RecentDocumentsSection
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.OutlinedButton

import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items as lazyRowItems
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.HorizontalDivider
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.ui.graphics.Brush
import com.yomi.reader.ui.theme.YomiHeroEndDark
import com.yomi.reader.ui.theme.YomiHeroEndLight
import com.yomi.reader.ui.theme.YomiHeroStartDark
import com.yomi.reader.ui.theme.YomiHeroStartLight
import com.yomi.reader.ui.theme.isYomiDarkTheme
import com.yomi.reader.ui.theme.yomiDriveAccentColor
import com.yomi.reader.ui.theme.yomiDriveBorderColor
import com.yomi.reader.ui.theme.yomiDriveButtonContainerColor
import com.yomi.reader.ui.theme.yomiDriveSurfaceColor
import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.documentfile.provider.DocumentFile
import com.yomi.reader.data.local.PreferencesManager
import java.io.File
import java.io.FileOutputStream

import com.yomi.reader.ui.dashboard.components.SelectionTopBar
import com.yomi.reader.ui.dashboard.components.YomiQuickActionsBottomSheet
import com.yomi.reader.util.DocumentConverter
import com.yomi.reader.util.PdfToolsHelper
import com.yomi.reader.util.ShareUtils

@Composable
internal fun EmptyFolderDetailState(
    folderName: String,
    searchQuery: String,
    isFavoritesOnly: Boolean,
    isDriveFolder: Boolean,
    isDriveConnected: Boolean = true,
    isDriveLinkMode: Boolean = false,
    onClearSearch: () -> Unit,
    onClearFavorites: () -> Unit,
    onAddExistingPdfs: () -> Unit,
    onImportNewPdf: () -> Unit,
    onSyncDrive: () -> Unit,
    onConnectDriveFolder: () -> Unit = {},
    onOpenDriveApp: () -> Unit = {},
    onUploadPdfToDrive: () -> Unit = {},
    onCreateDriveSubfolder: () -> Unit = {},
    onPasteDriveLink: () -> Unit = {},
    onUnlinkDriveFolder: () -> Unit = {},
    folderIcon: ImageVector? = null,
    folderAccentColor: Color? = null,
    folderContainerColor: Color? = null,
    modifier: Modifier = Modifier
) {
    val activeAccent = folderAccentColor ?: MaterialTheme.colorScheme.primary
    val activeContainer = folderContainerColor ?: MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
    val activeIcon = folderIcon ?: Icons.Default.FolderOpen

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(activeContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = activeIcon,
                    contentDescription = null,
                    tint = activeAccent,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            val headline = when {
                searchQuery.isNotEmpty() -> "No Matching Files"
                isFavoritesOnly -> "No Favorite Files Here"
                isDriveFolder && !isDriveConnected -> "No Drive Folder Connected"
                else -> "\"$folderName\" is empty"
            }
            val bodyText = when {
                searchQuery.isNotEmpty() -> "No files in \"$folderName\" match \"$searchQuery\"."
                isFavoritesOnly -> "Favorite files inside \"$folderName\" will appear here."
                isDriveFolder && !isDriveConnected -> "Connect a Google Drive folder to view subfolders and supported files safely. Tapping any file downloads a local copy into Yomi without altering Drive."
                isDriveFolder -> "This Drive folder is empty. You can upload a PDF, create a subfolder, or refresh — existing Drive items stay protected."
                else -> "Add existing PDFs from your library or import a new document into \"$folderName\"."
            }

            Text(
                text = headline,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = bodyText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(20.dp))

            when {
                searchQuery.isNotEmpty() -> {
                    Button(onClick = onClearSearch, shape = RoundedCornerShape(20.dp)) {
                        Icon(Icons.Default.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clear Search")
                    }
                }
                isFavoritesOnly -> {
                    Button(onClick = onClearFavorites, shape = RoundedCornerShape(20.dp)) {
                        Text("Show All Files in Folder")
                    }
                }
                isDriveFolder -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (isDriveConnected && !isDriveLinkMode) {
                            Button(
                                onClick = onUploadPdfToDrive,
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Upload PDF to Folder")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onCreateDriveSubfolder, shape = RoundedCornerShape(20.dp)) {
                                    Icon(Icons.Default.CreateNewFolder, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("New Folder")
                                }
                                OutlinedButton(onClick = onSyncDrive, shape = RoundedCornerShape(20.dp)) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Refresh")
                                }
                            }
                            TextButton(onClick = onPasteDriveLink) {
                                Icon(Icons.Default.AddLink, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Or Open Shared Folder Link")
                            }
                        } else if (isDriveConnected && isDriveLinkMode) {
                            Button(
                                onClick = onPasteDriveLink,
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Default.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Open Another Folder Link")
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = onSyncDrive, shape = RoundedCornerShape(20.dp)) {
                                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Refresh")
                                }
                                OutlinedButton(onClick = onOpenDriveApp, shape = RoundedCornerShape(20.dp)) {
                                    Text("Open Drive App")
                                }
                            }
                        } else {
                            Button(
                                onClick = onPasteDriveLink,
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Default.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Paste Drive Folder Link")
                            }
                            OutlinedButton(
                                onClick = onConnectDriveFolder,
                                shape = RoundedCornerShape(20.dp)
                            ) {
                                Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Connect Drive Folder")
                            }
                            TextButton(onClick = onOpenDriveApp) {
                                Text("Open Drive App")
                            }
                        }
                        if (isDriveConnected) {
                            TextButton(
                                onClick = onUnlinkDriveFolder,
                                colors = ButtonDefaults.textButtonColors(
                                    contentColor = MaterialTheme.colorScheme.error
                                )
                            ) {
                                Icon(Icons.Default.LinkOff, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Unlink Drive Folder")
                            }
                        }
                    }
                }
                else -> {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = onAddExistingPdfs,
                            shape = RoundedCornerShape(20.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = activeAccent,
                                contentColor = if (isYomiDarkTheme()) Color(0xFF171A22) else Color.White
                            )
                        ) {
                            Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Add PDFs from Library")
                        }
                        OutlinedButton(onClick = onImportNewPdf, shape = RoundedCornerShape(20.dp)) {
                            Text("Import New File")
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun EmptyDocumentsState(
    message: String,
    showImportButton: Boolean = true,
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(36.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Empty View",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )

            if (showImportButton) {
                Spacer(modifier = Modifier.height(20.dp))
                Button(
                    onClick = onImportClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(text = "Add Document")
                }
            }
        }
    }
}

@Composable
internal fun DriveWorkspaceCard(
    connectedFolderUri: String?,
    connectedFolderName: String?,
    isSyncing: Boolean,
    isSignedIn: Boolean = true,
    onOpenDriveFolder: () -> Unit,
    onConnectDriveFolder: () -> Unit,
    onSyncNow: () -> Unit,
    onOpenDriveApp: () -> Unit,
    onPasteDriveLink: () -> Unit = {},
    onUnlinkDriveFolder: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isDark = isYomiDarkTheme()
    val driveAccent = yomiDriveAccentColor()
    val driveButtonBg = yomiDriveButtonContainerColor()
    val driveCardContainer = when {
        isSignedIn && connectedFolderUri != null -> yomiDriveSurfaceColor()
        isDark -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    }
    val driveCardBorder = when {
        isSignedIn && connectedFolderUri != null -> BorderStroke(1.dp, yomiDriveBorderColor())
        isDark -> BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f))
        else -> null
    }

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = driveCardContainer),
        border = driveCardBorder,
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val badgeBg = if (isSignedIn) {
                    driveAccent.copy(alpha = if (isDark) 0.18f else 0.14f)
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.12f)
                }
                val badgeTint = if (isSignedIn) {
                    driveAccent
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                }
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(badgeBg),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isSignedIn) Icons.Default.CloudDone else Icons.Default.CloudOff,
                        contentDescription = null,
                        tint = badgeTint,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Google Drive Workspace",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = when {
                            !isSignedIn -> "Sign in required to connect & use Google Drive"
                            connectedFolderUri != null -> "${connectedFolderName ?: "Connected Drive Folder"} (Read-safe)"
                            else -> "Browse Drive safely & download local Yomi copies"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                IconButton(onClick = onPasteDriveLink) {
                    Icon(
                        imageVector = Icons.Default.AddLink,
                        contentDescription = "Paste Drive folder link",
                        tint = if (isSignedIn) driveAccent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (isSignedIn && connectedFolderUri != null) {
                    IconButton(onClick = onUnlinkDriveFolder) {
                        Icon(
                            imageVector = Icons.Default.LinkOff,
                            contentDescription = "Unlink Drive folder",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isSignedIn) {
                    OutlinedButton(
                        onClick = onOpenDriveFolder,
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.CloudOff, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Use Google Drive (Sign In Required)")
                    }
                } else if (connectedFolderUri != null) {
                    Button(
                        onClick = onOpenDriveFolder,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = driveButtonBg,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Drive Folder")
                    }
                    OutlinedButton(
                        onClick = onSyncNow,
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, yomiDriveBorderColor()),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = driveAccent
                        ),
                        enabled = !isSyncing
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp, color = driveAccent)
                        } else {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Sync")
                        }
                    }
                } else {
                    Button(
                        onClick = onConnectDriveFolder,
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = driveButtonBg,
                            contentColor = Color.White
                        ),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.FolderShared, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Connect Drive Folder")
                    }
                    OutlinedButton(
                        onClick = onOpenDriveApp,
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Open Drive App")
                    }
                }
            }
        }
    }
}

@Composable
internal fun CategoryViewHeader(
    category: DocumentCategory,
    documentCount: Int,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                onClick = onBack,
                shape = RoundedCornerShape(50),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.72f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "Back to All Files",
                        tint = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.size(15.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "All Files",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .size(18.dp)
            )

            Text(
                text = category.label,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        ) {
            Text(
                text = "$documentCount ${if (documentCount == 1) "file" else "files"}",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
            )
        }
    }
}

@Composable
internal fun ContinueReadingHeroCard(
    document: DocumentItem,
    lastPageZeroBased: Int,
    totalPages: Int?,
    onResumeClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val isLightTheme = !isYomiDarkTheme()
    val gradientStart = if (isLightTheme) YomiHeroStartLight else YomiHeroStartDark
    val gradientEnd = if (isLightTheme) YomiHeroEndLight else YomiHeroEndDark

    val safeTotal = totalPages?.takeIf { it > 0 }
    val currentPage = if (safeTotal != null) {
        (lastPageZeroBased + 1).coerceIn(1, safeTotal)
    } else {
        (lastPageZeroBased + 1).coerceAtLeast(1)
    }
    val progress = if (safeTotal != null) {
        (currentPage.toFloat() / safeTotal.toFloat()).coerceIn(0.06f, 1f)
    } else {
        0.3f
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(
                brush = Brush.linearGradient(
                    colors = listOf(gradientStart, gradientEnd)
                )
            )
            .border(
                width = 1.dp,
                color = if (isLightTheme) {
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)
                } else {
                    MaterialTheme.colorScheme.primary.copy(alpha = 0.28f)
                },
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onResumeClick)
            .padding(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Crisp mini document thumbnail preview
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isLightTheme) Color.White else MaterialTheme.colorScheme.surfaceContainerHigh,
                shadowElevation = 2.dp,
                border = if (isLightTheme) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                modifier = Modifier
                    .width(58.dp)
                    .height(74.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.7f)
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f))
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.primaryContainer)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.92f)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.secondaryContainer)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.84f)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant)
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.6f)
                            .height(2.5.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "CONTINUE READING",
                    style = MaterialTheme.typography.labelSmall,
                    letterSpacing = 0.8.sp,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.height(8.dp))

                // Progress Track
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(
                            if (isLightTheme) Color.White.copy(alpha = 0.65f)
                            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.14f)
                        )
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(progress)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(50))
                            .background(MaterialTheme.colorScheme.secondary)
                    )
                }

                Spacer(modifier = Modifier.height(10.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = if (safeTotal != null) "Page $currentPage of $safeTotal" else "Page $currentPage",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable(onClick = onResumeClick)
                    ) {
                        Text(
                            text = "Resume",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 7.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun HomeEmptyLibraryCard(
    onImportClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = androidx.compose.foundation.BorderStroke(
            width = 1.dp,
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "No documents found yet. Tap + to import a PDF or convert an Office file.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(12.dp))
            TextButton(onClick = onImportClick) {
                Text(
                    text = "Import New File",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
internal fun YomiBottomNavBar(
    selectedTab: Int,
    onSelectTab: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        Triple(0, "Home", Icons.Default.Home),
        Triple(1, "Folders", Icons.Default.Folder),
        Triple(2, "Favorites", Icons.Default.Favorite),
        Triple(3, "History", Icons.Default.History)
    )
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        shadowElevation = 0.dp
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            HorizontalDivider(
                thickness = 0.75.dp,
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f)
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .height(66.dp)
                    .padding(horizontal = 6.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEach { (index, label, icon) ->
                    val isSelected = selectedTab == index
                    val interactionSource = remember { MutableInteractionSource() }
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable(
                                interactionSource = interactionSource,
                                indication = null
                            ) { onSelectTab(index) }
                            .padding(vertical = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(
                                    if (isSelected) MaterialTheme.colorScheme.primaryContainer
                                    else Color.Transparent
                                )
                                .padding(horizontal = 16.dp, vertical = 4.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = icon,
                                contentDescription = label,
                                tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(21.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}
