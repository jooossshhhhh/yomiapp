package com.yomi.reader

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.lifecycleScope
import com.yomi.reader.auth.GoogleAuthManager
import com.yomi.reader.domain.model.DocumentItem
import com.yomi.reader.domain.model.DocumentType
import com.yomi.reader.ui.auth.AuthScreen
import com.yomi.reader.ui.dashboard.DashboardScreen
import com.yomi.reader.ui.dashboard.DashboardViewModel
import com.yomi.reader.ui.theme.YomiTheme
import com.yomi.reader.ui.viewer.DocumentViewerHost
import kotlinx.coroutines.launch

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.isSystemInDarkTheme
import com.yomi.reader.data.local.PreferencesManager
import com.yomi.reader.data.local.RecentPdfHistoryHelper
import com.yomi.reader.data.local.ThemeMode
import com.yomi.reader.ui.components.AppUpdateDialog
import com.yomi.reader.ui.components.YomiSplashScreen
import com.yomi.reader.util.GitHubReleaseInfo
import com.yomi.reader.util.GitHubUpdateChecker
import com.yomi.reader.util.NetworkUtils

class MainActivity : ComponentActivity() {

    private val appContainer by lazy {
        (application as YomiApplication).container
    }

    private val dashboardViewModel: DashboardViewModel by viewModels {
        DashboardViewModel.provideFactory(appContainer.documentRepository, appContainer.preferencesManager)
    }

    private var initialDocumentToOpen by mutableStateOf<DocumentItem?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        handleIncomingIntent(intent)

        setContent {
            val themeMode by appContainer.preferencesManager.themeMode.collectAsState()
            val isSystemDark = isSystemInDarkTheme()
            val isDarkTheme = when (themeMode) {
                ThemeMode.SYSTEM -> isSystemDark
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
            }

            YomiTheme(darkTheme = isDarkTheme) {
                YomiApp(
                    viewModel = dashboardViewModel,
                    authManager = appContainer.googleAuthManager,
                    preferencesManager = appContainer.preferencesManager,
                    initialDocument = initialDocumentToOpen,
                    onDocumentOpened = { initialDocumentToOpen = null }
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleIncomingIntent(intent)
    }

    private fun handleIncomingIntent(intent: Intent?) {
        if (intent == null) return

        val incomingUris = mutableListOf<Uri>()

        when (intent.action) {
            Intent.ACTION_SEND -> {
                val streamUri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(Intent.EXTRA_STREAM)
                }
                if (streamUri != null) {
                    incomingUris.add(streamUri)
                } else if (intent.data != null) {
                    incomingUris.add(intent.data!!)
                }
            }
            Intent.ACTION_SEND_MULTIPLE -> {
                val streamUris = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM)
                }
                streamUris?.filterNotNull()?.let { incomingUris.addAll(it) }
            }
            else -> {
                intent.data?.let { incomingUris.add(it) }
            }
        }

        if (incomingUris.isNotEmpty()) {
            lifecycleScope.launch {
                val driveUrisToTrack = mutableSetOf<String>()
                var firstImportedDoc: DocumentItem? = null

                for (uri in incomingUris) {
                    try {
                        var fileName = "External_Document"
                        var fileSize = 0L

                        contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                            val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                            val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                            if (cursor.moveToFirst()) {
                                if (nameIndex != -1) fileName = cursor.getString(nameIndex) ?: fileName
                                if (sizeIndex != -1) fileSize = cursor.getLong(sizeIndex)
                            }
                        }

                        val ext = fileName.substringAfterLast('.', "")
                        val type = DocumentType.fromExtension(ext)
                        val doc = appContainer.documentRepository.importDocument(
                            uriString = uri.toString(),
                            title = fileName,
                            type = type,
                            sizeBytes = fileSize
                        )

                        if (uri.toString().contains("com.google.android.apps.docs") ||
                            uri.toString().contains("google.drive") ||
                            intent.component?.packageName?.contains("google.android.apps.docs") == true
                        ) {
                            driveUrisToTrack.add(uri.toString())
                        }

                        if (firstImportedDoc == null) {
                            firstImportedDoc = doc
                        }
                    } catch (_: Exception) {}
                }

                if (driveUrisToTrack.isNotEmpty()) {
                    appContainer.preferencesManager.addSyncedDriveUris(driveUrisToTrack)
                }

                initialDocumentToOpen = firstImportedDoc
            }
        }
    }
}

private sealed class AppNavigationState {
    data object Splash : AppNavigationState()
    data object Dashboard : AppNavigationState()
    data object Auth : AppNavigationState()
    data class Viewer(val document: DocumentItem) : AppNavigationState()
}

@Composable
fun YomiApp(
    viewModel: DashboardViewModel,
    authManager: GoogleAuthManager,
    preferencesManager: PreferencesManager,
    initialDocument: DocumentItem?,
    onDocumentOpened: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val currentUser by authManager.currentUser.collectAsState()
    val pendingProfileConsentIntent by authManager.pendingProfileConsentIntent.collectAsState()

    val profileConsentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        val activity = context as? Activity ?: return@rememberLauncherForActivityResult
        coroutineScope.launch {
            val updated = authManager.handleProfileConsentResult(activity, result.data)
            if (updated) {
                Toast.makeText(context, "Google profile picture synced", Toast.LENGTH_SHORT).show()
            }
        }
    }

    LaunchedEffect(pendingProfileConsentIntent) {
        val pending = pendingProfileConsentIntent ?: return@LaunchedEffect
        authManager.consumePendingProfileConsentIntent()
        runCatching {
            profileConsentLauncher.launch(
                IntentSenderRequest.Builder(pending.intentSender).build()
            )
        }
    }

    LaunchedEffect(currentUser?.email, currentUser?.isGoogleAccount) {
        val user = currentUser
        if (user != null && user.isGoogleAccount && user.googlePhotoUrl.isNullOrBlank()) {
            authManager.fetchGoogleProfilePhoto(
                activity = context as? Activity,
                email = user.email,
                promptConsentIfNeeded = false,
                overwriteCurrentPhoto = false
            )
        }
    }

    val documentSaver = remember {
        Saver<DocumentItem?, ArrayList<String>>(
            save = { doc ->
                if (doc == null) {
                    arrayListOf()
                } else {
                    arrayListOf(
                        doc.id.toString(),
                        doc.uriString,
                        doc.title,
                        doc.type.name,
                        doc.sizeBytes.toString(),
                        doc.lastModified.toString(),
                        doc.isFavorite.toString(),
                        (doc.lastOpened ?: -1L).toString(),
                        doc.parentPath
                    )
                }
            },
            restore = { saved ->
                if (saved.size < 9) {
                    null
                } else {
                    val lastOpenedVal = saved[7].toLongOrNull() ?: -1L
                    DocumentItem(
                        id = saved[0].toLongOrNull() ?: 0L,
                        uriString = saved[1],
                        title = saved[2],
                        type = runCatching { DocumentType.valueOf(saved[3]) }.getOrDefault(DocumentType.PDF),
                        sizeBytes = saved[4].toLongOrNull() ?: 0L,
                        lastModified = saved[5].toLongOrNull() ?: 0L,
                        isFavorite = saved[6].toBoolean(),
                        lastOpened = if (lastOpenedVal >= 0L) lastOpenedVal else null,
                        parentPath = saved[8]
                    )
                }
            }
        )
    }

    var showSplash by rememberSaveable { mutableStateOf(initialDocument == null) }
    var activeDocument by rememberSaveable(stateSaver = documentSaver) { mutableStateOf<DocumentItem?>(null) }
    var showAuthScreen by rememberSaveable { mutableStateOf(false) }
    var isAuthLoading by remember { mutableStateOf(false) }
    var authErrorMessage by remember { mutableStateOf<String?>(null) }
    var pendingDriveImportUris by remember { mutableStateOf<List<Uri>>(emptyList()) }
    var startupUpdateInfo by remember { mutableStateOf<GitHubReleaseInfo?>(null) }
    val dashboardUiState by viewModel.uiState.collectAsState()
    val recordedRecentPdfs by preferencesManager.recentPdfs.collectAsState()
    val recentPdfDocuments = remember(recordedRecentPdfs, dashboardUiState.recentDocuments) {
        RecentPdfHistoryHelper.mergeRecentPdfDocuments(
            recordedPdfs = recordedRecentPdfs,
            dbRecentDocs = dashboardUiState.recentDocuments
        )
    }

    LaunchedEffect(Unit) {
        if (showSplash) {
            kotlinx.coroutines.delay(900L)
            showSplash = false
        }
        if (NetworkUtils.isOnline(context)) {
            GitHubUpdateChecker.checkForUpdates(context).onSuccess { info ->
                if (info.isUpdateAvailable && GitHubUpdateChecker.shouldAutoPromptForTag(context, info.tagName)) {
                    startupUpdateInfo = info
                }
            }
        }
    }

    LaunchedEffect(initialDocument) {
        if (initialDocument != null) {
            showSplash = false
            activeDocument = initialDocument
            onDocumentOpened()
        }
    }

    val navState: AppNavigationState = when {
        activeDocument != null -> AppNavigationState.Viewer(activeDocument!!)
        showSplash -> AppNavigationState.Splash
        showAuthScreen -> AppNavigationState.Auth
        else -> AppNavigationState.Dashboard
    }

    AnimatedContent(
        targetState = navState,
        contentKey = { screen -> screen::class },
        transitionSpec = {
            // Google Play Store / Material 3 Shared-Axis Z transitions (scale + fade depth on GPU)
            val enterEasing = FastOutSlowInEasing
            val exitEasing = FastOutLinearInEasing
            val isForward = targetState is AppNavigationState.Viewer ||
                targetState is AppNavigationState.Auth ||
                initialState is AppNavigationState.Splash
            val spec = if (isForward) {
                (fadeIn(animationSpec = tween(220, delayMillis = 50, easing = enterEasing)) +
                    scaleIn(initialScale = 0.96f, animationSpec = tween(250, delayMillis = 40, easing = enterEasing)))
                    .togetherWith(
                        fadeOut(animationSpec = tween(110, easing = exitEasing)) +
                            scaleOut(targetScale = 1.025f, animationSpec = tween(150, easing = exitEasing))
                    )
            } else {
                (fadeIn(animationSpec = tween(190, delayMillis = 40, easing = enterEasing)) +
                    scaleIn(initialScale = 1.025f, animationSpec = tween(210, delayMillis = 40, easing = enterEasing)))
                    .togetherWith(
                        fadeOut(animationSpec = tween(120, easing = exitEasing)) +
                            scaleOut(targetScale = 0.965f, animationSpec = tween(150, easing = exitEasing))
                    )
            }
            spec.using(SizeTransform(clip = false) { _, _ -> snap() })
        },
        label = "AppScreenTransition",
        modifier = Modifier.fillMaxSize()
    ) { screen ->
        when (screen) {
            AppNavigationState.Splash -> {
                YomiSplashScreen(modifier = Modifier.fillMaxSize())
            }
            is AppNavigationState.Viewer -> {
                BackHandler {
                    activeDocument = null
                }
                key(screen.document.id, screen.document.uriString) {
                    DocumentViewerHost(
                        document = screen.document,
                        onBack = { activeDocument = null },
                        recentPdfDocuments = recentPdfDocuments,
                        onOpenRecentPdf = { targetDoc ->
                            activeDocument = targetDoc
                            if (targetDoc.id > 0L) {
                                viewModel.recordOpened(targetDoc)
                            }
                        },
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
            AppNavigationState.Auth -> {
                BackHandler {
                    showAuthScreen = false
                }
                AuthScreen(
                    currentUser = currentUser,
                    preferencesManager = preferencesManager,
                    isLoading = isAuthLoading,
                    errorMessage = authErrorMessage,
                    onClearError = { authErrorMessage = null },
                    onLocalSignIn = { username, password, isNewAccount, name, photoUri ->
                        val result = if (isNewAccount) {
                            authManager.registerWithUsernamePassword(username, password, name)
                        } else {
                            authManager.loginWithUsernamePassword(username, password)
                        }
                        result.onSuccess { user ->
                            if (photoUri != null) {
                                coroutineScope.launch {
                                    authManager.updateProfilePhoto(photoUri)
                                }
                            }
                            Toast.makeText(context, "Welcome, ${user.displayName}!", Toast.LENGTH_SHORT).show()
                            showAuthScreen = false
                        }.onFailure { err ->
                            authErrorMessage = err.message ?: "Authentication failed"
                        }
                    },
                    onGoogleSignInClick = {
                        coroutineScope.launch {
                            isAuthLoading = true
                            authErrorMessage = null
                            val result = authManager.signInWithGoogle(
                                activityContext = context,
                                serverClientId = GoogleAuthManager.CLIENT_ID
                            )
                            isAuthLoading = false
                            result.onSuccess { user ->
                                Toast.makeText(
                                    context,
                                    "Signed in as ${user.displayName ?: user.email}",
                                    Toast.LENGTH_LONG
                                ).show()
                                showAuthScreen = false
                            }.onFailure { error ->
                                authErrorMessage = error.message ?: "Google Sign-In failed"
                            }
                        }
                    },
                    onUploadPhoto = { uri ->
                        coroutineScope.launch {
                            authManager.updateProfilePhoto(uri)
                                .onSuccess {
                                    Toast.makeText(context, "App profile picture updated", Toast.LENGTH_SHORT).show()
                                }
                                .onFailure { err ->
                                    Toast.makeText(context, err.message ?: "Could not update photo", Toast.LENGTH_SHORT).show()
                                }
                        }
                    },
                    onResetToGooglePhoto = {
                        val user = currentUser
                        if (user != null && !user.googlePhotoUrl.isNullOrBlank()) {
                            authManager.resetToGooglePhoto()
                            Toast.makeText(context, "Restored Google profile picture", Toast.LENGTH_SHORT).show()
                        } else if (user != null && user.isGoogleAccount) {
                            coroutineScope.launch {
                                val fetched = authManager.fetchGoogleProfilePhoto(
                                    activity = context as? Activity,
                                    email = user.email,
                                    promptConsentIfNeeded = true,
                                    overwriteCurrentPhoto = true
                                )
                                if (!fetched.isNullOrBlank()) {
                                    Toast.makeText(context, "Google profile picture loaded", Toast.LENGTH_SHORT).show()
                                }
                            }
                        }
                    },
                    onRemovePhoto = {
                        authManager.removeProfilePhoto()
                        Toast.makeText(context, "Removed custom Yomi profile photo", Toast.LENGTH_SHORT).show()
                    },
                    onUpdateDisplayName = { newName ->
                        authManager.updateDisplayName(newName)
                            .onSuccess {
                                Toast.makeText(context, "Updated Yomi display name", Toast.LENGTH_SHORT).show()
                            }
                            .onFailure { err ->
                                Toast.makeText(context, err.message ?: "Could not update name", Toast.LENGTH_SHORT).show()
                            }
                    },
                    onSignOut = {
                        authManager.signOut()
                        Toast.makeText(context, "Signed out from Yomi", Toast.LENGTH_SHORT).show()
                    },
                    onContinueAsGuest = {
                        showAuthScreen = false
                    },
                    onPickDriveFiles = { uris ->
                        pendingDriveImportUris = uris
                        showAuthScreen = false
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
            AppNavigationState.Dashboard -> {
                DashboardScreen(
                    viewModel = viewModel,
                    preferencesManager = preferencesManager,
                    currentUser = currentUser,
                    pendingExternalUris = pendingDriveImportUris,
                    onExternalUrisConsumed = { pendingDriveImportUris = emptyList() },
                    onOpenDocument = { doc ->
                        activeDocument = doc
                    },
                    onOpenAccount = {
                        showAuthScreen = true
                    },
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }

    val pendingUpdate = startupUpdateInfo
    if (pendingUpdate != null && !showSplash) {
        AppUpdateDialog(
            releaseInfo = pendingUpdate,
            onDismiss = {
                GitHubUpdateChecker.markUpdatePromptDismissed(context, pendingUpdate.tagName)
                startupUpdateInfo = null
            }
        )
    }
}