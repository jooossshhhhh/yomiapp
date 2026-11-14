package com.yomi.reader.ui.dashboard

import com.yomi.reader.domain.model.DocumentCategory
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DriveAccessPolicyTest {

    @Test
    fun canUseDriveWorkspace_requiresSignedInUser() {
        assertFalse(DriveAccessPolicy.canUseDriveWorkspace(isSignedIn = false))
        assertTrue(DriveAccessPolicy.canUseDriveWorkspace(isSignedIn = true))
    }

    @Test
    fun shouldAutoSyncDrive_blocksSyncWhenUnsignedOrMissingFolder() {
        assertFalse(
            DriveAccessPolicy.shouldAutoSyncDrive(
                isSignedIn = false,
                driveAutoSyncEnabled = true,
                connectedFolderUri = "content://drive/folder"
            )
        )
        assertFalse(
            DriveAccessPolicy.shouldAutoSyncDrive(
                isSignedIn = true,
                driveAutoSyncEnabled = false,
                connectedFolderUri = "content://drive/folder"
            )
        )
        assertFalse(
            DriveAccessPolicy.shouldAutoSyncDrive(
                isSignedIn = true,
                driveAutoSyncEnabled = true,
                connectedFolderUri = "   "
            )
        )
        assertTrue(
            DriveAccessPolicy.shouldAutoSyncDrive(
                isSignedIn = true,
                driveAutoSyncEnabled = true,
                connectedFolderUri = "content://drive/folder"
            )
        )
    }

    @Test
    fun shouldExitDriveCategory_returnsTrueOnlyWhenUnsignedInDriveCategory() {
        assertTrue(
            DriveAccessPolicy.shouldExitDriveCategory(
                isSignedIn = false,
                selectedCategory = DocumentCategory.GOOGLE_DRIVE
            )
        )
        assertFalse(
            DriveAccessPolicy.shouldExitDriveCategory(
                isSignedIn = false,
                selectedCategory = DocumentCategory.ALL
            )
        )
        assertFalse(
            DriveAccessPolicy.shouldExitDriveCategory(
                isSignedIn = true,
                selectedCategory = DocumentCategory.GOOGLE_DRIVE
            )
        )
    }
}

