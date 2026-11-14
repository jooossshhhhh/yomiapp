package com.yomi.reader.ui.viewer.components

import android.graphics.Bitmap
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.yomi.reader.domain.model.DocumentItem
import com.yomi.reader.ui.viewer.annotation.StickyNote

@Composable
internal fun PdfJumpToPageDialog(
    currentVisiblePage: Int,
    maxPage: Int,
    onJumpToPage: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    var pageInput by remember { mutableStateOf(currentVisiblePage.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Jump to Page (1 - $maxPage)") },
        text = {
            Column {
                OutlinedTextField(
                    value = pageInput,
                    onValueChange = { pageInput = it.filter { c -> c.isDigit() } },
                    label = { Text("Page Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(modifier = Modifier.height(12.dp))

                val sliderVal = pageInput.toIntOrNull()?.coerceIn(1, maxPage)?.toFloat() ?: 1f
                Slider(
                    value = sliderVal,
                    onValueChange = { pageInput = it.toInt().toString() },
                    valueRange = 1f..maxPage.toFloat(),
                    steps = if (maxPage > 2) maxPage - 2 else 0,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    TextButton(onClick = { pageInput = "1" }) { Text("First") }
                    TextButton(onClick = { pageInput = maxPage.toString() }) { Text("Last") }
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val target = pageInput.toIntOrNull()?.coerceIn(1, maxPage) ?: currentVisiblePage
                onJumpToPage(target)
                onDismiss()
            }) {
                Text("Go")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PdfThumbnailsSheet(
    maxPage: Int,
    currentVisiblePage: Int,
    thumbnailBitmaps: Map<Int, Bitmap>,
    onRequestThumbnail: suspend (Int) -> Unit,
    onSelectPage: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val isExpandableToTop = maxPage > 6
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = !isExpandableToTop)
    val initialGridIndex = (currentVisiblePage - 1).coerceIn(0, (maxPage - 1).coerceAtLeast(0))
    val gridState = rememberLazyGridState(initialFirstVisibleItemIndex = initialGridIndex)
    val isExpanded = sheetState.currentValue == SheetValue.Expanded

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isExpandableToTop) Modifier.fillMaxHeight() else Modifier)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Page Thumbnails ($maxPage pages)",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
            )

            val gridModifier = if (isExpandableToTop) {
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
            } else {
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
            }

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Fixed(3),
                // While partially expanded, route upward drags anywhere on the grid to the sheet
                // so the user can smoothly pull the thumbnails sheet all the way to the top.
                userScrollEnabled = !isExpandableToTop || isExpanded,
                contentPadding = PaddingValues(bottom = 24.dp),
                modifier = gridModifier,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(count = maxPage, key = { it }) { pageIdx ->
                    val thumbBitmap = thumbnailBitmaps[pageIdx]
                    LaunchedEffect(pageIdx) {
                        if (thumbBitmap == null) {
                            onRequestThumbnail(pageIdx)
                        }
                    }

                    val isCurrent = (pageIdx == currentVisiblePage - 1)

                    Card(
                        shape = RoundedCornerShape(8.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectPage(pageIdx) }
                            .border(
                                width = if (isCurrent) 2.dp else 0.dp,
                                color = if (isCurrent) MaterialTheme.colorScheme.primary else Color.Transparent,
                                shape = RoundedCornerShape(8.dp)
                            )
                    ) {
                        Column(
                            modifier = Modifier.padding(6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            if (thumbBitmap != null) {
                                Image(
                                    bitmap = thumbBitmap.asImageBitmap(),
                                    contentDescription = "Thumb ${pageIdx + 1}",
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.75f)
                                        .clip(RoundedCornerShape(4.dp))
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(0.75f)
                                        .background(Color.LightGray.copy(alpha = 0.3f)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                                }
                            }

                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "${pageIdx + 1}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun PdfBookmarksDialog(
    bookmarkedPages: List<Int>,
    onSelectPage: (Int) -> Unit,
    onRemoveBookmark: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sortedPages = remember(bookmarkedPages) { bookmarkedPages.sorted() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bookmarked Pages") },
        text = {
            if (sortedPages.isEmpty()) {
                Text("No pages bookmarked yet. Tap the bookmark icon in the top bar to save pages.")
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(sortedPages.size) { idx ->
                        val pageIdx = sortedPages[idx]
                        Card(
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelectPage(pageIdx) }
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Page ${pageIdx + 1}", fontWeight = FontWeight.SemiBold)
                                }
                                IconButton(onClick = { onRemoveBookmark(pageIdx) }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        }
    )
}

@Composable
internal fun PdfDocumentInfoDialog(
    document: DocumentItem,
    totalPages: Int?,
    bookmarkCount: Int,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Document Info") },
        text = {
            Column {
                Text("Title: ${document.title}", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text("Format: PDF Document", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                totalPages?.let {
                    Text("Total Pages: $it", style = MaterialTheme.typography.bodyMedium)
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text("Bookmarks: $bookmarkCount", style = MaterialTheme.typography.bodyMedium)
                Spacer(modifier = Modifier.height(4.dp))
                Text("URI: ${document.uriString}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

