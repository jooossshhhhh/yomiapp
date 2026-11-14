package com.yomi.reader.ui.dashboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yomi.reader.domain.model.DocumentCategory
import com.yomi.reader.domain.model.DocumentSortOrder
import com.yomi.reader.ui.theme.DocsGridActiveDark

/**
 * Google Docs Style Sub-header Toolbar matching Screenshot 1.
 * Features:
 * - Left: "Date opened by me" with small round down-arrow pill chip
 * - Right: Dual-segment pill container (List icon vs Grid icon with active dark green container)
 */
@Composable
fun HomeUtilityBar(
    totalDocuments: Int,
    totalStorageFormatted: String,
    selectedCategory: DocumentCategory,
    onClearCategoryFilter: () -> Unit,
    currentSortOrder: DocumentSortOrder,
    onSortOrderChange: (DocumentSortOrder) -> Unit,
    isGridView: Boolean,
    onToggleViewMode: () -> Unit,
    isFavoritesOnly: Boolean = false,
    onToggleFavoritesOnly: () -> Unit = {},
    onEnterSelectionMode: (() -> Unit)? = null,
    sectionTitle: String = "Recent",
    modifier: Modifier = Modifier
) {
    var showSortMenu by remember { mutableStateOf(false) }

    val sortLabel = when (currentSortOrder) {
        DocumentSortOrder.DATE_DESC -> "Date opened"
        DocumentSortOrder.DATE_ASC -> "Oldest"
        DocumentSortOrder.NAME_ASC -> "Title (A–Z)"
        DocumentSortOrder.NAME_DESC -> "Title (Z–A)"
        DocumentSortOrder.SIZE_DESC -> "Largest"
        DocumentSortOrder.SIZE_ASC -> "Smallest"
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 6.dp, bottom = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = sectionTitle,
            style = MaterialTheme.typography.titleMedium,
            fontSize = 18.sp,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Sort Selector: "Date opened ⌄"
            Box {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .clickable { showSortMenu = true }
                        .padding(horizontal = 6.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = sortLabel,
                        style = MaterialTheme.typography.bodySmall,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(2.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Sort order",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(16.dp)
                    )
                }

                DropdownMenu(
                    expanded = showSortMenu,
                    onDismissRequest = { showSortMenu = false }
                ) {
                    listOf(
                        DocumentSortOrder.DATE_DESC to "Date opened",
                        DocumentSortOrder.NAME_ASC to "Title (A–Z)",
                        DocumentSortOrder.SIZE_DESC to "File size",
                        DocumentSortOrder.DATE_ASC to "Oldest opened"
                    ).forEach { (order, label) ->
                        val isSelected = order == currentSortOrder
                        DropdownMenuItem(
                            text = {
                                Text(
                                    text = label,
                                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                )
                            },
                            trailingIcon = {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            },
                            onClick = {
                                onSortOrderChange(order)
                                showSortMenu = false
                            }
                        )
                    }
                }
            }

            // Single Circular List / Grid Toggle Icon Button
            Box(
                modifier = Modifier
                    .size(34.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { onToggleViewMode() },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isGridView) Icons.Default.GridView else Icons.AutoMirrored.Filled.ViewList,
                    contentDescription = if (isGridView) "Switch to list view" else "Switch to grid view",
                    tint = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}
