package com.yomi.reader.ui.dashboard.components

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class FolderAppearanceCatalogTest {

    @Test
    fun defaultPresets_assignDistinctColorsAndMatchingIconsForBuiltInFolders() {
        val studyColor = FolderAppearanceCatalog.defaultColorKeyFor("Study")
        val workColor = FolderAppearanceCatalog.defaultColorKeyFor("Work")
        val personalColor = FolderAppearanceCatalog.defaultColorKeyFor("Personal")
        val archiveColor = FolderAppearanceCatalog.defaultColorKeyFor("Archive")

        assertNotEquals(studyColor, workColor)
        assertNotEquals(workColor, personalColor)
        assertNotEquals(personalColor, archiveColor)

        assertEquals("school", FolderAppearanceCatalog.defaultIconKeyFor("Study"))
        assertEquals("work", FolderAppearanceCatalog.defaultIconKeyFor("Work"))
        assertEquals("person", FolderAppearanceCatalog.defaultIconKeyFor("Personal"))
        assertEquals("archive", FolderAppearanceCatalog.defaultIconKeyFor("Archive"))
        assertEquals("code", FolderAppearanceCatalog.defaultIconKeyFor("Capstone_Project"))
    }

    @Test
    fun colorAndIconLookups_fallBackCleanlyAndCycleSuggestions() {
        assertEquals("lavender", FolderAppearanceCatalog.colorForKey("missing").key)
        assertEquals("folder", FolderAppearanceCatalog.iconForKey("missing").key)

        val suggested0 = FolderAppearanceCatalog.nextSuggestedColorKey(0)
        val suggested1 = FolderAppearanceCatalog.nextSuggestedColorKey(1)
        assertNotEquals(suggested0, suggested1)
    }
}

