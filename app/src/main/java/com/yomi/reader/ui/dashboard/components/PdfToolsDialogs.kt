package com.yomi.reader.ui.dashboard.components

import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yomi.reader.domain.model.DocumentItem
import com.yomi.reader.util.PdfToolsHelper
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ImagesToPdfDialog(
    imageUris: List<Uri>,
    onRepickImages: () -> Unit,
    onDismiss: () -> Unit,
    onPdfCreated: (File) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val defaultName = remember {
        val stamp = SimpleDateFormat("MMMdd_HHmm", Locale.getDefault()).format(Date())
        "Images_$stamp"
    }
    var outputName by remember { mutableStateOf(defaultName) }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.AddPhotoAlternate,
                    contentDescription = null,
                    tint = Color(0xFF5E35B1)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Images to PDF", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${imageUris.size} ${if (imageUris.size == 1) "image" else "images"} selected",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Each image becomes 1 formatted page",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        TextButton(
                            onClick = onRepickImages,
                            enabled = !isProcessing
                        ) {
                            Text("Change")
                        }
                    }
                }

                OutlinedTextField(
                    value = outputName,
                    onValueChange = { outputName = it },
                    label = { Text("PDF File Name") },
                    singleLine = true,
                    enabled = !isProcessing,
                    modifier = Modifier.fillMaxWidth()
                )

                if (isProcessing) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { (progress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Creating PDF... $progress%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                errorMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            Button(
                enabled = !isProcessing && imageUris.isNotEmpty(),
                onClick = {
                    isProcessing = true
                    errorMessage = null
                    coroutineScope.launch {
                        val result = PdfToolsHelper.imagesToPdf(
                            context = context,
                            imageUris = imageUris,
                            outputBaseName = outputName,
                            onProgress = { progress = it }
                        )
                        isProcessing = false
                        result.onSuccess { file ->
                            onPdfCreated(file)
                        }.onFailure { err ->
                            errorMessage = err.message ?: "Failed to create PDF."
                        }
                    }
                }
            ) {
                Text("Create PDF")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isProcessing,
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun SplitPdfDialog(
    libraryPdfs: List<DocumentItem>,
    externalSourceUri: Uri?,
    externalSourceTitle: String?,
    onPickExternalPdf: () -> Unit,
    onDismiss: () -> Unit,
    onPdfCreated: (File) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var selectedUri by remember(externalSourceUri) {
        mutableStateOf(externalSourceUri ?: libraryPdfs.firstOrNull()?.let { Uri.parse(it.uriString) })
    }
    var selectedTitle by remember(externalSourceUri, externalSourceTitle) {
        mutableStateOf(
            if (externalSourceUri != null) (externalSourceTitle ?: "Selected_PDF")
            else libraryPdfs.firstOrNull()?.title ?: ""
        )
    }

    var totalPages by remember { mutableIntStateOf(0) }
    var isLoadingPages by remember { mutableStateOf(false) }
    var pageRangeInput by remember { mutableStateOf("1") }
    var outputName by remember { mutableStateOf("") }
    var isProcessing by remember { mutableStateOf(false) }
    var progress by remember { mutableIntStateOf(0) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(selectedUri) {
        val uri = selectedUri ?: return@LaunchedEffect
        isLoadingPages = true
        totalPages = 0
        errorMessage = null
        val countResult = PdfToolsHelper.getPdfPageCount(context, uri)
        isLoadingPages = false
        countResult.onSuccess { count ->
            totalPages = count
            val defaultEnd = minOf(3, count).coerceAtLeast(1)
            pageRangeInput = if (count > 1) "1-$defaultEnd" else "1"
            val base = selectedTitle.substringBeforeLast('.').ifBlank { "Document" }
            outputName = "${base}_split"
        }.onFailure { err ->
            errorMessage = err.message ?: "Cannot read PDF pages."
        }
    }

    val parsedPreview = remember(pageRangeInput, totalPages) {
        if (totalPages > 0) PdfToolsHelper.parsePageRanges(pageRangeInput, totalPages) else null
    }

    AlertDialog(
        onDismissRequest = { if (!isProcessing) onDismiss() },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.ContentCut,
                    contentDescription = null,
                    tint = Color(0xFF00897B)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Split PDF Pages", fontWeight = FontWeight.Bold)
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Select Source PDF",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold
                    )
                    TextButton(
                        onClick = onPickExternalPdf,
                        enabled = !isProcessing
                    ) {
                        Icon(Icons.Default.FolderOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Browse Device")
                    }
                }

                if (libraryPdfs.isEmpty() && selectedUri == null) {
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier.padding(16.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "No PDFs in library yet. Tap Browse Device to pick a PDF file.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else {
                    if (externalSourceUri != null && selectedUri == externalSourceUri) {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(
                                    imageVector = Icons.Default.PictureAsPdf,
                                    contentDescription = null,
                                    tint = Color(0xFFE53935),
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = selectedTitle,
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }

                    if (libraryPdfs.isNotEmpty()) {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 150.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            items(libraryPdfs, key = { it.id }) { doc ->
                                val docUri = Uri.parse(doc.uriString)
                                val isSelected = selectedUri == docUri
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = if (isSelected) {
                                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                                    } else {
                                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                                    },
                                    border = if (isSelected) BorderStroke(1.dp, MaterialTheme.colorScheme.primary) else null,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable(enabled = !isProcessing) {
                                            selectedUri = docUri
                                            selectedTitle = doc.title
                                        }
                                ) {
                                    Row(
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.PictureAsPdf,
                                            contentDescription = null,
                                            tint = Color(0xFFE53935),
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text(
                                            text = doc.title,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            modifier = Modifier.weight(1f)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                if (isLoadingPages) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else if (totalPages > 0) {
                    Text(
                        text = "Total pages in PDF: $totalPages",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        if (totalPages >= 2) {
                            val mid = totalPages / 2
                            FilterChip(
                                selected = false,
                                enabled = !isProcessing,
                                onClick = { pageRangeInput = "1-$mid" },
                                label = { Text("First half (1-$mid)", fontSize = 11.sp) }
                            )
                            FilterChip(
                                selected = false,
                                enabled = !isProcessing,
                                onClick = { pageRangeInput = "${mid + 1}-$totalPages" },
                                label = { Text("Second half (${mid + 1}-$totalPages)", fontSize = 11.sp) }
                            )
                            val odds = (1..totalPages step 2).joinToString(",")
                            FilterChip(
                                selected = false,
                                enabled = !isProcessing,
                                onClick = { pageRangeInput = odds },
                                label = { Text("Odd pages", fontSize = 11.sp) }
                            )
                            val evens = (2..totalPages step 2).joinToString(",")
                            FilterChip(
                                selected = false,
                                enabled = !isProcessing,
                                onClick = { pageRangeInput = evens },
                                label = { Text("Even pages", fontSize = 11.sp) }
                            )
                        } else {
                            FilterChip(
                                selected = true,
                                enabled = false,
                                onClick = {},
                                label = { Text("Single page (1)", fontSize = 11.sp) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = pageRangeInput,
                        onValueChange = { pageRangeInput = it },
                        label = { Text("Pages to extract (e.g. 1-3, 5)") },
                        singleLine = true,
                        enabled = !isProcessing,
                        modifier = Modifier.fillMaxWidth()
                    )

                    parsedPreview?.let { res ->
                        res.onSuccess { pages ->
                            Text(
                                text = "Will extract ${pages.size} page${if (pages.size != 1) "s" else ""}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }.onFailure { err ->
                            Text(
                                text = err.message ?: "Invalid page range",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.error
                            )
                        }
                    }

                    OutlinedTextField(
                        value = outputName,
                        onValueChange = { outputName = it },
                        label = { Text("New PDF Name") },
                        singleLine = true,
                        enabled = !isProcessing,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                if (isProcessing) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        LinearProgressIndicator(
                            progress = { (progress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = "Extracting pages... $progress%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                errorMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            val validPages = parsedPreview?.getOrNull()
            Button(
                enabled = !isProcessing && !isLoadingPages && selectedUri != null && !validPages.isNullOrEmpty(),
                onClick = {
                    val uri = selectedUri ?: return@Button
                    val pages = validPages ?: return@Button
                    isProcessing = true
                    errorMessage = null
                    coroutineScope.launch {
                        val result = PdfToolsHelper.splitPdfPages(
                            context = context,
                            sourceUri = uri,
                            pageNumbersOneBased = pages,
                            outputBaseName = outputName,
                            onProgress = { progress = it }
                        )
                        isProcessing = false
                        result.onSuccess { file ->
                            onPdfCreated(file)
                        }.onFailure { err ->
                            errorMessage = err.message ?: "Failed to split PDF."
                        }
                    }
                }
            ) {
                Text("Extract Pages")
            }
        },
        dismissButton = {
            TextButton(
                enabled = !isProcessing,
                onClick = onDismiss
            ) {
                Text("Cancel")
            }
        }
    )
}

