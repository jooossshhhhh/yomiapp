package com.yomi.reader.ui.dashboard

import com.yomi.reader.domain.model.DocumentCategory

/**
 * Access-control rules for the Google Drive Workspace feature.
 *
 * When a user is not signed in, Google Drive folder browsing/syncing is disabled
 * while all local device document features remain fully accessible.
 */
object DriveAccessPolicy {

    fun canUseDriveWorkspace(isSignedIn: Boolean): Boolean = isSignedIn

    fun shouldAutoSyncDrive(
        isSignedIn: Boolean,
        driveAutoSyncEnabled: Boolean,
        connectedFolderUri: String?
    ): Boolean = isSignedIn && driveAutoSyncEnabled && !connectedFolderUri.isNullOrBlank()

    fun shouldExitDriveCategory(
        isSignedIn: Boolean,
        selectedCategory: DocumentCategory
    ): Boolean = !isSignedIn && selectedCategory == DocumentCategory.GOOGLE_DRIVE
}

