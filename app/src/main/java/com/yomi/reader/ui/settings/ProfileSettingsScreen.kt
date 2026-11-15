package com.yomi.reader.ui.settings

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.ui.layout.ContentScale
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.HelpOutline
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Brightness4
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderShared
import androidx.compose.material.icons.filled.FormatSize
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LinkOff
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import coil.compose.AsyncImage
import com.yomi.reader.auth.GoogleUser
import com.yomi.reader.ui.auth.GoogleLogoIcon
import com.yomi.reader.data.local.DownloadDestinationType
import com.yomi.reader.data.local.PreferencesManager
import com.yomi.reader.data.local.ThemeMode
import com.yomi.reader.ui.components.YOMI_BRAND_NAME
import com.yomi.reader.ui.components.YOMI_TAGLINE
import com.yomi.reader.ui.components.YomiLogo
import com.yomi.reader.ui.theme.isYomiDarkTheme
import com.yomi.reader.ui.theme.yomiDriveAccentColor
import com.yomi.reader.ui.theme.yomiDriveBorderColor
import com.yomi.reader.ui.theme.yomiDriveOnSurfaceColor
import com.yomi.reader.ui.theme.yomiDriveSurfaceColor
import com.yomi.reader.ui.theme.yomiPaperBackground
import com.yomi.reader.ui.components.AppUpdateDialog
import com.yomi.reader.util.GitHubReleaseInfo
import com.yomi.reader.util.GitHubUpdateChecker
import com.yomi.reader.util.NetworkUtils
import com.yomi.reader.util.PdfToolsHelper
import androidx.compose.material.icons.filled.SystemUpdate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileSettingsContent(
    user: GoogleUser,
    preferencesManager: PreferencesManager,
    onUploadPhoto: (Uri) -> Unit,
    onResetToGooglePhoto: () -> Unit,
    onRemovePhoto: () -> Unit,
    onUpdateDisplayName: (String) -> Unit,
    onSignOut: () -> Unit,
    onPickDriveFiles: (List<Uri>) -> Unit = {},
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    val currentTheme by preferencesManager.themeMode.collectAsState()
    val defaultGridView by preferencesManager.defaultGridView.collectAsState()
    val gridColumnCount by preferencesManager.gridColumnCount.collectAsState()
    val autoScan by preferencesManager.autoScanOnLaunch.collectAsState()
    val connectedDriveFolderName by preferencesManager.connectedDriveFolderName.collectAsState()
    val connectedDriveFolderUri by preferencesManager.connectedDriveFolderUri.collectAsState()
    val lastDriveSyncTime by preferencesManager.lastDriveSyncTime.collectAsState()
    val driveAutoSync by preferencesManager.driveAutoSyncEnabled.collectAsState()
    val downloadDestType by preferencesManager.downloadDestinationType.collectAsState()
    val downloadCustomFolderName by preferencesManager.downloadCustomFolderName.collectAsState()
    val downloadedFiles by preferencesManager.downloadedFiles.collectAsState()

    val coroutineScope = rememberCoroutineScope()
    var cacheSize by remember { mutableStateOf("...") }
    var managedFileCount by remember { mutableIntStateOf(0) }
    var managedStorageSize by remember { mutableStateOf("...") }
    var showLicensesDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showFaqDialog by remember { mutableStateOf(false) }
    var showSignOutConfirm by remember { mutableStateOf(false) }
    var showPhotoOptionsSheet by remember { mutableStateOf(false) }
    var showEditNameDialog by remember { mutableStateOf(false) }
    var showDownloadLocationDialog by remember { mutableStateOf(false) }
    var isCheckingUpdates by remember { mutableStateOf(false) }
    var latestReleaseInfo by remember { mutableStateOf<GitHubReleaseInfo?>(null) }
    var showUpdateDialog by remember { mutableStateOf(false) }

    val appVersionName = remember(context) {
        GitHubUpdateChecker.getInstalledVersionName(context)
    }

    val lastSyncedText = remember(lastDriveSyncTime) {
        if (lastDriveSyncTime > 0L) {
            val fmt = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
            "Last synced: ${fmt.format(Date(lastDriveSyncTime))}"
        } else {
            "Connected • Not synced yet"
        }
    }

    LaunchedEffect(Unit) {
        val (size, managed) = withContext(Dispatchers.IO) {
            preferencesManager.getCacheSizeMB(context) to
                preferencesManager.getManagedStorageSummary(context)
        }
        cacheSize = size
        managedFileCount = managed.first
        managedStorageSize = managed.second
        if (NetworkUtils.isOnline(context)) {
            GitHubUpdateChecker.checkForUpdates(context).onSuccess { info ->
                latestReleaseInfo = info
            }
        }
    }

    // Folder picker launcher for connecting a Drive or local folder directly from settings
    val folderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}

            val folderDoc = DocumentFile.fromTreeUri(context, treeUri)
            val folderName = folderDoc?.name ?: "Connected Drive Folder"
            preferencesManager.setConnectedDriveFolder(treeUri.toString(), folderName)
            Toast.makeText(context, "Connected Drive folder: $folderName", Toast.LENGTH_SHORT).show()
        }
    }

    val downloadFolderPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { treeUri ->
        if (treeUri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    treeUri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                )
            } catch (_: Exception) {}

            val folderDoc = DocumentFile.fromTreeUri(context, treeUri)
            val folderName = folderDoc?.name?.takeIf { it.isNotBlank() } ?: "Selected Folder"
            preferencesManager.setCustomDownloadDestination(treeUri.toString(), folderName)
            Toast.makeText(context, "Download location set to $folderName", Toast.LENGTH_SHORT).show()
        }
    }

    val driveFilePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val uris = PdfToolsHelper.extractUrisFromActivityResult(result.data)
            if (uris.isNotEmpty()) {
                onPickDriveFiles(uris)
            }
        }
    }

    fun launchGoogleDriveFilePicker() {
        try {
            driveFilePickerLauncher.launch(PdfToolsHelper.buildGoogleDriveFilePickerIntent())
        } catch (_: ActivityNotFoundException) {
            try {
                driveFilePickerLauncher.launch(PdfToolsHelper.buildDeviceDocumentPickerIntent())
            } catch (_: Exception) {
                Toast.makeText(context, "No document picker available on device", Toast.LENGTH_SHORT).show()
            }
        }
    }

    val profilePhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onUploadPhoto(uri)
        }
    }
    val launchPhotoPicker = {
        profilePhotoPicker.launch(
            PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
        )
    }

    val hasCustomPhotoOverride = user.isGoogleAccount &&
        !user.googlePhotoUrl.isNullOrBlank() &&
        user.photoUrl != user.googlePhotoUrl
    val needsGooglePhotoSync = user.isGoogleAccount && user.googlePhotoUrl.isNullOrBlank()

    Scaffold(
        containerColor = Color.Transparent,
        modifier = Modifier
            .fillMaxSize()
            .yomiPaperBackground(),
        topBar = {
            CenterAlignedTopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back to Library",
                            tint = MaterialTheme.colorScheme.onBackground
                        )
                    }
                },
                title = {
                    Text(
                        text = "Profile & Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
                )
            )
        }
    ) { innerPadding ->
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {

        // Profile Avatar (tappable with circular ripple to open photo options)
        Box(
            modifier = Modifier.size(98.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(92.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f),
                        shape = CircleShape
                    )
                    .clickable { showPhotoOptionsSheet = true },
                contentAlignment = Alignment.Center
            ) {
                if (!user.photoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = user.photoUrl,
                        contentDescription = "Profile Picture",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxSize()
                            .clip(CircleShape)
                    )
                } else {
                    Text(
                        text = (user.displayName?.firstOrNull() ?: user.email.firstOrNull() ?: 'Y').uppercase(),
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.background),
                shadowElevation = 2.dp,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .size(30.dp)
                    .clip(CircleShape)
                    .clickable { showPhotoOptionsSheet = true }
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PhotoCamera,
                        contentDescription = "Manage profile picture",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Text(
                text = user.displayName ?: "Yomi User",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = MaterialTheme.colorScheme.onBackground
            )
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(
                onClick = { showEditNameDialog = true },
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit local display name",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        Text(
            text = user.email,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        // Account Badge
        val googleBadgeBg = yomiDriveSurfaceColor()
        val googleBadgeBorder = yomiDriveBorderColor()
        val googleBadgeTextColor = yomiDriveOnSurfaceColor()
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .background(
                    if (user.isGoogleAccount) googleBadgeBg
                    else MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.65f)
                )
                .border(
                    width = 1.dp,
                    color = if (user.isGoogleAccount) googleBadgeBorder
                    else MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(horizontal = 12.dp, vertical = 6.dp)
        ) {
            if (user.isGoogleAccount) {
                GoogleLogoIcon()
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Google Account Connected",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = googleBadgeTextColor
                )
            } else {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Yomi Offline Account",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(22.dp))

        // Card 1: Google Drive Portal Management
        SettingsCard(title = "Google Drive Portal", icon = Icons.Default.CloudSync) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Connected Folder",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = connectedDriveFolderName ?: "No folder connected",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (connectedDriveFolderName != null) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (connectedDriveFolderName != null) {
                        Text(
                            text = lastSyncedText,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable {
                                    preferencesManager.clearConnectedDriveFolder()
                                    Toast.makeText(context, "Disconnected Google Drive folder", Toast.LENGTH_SHORT).show()
                                }
                                .padding(vertical = 2.dp, horizontal = 2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.LinkOff,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(13.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Disconnect",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = { folderPickerLauncher.launch(null) },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 104.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FolderShared,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (connectedDriveFolderName != null) "Change" else "Connect",
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Pick File Directly from Google Drive App
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Add File from Google Drive",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Select Drive files for ${user.email} straight into Yomi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                FilledTonalButton(
                    onClick = { launchGoogleDriveFilePicker() },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.filledTonalButtonColors(
                        containerColor = yomiDriveSurfaceColor(),
                        contentColor = yomiDriveOnSurfaceColor()
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 104.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.CloudDone,
                        contentDescription = null,
                        tint = yomiDriveAccentColor(),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pick File", style = MaterialTheme.typography.labelMedium)
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            // Auto-Sync Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto Refresh Drive Folder",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Automatically refresh read-safe Drive folder view on launch",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = driveAutoSync,
                    onCheckedChange = { preferencesManager.setDriveAutoSyncEnabled(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card 1b: Download Management
        SettingsCard(title = "Download Management", icon = Icons.Default.FileDownload) {
            val currentLocationLabel = remember(downloadDestType, downloadCustomFolderName) {
                preferencesManager.getDownloadLocationDisplayLabel()
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    Text(
                        text = "Download Location",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = currentLocationLabel,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                OutlinedButton(
                    onClick = { showDownloadLocationDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Change", style = MaterialTheme.typography.labelMedium)
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f).padding(end = 10.dp)) {
                    Text(
                        text = "Downloaded Files History",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = if (downloadedFiles.isEmpty()) {
                            "No downloaded files in Home history"
                        } else {
                            "${downloadedFiles.size} ${if (downloadedFiles.size == 1) "file" else "files"} shown in Home downloads"
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                FilledTonalButton(
                    onClick = {
                        preferencesManager.clearDownloadedFilesHistory()
                        Toast.makeText(context, "Downloaded files list cleared", Toast.LENGTH_SHORT).show()
                    },
                    enabled = downloadedFiles.isNotEmpty(),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Clear List", style = MaterialTheme.typography.labelMedium)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card 2: Appearance & Library Defaults
        SettingsCard(title = "Appearance & Theme", icon = Icons.Default.Brightness4) {
            Text(
                text = "Color Theme",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf(ThemeMode.LIGHT, ThemeMode.DARK, ThemeMode.SYSTEM).forEach { mode ->
                    ThemePreviewCard(
                        mode = mode,
                        selected = currentTheme == mode,
                        onClick = { preferencesManager.setThemeMode(mode) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            Text(
                text = "Default Library View",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                LibraryViewPreviewCard(
                    isGrid = false,
                    label = "List",
                    selected = !defaultGridView,
                    onClick = { preferencesManager.setDefaultGridView(false) },
                    modifier = Modifier.weight(1f)
                )
                LibraryViewPreviewCard(
                    isGrid = true,
                    label = "Grid",
                    selected = defaultGridView,
                    onClick = { preferencesManager.setDefaultGridView(true) },
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 16.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            Text(
                text = "Grid Columns",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Cards per row in Grid view",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.78f)
            )
            Spacer(modifier = Modifier.height(10.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf(2, 3, 4).forEach { cols ->
                    GridColumnsPreviewCard(
                        columns = cols,
                        label = "$cols Columns",
                        selected = gridColumnCount == cols,
                        onClick = { preferencesManager.setGridColumnCount(cols) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card 3: Storage & Cache Management
        SettingsCard(title = "Storage & Cache", icon = Icons.Default.Storage) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Temporary Render Cache",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Current usage: $cacheSize",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                OutlinedButton(
                    onClick = {
                        coroutineScope.launch {
                            val freed = withContext(Dispatchers.IO) {
                                preferencesManager.clearCache(context)
                            }
                            val updatedSize = withContext(Dispatchers.IO) {
                                preferencesManager.getCacheSizeMB(context)
                            }
                            cacheSize = updatedSize
                            val freedKb = freed / 1024
                            Toast.makeText(context, "Cache cleared! Freed $freedKb KB", Toast.LENGTH_SHORT).show()
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier.defaultMinSize(minWidth = 92.dp)
                ) {
                    Icon(Icons.Default.CleaningServices, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Clear", style = MaterialTheme.typography.labelMedium)
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Managed & Converted Files",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "$managedFileCount ${if (managedFileCount == 1) "file" else "files"} stored locally ($managedStorageSize)",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Icon(
                    imageVector = Icons.Default.Description,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 12.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Auto-Scan on Launch",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = "Discover new files when opening Yomi",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Switch(
                    checked = autoScan,
                    onCheckedChange = { preferencesManager.setAutoScanOnLaunch(it) }
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card 4: About Yomi, Privacy & Legal
        SettingsCard(title = "About Yomi", icon = Icons.Default.Info) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Yomi PDF Workspace",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Surface(
                    shape = RoundedCornerShape(50),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.75f)
                ) {
                    Text(
                        text = "v$appVersionName",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Yomi is a fast, private, offline-first PDF reader and document organizer built for focused reading and productivity. Import or scan PDFs, organize files into custom folders, bookmark Favorites, resume recent documents, and sync PDFs from Google Drive with zero ads or subscription fees.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Security,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = "100% Private & Local",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Offline conversions & documents stay on your device with zero tracking or telemetry.",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Developer",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Jude Lawrence L. Sinugbuhan",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }

                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f)
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = "Product & UX Advisors",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "Mark June Repunte and Jherico Maurin",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(10.dp))

            // App Updates Checker
            val hasNewUpdate = latestReleaseInfo?.isUpdateAvailable == true
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (hasNewUpdate) {
                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                } else {
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                },
                border = if (hasNewUpdate) {
                    BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
                } else {
                    null
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.SystemUpdate,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Column {
                            Text(
                                text = if (hasNewUpdate) {
                                    "Update Available: v${latestReleaseInfo?.versionName}"
                                } else {
                                    "Check for Updates"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (hasNewUpdate) {
                                    MaterialTheme.colorScheme.primary
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                            Text(
                                text = when {
                                    isCheckingUpdates -> "Checking for updates..."
                                    hasNewUpdate -> "Tap to view What's New & download update"
                                    latestReleaseInfo != null -> "You're on the latest version (v$appVersionName)"
                                    else -> "Current version: v$appVersionName"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedButton(
                        onClick = {
                            if (isCheckingUpdates) return@OutlinedButton
                            if (!NetworkUtils.isOnline(context)) {
                                Toast.makeText(
                                    context,
                                    "Connect to the internet to check for updates.",
                                    Toast.LENGTH_SHORT
                                ).show()
                                return@OutlinedButton
                            }
                            isCheckingUpdates = true
                            coroutineScope.launch {
                                val res = GitHubUpdateChecker.checkForUpdates(context)
                                isCheckingUpdates = false
                                res.onSuccess { info ->
                                    latestReleaseInfo = info
                                    showUpdateDialog = true
                                }.onFailure { err ->
                                    Toast.makeText(
                                        context,
                                        err.message ?: "Could not check for updates right now.",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                        },
                        enabled = !isCheckingUpdates,
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        if (isCheckingUpdates) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Checking", fontSize = 12.sp)
                        } else {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(modifier = Modifier.width(5.dp))
                            Text(
                                text = if (hasNewUpdate) "View Update" else "Check for Updates",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            FilledTonalButton(
                onClick = { showFaqDialog = true },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.HelpOutline,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("FAQ & How to Use Yomi", fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = { showLicensesDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Licenses", fontSize = 12.sp)
                }

                OutlinedButton(
                    onClick = { showPrivacyDialog = true },
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Privacy Policy", fontSize = 12.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Sign Out Button
        OutlinedButton(
            onClick = { showSignOutConfirm = true },
            shape = RoundedCornerShape(14.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.error
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(50.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Logout,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Sign Out / Switch Account",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold
            )
        }

        Spacer(
            modifier = Modifier
                .height(16.dp)
                .navigationBarsPadding()
        )
    }
    }

    if (showUpdateDialog) {
        val info = latestReleaseInfo
        if (info != null) {
            AppUpdateDialog(
                releaseInfo = info,
                onDismiss = { showUpdateDialog = false }
            )
        }
    }

    // Profile Photo Action Sheet
    if (showPhotoOptionsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showPhotoOptionsSheet = false },
            sheetState = sheetState,
            shape = RoundedCornerShape(topStart = 24.dp, topEnd = 24.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "Yomi Profile Photo",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Stored only inside Yomi and never alters your Google Account.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(4.dp))

                Surface(
                    onClick = {
                        showPhotoOptionsSheet = false
                        launchPhotoPicker()
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.PhotoLibrary,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Text(
                            text = "Choose Photo from Gallery",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                if (hasCustomPhotoOverride || needsGooglePhotoSync) {
                    Surface(
                        onClick = {
                            showPhotoOptionsSheet = false
                            onResetToGooglePhoto()
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = if (needsGooglePhotoSync) "Sync Google Profile Photo" else "Reset to Google Photo",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                if (!user.photoUrl.isNullOrBlank()) {
                    Surface(
                        onClick = {
                            showPhotoOptionsSheet = false
                            onRemovePhoto()
                        },
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.35f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = "Remove Photo",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }
                }
            }
        }
    }

    // Edit Local Display Name Dialog
    if (showEditNameDialog) {
        var editedName by remember(user.displayName) { mutableStateOf(user.displayName ?: "") }
        AlertDialog(
            onDismissRequest = { showEditNameDialog = false },
            title = {
                Text(
                    text = "Edit Yomi Display Name",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "This updates your display name inside Yomi only and does not affect your Google Account.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = editedName,
                        onValueChange = { editedName = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showEditNameDialog = false
                        onUpdateDisplayName(editedName)
                    },
                    enabled = editedName.trim().isNotEmpty()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditNameDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Sign Out Confirmation Dialog
    if (showSignOutConfirm) {
        AlertDialog(
            onDismissRequest = { showSignOutConfirm = false },
            title = { Text("Sign Out of Yomi?") },
            text = { Text("Are you sure you want to sign out from ${user.email}? You can sign back in at any time.") },
            confirmButton = {
                Button(
                    onClick = {
                        showSignOutConfirm = false
                        onSignOut()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sign Out")
                }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutConfirm = false }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Open Source Licenses Dialog
    if (showLicensesDialog) {
        AlertDialog(
            onDismissRequest = { showLicensesDialog = false },
            title = { Text("Open Source Licenses") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    LicenseItem(name = "LibreOfficeKit (LibreOffice Core)", license = "Mozilla Public License 2.0 (MPL-2.0 — Offline Office conversion)")
                    LicenseItem(name = "Jetpack Compose", license = "Apache License 2.0 (Google UI toolkit)")
                    LicenseItem(name = "AndroidX DocumentFile", license = "Apache License 2.0 (SAF directory tree access)")
                    LicenseItem(name = "Coil Compose", license = "Apache License 2.0 (Image loading)")
                    LicenseItem(name = "Google Identity & Credential Manager", license = "Android Open Source Project")
                }
            },
            confirmButton = {
                Button(onClick = { showLicensesDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showDownloadLocationDialog) {
        AlertDialog(
            onDismissRequest = { showDownloadLocationDialog = false },
            icon = {
                Icon(
                    imageVector = Icons.Default.FolderOpen,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = { Text("Choose Download Location") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Select where downloaded PDFs are saved when you download a file from Yomi.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Surface(
                        onClick = {
                            preferencesManager.setBuiltInDownloadDestination(DownloadDestinationType.DOCUMENTS_YOMI)
                            showDownloadLocationDialog = false
                            Toast.makeText(context, "Download location: Documents/Yomi", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (downloadDestType == DownloadDestinationType.DOCUMENTS_YOMI) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Documents / Yomi", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text("Default device documents folder", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (downloadDestType == DownloadDestinationType.DOCUMENTS_YOMI) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    Surface(
                        onClick = {
                            preferencesManager.setBuiltInDownloadDestination(DownloadDestinationType.DOWNLOADS_YOMI)
                            showDownloadLocationDialog = false
                            Toast.makeText(context, "Download location: Downloads/Yomi", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        color = if (downloadDestType == DownloadDestinationType.DOWNLOADS_YOMI) {
                            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Downloads / Yomi", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.bodyMedium)
                                Text("System Downloads folder", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                            if (downloadDestType == DownloadDestinationType.DOWNLOADS_YOMI) {
                                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }

                    OutlinedButton(
                        onClick = {
                            showDownloadLocationDialog = false
                            downloadFolderPickerLauncher.launch(null)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Folder, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            if (downloadDestType == DownloadDestinationType.CUSTOM_FOLDER && !downloadCustomFolderName.isNullOrBlank()) {
                                "Custom Folder: $downloadCustomFolderName"
                            } else {
                                "Choose Custom Folder..."
                            }
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDownloadLocationDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    // Privacy Dialog
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Privacy Policy & Terms") },
            text = {
                Text(
                    text = "Yomi is committed to your privacy. All documents are processed 100% locally on your device. We do not transmit, analyze, or collect telemetry on any file content. Google Drive connections use official Android Storage Access Framework permissions to keep your data private and secure.",
                    fontSize = 14.sp,
                    lineHeight = 20.sp
                )
            },
            confirmButton = {
                Button(onClick = { showPrivacyDialog = false }) {
                    Text("Understood")
                }
            }
        )
    }

    // Modern FAQ & How-to-Use Guide Bottom Sheet
    if (showFaqDialog) {
        val faqSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = false)
        val isFaqExpanded = faqSheetState.currentValue == SheetValue.Expanded
        val faqScrollState = rememberScrollState()
        var selectedFaqFilter by remember { mutableStateOf("All") }
        LaunchedEffect(selectedFaqFilter) {
            faqScrollState.scrollTo(0)
        }
        val faqCategories = listOf(
            "All",
            "Tools & Viewer",
            "Storage & Cache",
            "Drive & Folders",
            "Account & Privacy"
        )

        val isFaqDark = isYomiDarkTheme()
        val faqs = listOf(
            FaqGuideEntry(
                category = "Tools & Viewer",
                title = "How does Office-to-PDF conversion work?",
                body = "When importing or opening Word (.docx/.doc), Excel (.xlsx/.xls), or PowerPoint (.pptx/.ppt) files, Yomi converts them completely on your device using its bundled LibreOfficeKit Offline Engine—no internet connection, cloud upload, or usage limits required.\n• Word Fidelity: Automatically unclips overflowing text boxes, table rows, and line spacing, merges layered figures/insets cleanly, and recovers off-page diagrams so multi-page documents stay complete.\n• Excel Fit-to-Page: Automatically removes awkward manual page breaks and scales each worksheet cleanly onto 1 A4 page (portrait or landscape).\n• Converted PDFs open and save privately inside Yomi—no copy is written to shared device storage unless you tap Download.",
                icon = Icons.Default.Sync,
                tint = if (isFaqDark) Color(0xFF34D399) else Color(0xFF00897B)
            ),
            FaqGuideEntry(
                category = "Tools & Viewer",
                title = "What tools are in the Yomi Quick Suite & Converted Files?",
                body = "Tap the Yomi logo in the top-left corner of Home to open Quick Suite:\n• Starred & Converted Files shortcuts\n• Google Drive Workspace (requires sign-in)\n• Import & Convert Document (PDF, Word, Excel, PowerPoint)\n• Merge PDFs, Split Pages & Images to PDF\n• Quick Device Scan\n\nIn Converted Files, you can search, sort, switch List/Grid views, tap ⋮ or long-press cards for multiple selection (batch Download, Share, or Delete), or tap + to add more files right there.",
                icon = Icons.Default.AutoAwesome,
                tint = if (isFaqDark) Color(0xFFBBA8FF) else Color(0xFF7E57C2)
            ),
            FaqGuideEntry(
                category = "Tools & Viewer",
                title = "What viewing controls are in the PDF Reader?",
                body = "• Top Bar: Search text, Bookmarks, Recent PDFs history icon (slides out a right-hand pane to switch quickly between recently opened PDFs), and ⋮ menu (Rotate 90°, Fullscreen Reading, Bookmarks, Notes & Annotations, Export Annotated PDF, Print/Save as PDF, Share, Document Info).\n• Bottom Toolbar: Page jump pill (e.g. 1/60), Page Thumbnails grid button beside the page numbers (drag the thumbnails sheet up to expand on long PDFs), 3 layout modes (Vertical scroll, Single page, Two pages view), and zoom controls (-, Fit Page/Fit Width/50%–200% dropdown, +).",
                icon = Icons.Default.PictureAsPdf,
                tint = if (isFaqDark) Color(0xFFF87171) else Color(0xFFE53935)
            ),
            FaqGuideEntry(
                category = "Storage & Cache",
                title = "How does Yomi manage Storage vs. Temporary Render Cache?",
                body = "Yomi separates temporary render cache from both offline Drive cache and your saved documents:\n• Temporary Render Cache (app cacheDir): Stores page preview thumbnails (pdf_thumbs), page snapshots, and conversion staging files so scrolling stays fast.\n• Clearing Cache (Profile & Settings → Storage & Cache → Clear): Safely removes temporary render files and recalculates freed space. It NEVER deletes your imported PDFs, Converted Files, Offline-Ready Drive cache, Custom Folders, Favorites, Highlights, or Bookmarks—needed thumbnails simply re-render when viewed again.",
                icon = Icons.Default.CleaningServices,
                tint = if (isFaqDark) Color(0xFFFBBF24) else Color(0xFFF57C00)
            ),
            FaqGuideEntry(
                category = "Storage & Cache",
                title = "Where are Managed, Converted, Cached Drive & Downloaded files stored?",
                body = "• Inside Yomi (App-Private Documents Storage): Converted PDFs (Converted_PDFs), Imported PDFs (Imported_PDFs), merged images (Images_to_PDF), and split extracts (Split_PDFs) live privately inside Yomi until you delete them or uninstall the app, and their count/size appear under Storage & Cache → Managed & Converted Files.\n• Offline Drive Cache (app-private drive_cache): Synced Drive files stay cached locally so they show \"Offline ready\" and open or convert without internet. Unlinking the Drive folder automatically deletes temporary Drive cache to free storage while keeping all PDFs you already converted or saved.\n• Device Downloads (Exported Copies): Files are only written to shared device storage when you explicitly choose Download. Choose Documents/Yomi (Default), Downloads/Yomi, or a Custom Folder in Profile & Settings → Download Management.\n• Home Downloads Icon: Tap the Downloads icon in the Home top bar to view/open downloaded records or clear the list (clearing history only removes list entries, not exported files).",
                icon = Icons.Default.Storage,
                tint = if (isFaqDark) Color(0xFFA78BFA) else Color(0xFF5E35B1)
            ),
            FaqGuideEntry(
                category = "Drive & Folders",
                title = "How do Google Drive Workspace, Offline Sync & Folder Links work?",
                body = "Google Drive Workspace requires signing in so Drive folders, sync, and shared links stay tied to your workspace. If signed out, tapping any Drive option lets you choose Sign In or Continue Without Drive (keeping all local PDF, offline conversion, folder, and viewer tools fully usable).\n\nOnce signed in:\n• Pick File or Paste Folder Link / Connect Folder: Browse shared Drive folders in read-safe mode (Office lock/temp files like ~$* are skipped automatically), unlink anytime, or toggle Auto Refresh Drive Folder in Settings.\n• Background Sync & Hideable Progress: During sync, Yomi caches supported files for offline use and shows accurate 0%–100% progress. Tap Hide anytime to keep browsing while syncing finishes.\n• Interrupted Internet Modal: If your connection drops mid-sync, Yomi notifies you that the folder hasn't finished syncing while keeping every already-cached \"Offline ready\" file usable offline—reconnect and tap Sync later to fetch the rest.",
                icon = Icons.Default.CloudDone,
                tint = yomiDriveAccentColor()
            ),
            FaqGuideEntry(
                category = "Drive & Folders",
                title = "How do Custom Folders, Multi-Select, Favorites & History work?",
                body = "• Custom Folders: In the Folders tab, tap + New Folder, customize folder icons/colors, long-press to rename/delete, or open a folder to filter and add PDFs.\n• Batch Multi-Select: Long-press any file (or tap ⋮ → Select files) in Home, Folders, or Converted Files to move, star, share, download, or delete multiple documents at once.\n• Favorites, History & Continue Reading: Star documents for quick access in Favorites, browse History or the in-viewer Recent PDFs pane to reopen recent PDFs, and use Continue Reading on Home to resume your last page.",
                icon = Icons.Default.Folder,
                tint = if (isFaqDark) Color(0xFF34D399) else Color(0xFF00897B)
            ),
            FaqGuideEntry(
                category = "Account & Privacy",
                title = "What stays local, and how do App Updates & Profile settings work?",
                body = "• Local & Private: All local PDFs, annotations, bookmarks, custom folders, download preferences, and LibreOfficeKit Office-to-PDF conversions stay 100% on your device.\n• Accounts & Appearance: Sign in (Google or local account) only when you want Google Drive Workspace. Editing your Yomi display name or profile photo only updates your local profile. In Profile & Settings, choose Light, Dark, or System theme, List or Grid layout (2–4 columns), and toggle Auto-Scan on Launch.\n• Built-In App Updates: Yomi checks GitHub Releases on launch (or manually via Profile & Settings → About Yomi → Check for Updates). Tap Update Now to download the latest signed release APK directly—installing it updates Yomi in-place without deleting your saved PDFs, folders, or Drive links.",
                icon = Icons.Default.Security,
                tint = if (isFaqDark) Color(0xFF4ADE80) else Color(0xFF2E7D32)
            )
        )

        val visibleFaqs = remember(selectedFaqFilter) {
            if (selectedFaqFilter == "All") faqs else faqs.filter { it.category == selectedFaqFilter }
        }

        ModalBottomSheet(
            onDismissRequest = { showFaqDialog = false },
            sheetState = faqSheetState,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight()
                    .navigationBarsPadding()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    YomiLogo(size = 44.dp, shapeRadius = 14.dp)
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "FAQ & User Guide",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Master Yomi's tools, shortcuts & workflows",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    faqCategories.forEach { category ->
                        FilterChip(
                            selected = selectedFaqFilter == category,
                            onClick = { selectedFaqFilter = category },
                            label = {
                                Text(
                                    text = category,
                                    style = MaterialTheme.typography.labelMedium,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .verticalScroll(faqScrollState, enabled = isFaqExpanded),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    visibleFaqs.forEachIndexed { index, entry ->
                        androidx.compose.runtime.key(selectedFaqFilter, entry.title) {
                            FaqAccordionItem(
                                question = entry.title,
                                answer = entry.body,
                                icon = entry.icon,
                                tint = entry.tint,
                                initiallyExpanded = index == 0
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Button(
                    onClick = { showFaqDialog = false },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Text(
                        text = "Got It",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

private data class FaqGuideEntry(
    val category: String,
    val title: String,
    val body: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color
)
