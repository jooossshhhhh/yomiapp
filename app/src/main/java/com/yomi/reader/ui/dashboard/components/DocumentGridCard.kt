package com.yomi.reader.ui.dashboard.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DriveFileMove
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yomi.reader.domain.model.DocumentItem
import com.yomi.reader.domain.model.DocumentType
import com.yomi.reader.ui.theme.ExcelBadgeContainer
import com.yomi.reader.ui.theme.ExcelBadgeContent
import com.yomi.reader.ui.theme.ExcelBrandGreen
import com.yomi.reader.ui.theme.PdfBadgeContainer
import com.yomi.reader.ui.theme.PdfBadgeContent
import com.yomi.reader.ui.theme.PdfBrandRed
import com.yomi.reader.ui.theme.PptBadgeContainer
import com.yomi.reader.ui.theme.PptBadgeContent
import com.yomi.reader.ui.theme.PptBrandOrange
import com.yomi.reader.ui.theme.WordBadgeContainer
import com.yomi.reader.ui.theme.WordBadgeContent
import com.yomi.reader.ui.theme.WordBrandBlue

/**
 * Google Docs Style Document Card matching Screenshot 1.
 * Features:
 * - App icon badge top-left (Blue "W", Red "PDF", Green "X", Orange "P")
 * - Document title + 3-dots overflow menu
 * - White paper sheet thumbnail preview with simulated layout text
 * - Dark circular collaborator badge in the bottom-right corner of the sheet
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun DocumentGridCard(
    document: DocumentItem,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onShare: (() -> Unit)? = null,
    onDownload: (() -> Unit)? = null,
    onDelete: (() -> Unit)? = null,
    onRename: (() -> Unit)? = null,
    onMoveToFolder: (() -> Unit)? = null,
    isSelectionMode: Boolean = false,
    isSelected: Boolean = false,
    columnCount: Int = 2,
    onLongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }
    val isUltraCompact = columnCount >= 4
    val isCompact = columnCount == 3
    val cardPadding = when {
        isUltraCompact -> 7.dp
        isCompact -> 8.dp
        else -> 10.dp
    }
    val badgeSize = when {
        isUltraCompact -> 18.dp
        isCompact -> 20.dp
        else -> 24.dp
    }

    val (brandColor, brandLetter) = when (document.type) {
        DocumentType.WORD -> WordBrandBlue to "W"
        DocumentType.PDF -> PdfBrandRed to "PDF"
        DocumentType.EXCEL -> ExcelBrandGreen to "X"
        DocumentType.POWERPOINT -> PptBrandOrange to "P"
        DocumentType.UNKNOWN -> Color(0xFF607D8B) to "DOC"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) {
                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            } else {
                MaterialTheme.colorScheme.surface
            }
        ),
        border = if (isSelected) {
            BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        } else {
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.85f))
        },
        elevation = CardDefaults.cardElevation(defaultElevation = if (isSelected) 3.dp else 1.5.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(cardPadding)
        ) {
            // 1. Header: Brand Icon + (Inline Title on 2/3 cols) + 3-dots Menu
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = if (isUltraCompact) Alignment.CenterVertically else Alignment.Top,
                horizontalArrangement = if (isUltraCompact) Arrangement.SpaceBetween else Arrangement.Start
            ) {
                Box(
                    modifier = Modifier
                        .size(badgeSize)
                        .clip(RoundedCornerShape(if (isUltraCompact || isCompact) 5.dp else 6.dp))
                        .background(brandColor),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = brandLetter,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = when {
                            brandLetter.length > 1 && (isUltraCompact || isCompact) -> 7.sp
                            brandLetter.length > 1 -> 9.sp
                            isUltraCompact || isCompact -> 10.sp
                            else -> 13.sp
                        }
                    )
                }

                if (!isUltraCompact) {
                    Spacer(modifier = Modifier.width(if (isCompact) 6.dp else 8.dp))

                    Text(
                        text = document.title,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        fontSize = if (isCompact) 11.5.sp else 13.sp,
                        lineHeight = if (isCompact) 14.sp else 17.sp,
                        modifier = Modifier.weight(1f)
                    )
                }

                // 3-dots Menu Icon
                if (isSelectionMode) {
                    Box(
                        modifier = Modifier
                            .size(if (isUltraCompact || isCompact) 18.dp else 22.dp)
                            .clip(CircleShape)
                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                            .border(
                                width = 1.5.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                                shape = CircleShape
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isSelected) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = "Selected",
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(if (isUltraCompact || isCompact) 11.dp else 14.dp)
                            )
                        }
                    }
                } else {
                    Box {
                        IconButton(
                            onClick = { showMenu = true },
                            modifier = Modifier
                                .size(if (isUltraCompact || isCompact) 20.dp else 24.dp)
                                .padding(0.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.MoreVert,
                                contentDescription = "More options",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(if (isUltraCompact || isCompact) 15.dp else 18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text(if (document.isFavorite) "Remove from Favorites" else "Add to Favorites") },
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (document.isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                        contentDescription = null,
                                        tint = if (document.isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    showMenu = false
                                    onToggleFavorite()
                                }
                            )

                            if (onMoveToFolder != null) {
                                DropdownMenuItem(
                                    text = { Text("Move to Folder...") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.DriveFileMove, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onMoveToFolder()
                                    }
                                )
                            }

                            if (onRename != null) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onRename()
                                    }
                                )
                            }

                            if (onDownload != null) {
                                DropdownMenuItem(
                                    text = { Text("Download to Device") },
                                    leadingIcon = { Icon(Icons.Default.FileDownload, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onDownload()
                                    }
                                )
                            }

                            if (onShare != null) {
                                DropdownMenuItem(
                                    text = { Text("Share") },
                                    leadingIcon = { Icon(Icons.Default.Share, contentDescription = null) },
                                    onClick = {
                                        showMenu = false
                                        onShare()
                                    }
                                )
                            }

                            if (onDelete != null) {
                                HorizontalDivider()
                                DropdownMenuItem(
                                    text = { Text("Delete", color = MaterialTheme.colorScheme.error) },
                                    leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                                    onClick = {
                                        showMenu = false
                                        onDelete()
                                    }
                                )
                            }
                        }
                    }
                }
            }

            if (isUltraCompact) {
                Spacer(modifier = Modifier.height(5.dp))
                Text(
                    text = document.title,
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    fontSize = 10.5.sp,
                    lineHeight = 13.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(if (isUltraCompact || isCompact) 7.dp else 10.dp))

            // 2. First-Page PDF Preview Sheet
            val (badgeBg, badgeFg) = when (document.type) {
                DocumentType.PDF -> PdfBadgeContainer to PdfBadgeContent
                DocumentType.WORD -> WordBadgeContainer to WordBadgeContent
                DocumentType.EXCEL -> ExcelBadgeContainer to ExcelBadgeContent
                DocumentType.POWERPOINT -> PptBadgeContainer to PptBadgeContent
                DocumentType.UNKNOWN -> MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
            }
            PdfMiniaturePageTile(
                uriString = document.uriString,
                lastModified = document.lastModified,
                isPdf = document.type == DocumentType.PDF,
                contentDescription = document.title,
                fallbackLabel = brandLetter,
                fallbackBadgeBg = badgeBg,
                fallbackBadgeFg = badgeFg,
                cornerRadius = 8.dp,
                shadowElevation = 2.dp,
                badgeFontSize = if (isUltraCompact) 9.sp else 11.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(
                        when {
                            isUltraCompact -> 0.82f
                            isCompact -> 0.86f
                            else -> 0.98f
                        }
                    )
            )
        }
    }
}
