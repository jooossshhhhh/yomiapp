package com.yomi.reader.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GitHubUpdateCheckerTest {

    @Test
    fun isRemoteVersionNewer_comparesStableAndBetaVersionsAccurately() {
        assertFalse(GitHubUpdateChecker.isRemoteVersionNewer("1.0.0-beta", "v1.0.0-beta"))
        assertTrue(GitHubUpdateChecker.isRemoteVersionNewer("1.0.0-beta", "v1.0.0"))
        assertTrue(GitHubUpdateChecker.isRemoteVersionNewer("1.0.0-beta", "v1.0.1-beta"))
        assertTrue(GitHubUpdateChecker.isRemoteVersionNewer("1.0.0", "v1.1.0"))
        assertFalse(GitHubUpdateChecker.isRemoteVersionNewer("1.1.0", "v1.0.0-beta"))
        assertTrue(GitHubUpdateChecker.isRemoteVersionNewer("1.0.0-beta", "v1.0.5"))
        assertFalse(GitHubUpdateChecker.isRemoteVersionNewer("1.0.5", "v1.0.5"))
        assertTrue(GitHubUpdateChecker.isRemoteVersionNewer("1.0.0-beta.1", "v1.0.0-beta.2"))
    }

    @Test
    fun formatReleaseNotes_convertsMarkdownHeadingsAndBulletsCleanly() {
        val raw = """
            ## Yomi v1.1.0
            - **Fixed**: Crash on sharing PDF
            * Improved dark mode contrast
        """.trimIndent()
        val formatted = GitHubUpdateChecker.formatReleaseNotes(raw)
        assertTrue(formatted.contains("Yomi v1.1.0"))
        assertTrue(formatted.contains("• Fixed: Crash on sharing PDF"))
        assertTrue(formatted.contains("• Improved dark mode contrast"))
        assertEquals("1.0.0-beta", GitHubUpdateChecker.normalizeVersionString("v1.0.0-beta"))
    }
}

