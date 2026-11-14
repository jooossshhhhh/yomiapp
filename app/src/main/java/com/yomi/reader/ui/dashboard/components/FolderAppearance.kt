package com.yomi.reader.ui.dashboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material.icons.automirrored.filled.ReceiptLong
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Science
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Work
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.yomi.reader.ui.theme.SagePrimaryContainerDark
import com.yomi.reader.ui.theme.SagePrimaryContainerLight
import com.yomi.reader.ui.theme.SagePrimaryDark
import com.yomi.reader.ui.theme.SagePrimaryLight
import com.yomi.reader.ui.theme.SageSecondaryContainerDark
import com.yomi.reader.ui.theme.SageSecondaryContainerLight
import com.yomi.reader.ui.theme.SageSecondaryDark
import com.yomi.reader.ui.theme.SageSecondaryLight
import com.yomi.reader.ui.theme.SageTertiaryContainerDark
import com.yomi.reader.ui.theme.SageTertiaryContainerLight
import com.yomi.reader.ui.theme.SageTertiaryDark
import com.yomi.reader.ui.theme.SageTertiaryLight
import kotlin.math.abs

data class CustomFolderStyle(
    val colorKey: String,
    val iconKey: String
)

data class FolderColorPreset(
    val key: String,
    val label: String,
    val lightAccent: Color,
    val lightContainer: Color,
    val darkAccent: Color,
    val darkContainer: Color
) {
    fun accent(isDark: Boolean): Color = if (isDark) darkAccent else lightAccent
    fun container(isDark: Boolean): Color = if (isDark) darkContainer else lightContainer
}

data class FolderIconPreset(
    val key: String,
    val label: String,
    val icon: ImageVector
)

data class ResolvedFolderAppearance(
    val colorKey: String,
    val iconKey: String,
    val accentColor: Color,
    val containerColor: Color,
    val icon: ImageVector
)

object FolderAppearanceCatalog {

    val colorPresets: List<FolderColorPreset> = listOf(
        FolderColorPreset(
            key = "lavender",
            label = "Lavender",
            lightAccent = SagePrimaryLight,
            lightContainer = SagePrimaryContainerLight,
            darkAccent = SagePrimaryDark,
            darkContainer = SagePrimaryContainerDark
        ),
        FolderColorPreset(
            key = "sage",
            label = "Eucalyptus",
            lightAccent = SageSecondaryLight,
            lightContainer = SageSecondaryContainerLight,
            darkAccent = SageSecondaryDark,
            darkContainer = SageSecondaryContainerDark
        ),
        FolderColorPreset(
            key = "periwinkle",
            label = "Slate Blue",
            lightAccent = SageTertiaryLight,
            lightContainer = SageTertiaryContainerLight,
            darkAccent = SageTertiaryDark,
            darkContainer = SageTertiaryContainerDark
        ),
        FolderColorPreset(
            key = "rose",
            label = "Dusty Plum",
            lightAccent = Color(0xFF885269),
            lightContainer = Color(0xFFF3E2EA),
            darkAccent = Color(0xFFE3B7C8),
            darkContainer = Color(0xFF563342)
        ),
        FolderColorPreset(
            key = "moss",
            label = "Warm Olive",
            lightAccent = Color(0xFF5F6842),
            lightContainer = Color(0xFFE8ECDC),
            darkAccent = Color(0xFFC6CFA4),
            darkContainer = Color(0xFF3B4229)
        ),
        FolderColorPreset(
            key = "amber",
            label = "Terracotta",
            lightAccent = Color(0xFF8B6543),
            lightContainer = Color(0xFFF3E8DD),
            darkAccent = Color(0xFFE2BE9C),
            darkContainer = Color(0xFF4F3A26)
        )
    )

    val iconPresets: List<FolderIconPreset> = listOf(
        FolderIconPreset("folder", "Folder", Icons.Default.Folder),
        FolderIconPreset("work", "Work", Icons.Default.Work),
        FolderIconPreset("school", "Study", Icons.Default.School),
        FolderIconPreset("person", "Personal", Icons.Default.Person),
        FolderIconPreset("archive", "Archive", Icons.Default.Inventory2),
        FolderIconPreset("book", "Reading", Icons.AutoMirrored.Filled.MenuBook),
        FolderIconPreset("science", "Research", Icons.Default.Science),
        FolderIconPreset("code", "Projects", Icons.Default.Code),
        FolderIconPreset("receipt", "Receipts", Icons.AutoMirrored.Filled.ReceiptLong),
        FolderIconPreset("star", "Priority", Icons.Default.Star),
        FolderIconPreset("favorite", "Favorites", Icons.Default.Favorite),
        FolderIconPreset("lightbulb", "Ideas", Icons.Default.Lightbulb)
    )

    fun colorForKey(key: String?): FolderColorPreset {
        return colorPresets.firstOrNull { it.key == key } ?: colorPresets.first()
    }

    fun iconForKey(key: String?): FolderIconPreset {
        return iconPresets.firstOrNull { it.key == key } ?: iconPresets.first()
    }

    fun defaultColorKeyFor(folderName: String): String {
        return when (folderName.trim().lowercase()) {
            "study" -> "periwinkle"
            "work" -> "lavender"
            "personal" -> "sage"
            "archive" -> "amber"
            else -> {
                val index = abs(folderName.trim().lowercase().hashCode()) % colorPresets.size
                colorPresets[index].key
            }
        }
    }

    fun defaultIconKeyFor(folderName: String): String {
        val lower = folderName.trim().lowercase()
        return when {
            lower == "study" || "school" in lower || "class" in lower -> "school"
            lower == "work" || "office" in lower -> "work"
            lower == "personal" -> "person"
            lower == "archive" -> "archive"
            "project" in lower || "capstone" in lower || "code" in lower -> "code"
            "research" in lower || "lab" in lower -> "science"
            "receipt" in lower || "bill" in lower || "finance" in lower -> "receipt"
            "book" in lower || "read" in lower -> "book"
            else -> "folder"
        }
    }

    fun nextSuggestedColorKey(existingFolderCount: Int): String {
        return colorPresets[abs(existingFolderCount) % colorPresets.size].key
    }

    @Composable
    fun resolve(
        folderName: String,
        customStyles: Map<String, CustomFolderStyle>
    ): ResolvedFolderAppearance {
        val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
        val saved = customStyles[folderName]
        val colorKey = saved?.colorKey ?: defaultColorKeyFor(folderName)
        val iconKey = saved?.iconKey ?: defaultIconKeyFor(folderName)
        val colorPreset = colorForKey(colorKey)
        val iconPreset = iconForKey(iconKey)

        return ResolvedFolderAppearance(
            colorKey = colorPreset.key,
            iconKey = iconPreset.key,
            accentColor = colorPreset.accent(isDark),
            containerColor = colorPreset.container(isDark),
            icon = iconPreset.icon
        )
    }
}

@Composable
internal fun FolderAppearanceEditor(
    previewName: String,
    selectedColorKey: String,
    selectedIconKey: String,
    onSelectColorKey: (String) -> Unit,
    onSelectIconKey: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val isDark = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val activeColor = FolderAppearanceCatalog.colorForKey(selectedColorKey)
    val activeIcon = FolderAppearanceCatalog.iconForKey(selectedIconKey)
    val accent = activeColor.accent(isDark)
    val container = activeColor.container(isDark)

    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Live folder badge preview
        Surface(
            shape = RoundedCornerShape(14.dp),
            color = container.copy(alpha = 0.45f),
            border = BorderStroke(1.dp, accent.copy(alpha = 0.35f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(RoundedCornerShape(11.dp))
                        .background(container),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = activeIcon.icon,
                        contentDescription = null,
                        tint = accent,
                        modifier = Modifier.size(21.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = previewName.trim().ifEmpty { "Folder preview" },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${activeColor.label} • ${activeIcon.label}",
                        style = MaterialTheme.typography.labelSmall,
                        color = accent,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // Theme-aligned color swatch picker
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Folder Color",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FolderAppearanceCatalog.colorPresets.forEach { preset ->
                    val swatchAccent = preset.accent(isDark)
                    val isChosen = preset.key == selectedColorKey
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .then(
                                if (isChosen) {
                                    Modifier.border(2.dp, swatchAccent, CircleShape)
                                } else {
                                    Modifier
                                }
                            )
                            .padding(if (isChosen) 4.dp else 2.dp)
                            .clip(CircleShape)
                            .background(swatchAccent)
                            .clickable { onSelectColorKey(preset.key) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isChosen) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = preset.label,
                                tint = if (isDark) Color(0xFF1C1C22) else Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // Icon selector grid (2 rows of 6 icons)
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Folder Icon",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                FolderAppearanceCatalog.iconPresets.chunked(6).forEach { rowIcons ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        rowIcons.forEach { iconPreset ->
                            val isChosen = iconPreset.key == selectedIconKey
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .height(40.dp)
                                    .clip(RoundedCornerShape(11.dp))
                                    .background(
                                        if (isChosen) container
                                        else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                    )
                                    .then(
                                        if (isChosen) {
                                            Modifier.border(1.5.dp, accent, RoundedCornerShape(11.dp))
                                        } else {
                                            Modifier
                                        }
                                    )
                                    .clickable { onSelectIconKey(iconPreset.key) },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = iconPreset.icon,
                                    contentDescription = iconPreset.label,
                                    tint = if (isChosen) accent else MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

