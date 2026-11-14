package com.yomi.reader.ui.viewer

import android.net.Uri
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Warning
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.yomi.reader.domain.model.DocumentItem
import com.yomi.reader.domain.model.DocumentType
import com.yomi.reader.util.DocumentConverter
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DocumentViewerHost(
    document: DocumentItem,
    onBack: () -> Unit,
    recentPdfDocuments: List<DocumentItem> = emptyList(),
    onOpenRecentPdf: ((DocumentItem) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current

    when (document.type) {
        DocumentType.PDF -> {
            PdfViewerScreen(
                document = document,
                onBack = onBack,
                recentPdfDocuments = recentPdfDocuments,
                onOpenRecentPdf = onOpenRecentPdf,
                modifier = modifier
            )
        }

        DocumentType.WORD, DocumentType.EXCEL, DocumentType.POWERPOINT -> {
            var convertedDocItem by remember(document.id, document.uriString) { mutableStateOf<DocumentItem?>(null) }
            var isConverting by remember(document.id, document.uriString) { mutableStateOf(true) }
            var conversionProgress by remember(document.id, document.uriString) { mutableStateOf(0) }
            var conversionError by remember(document.id, document.uriString) { mutableStateOf<String?>(null) }

            LaunchedEffect(document.id, document.uriString) {
                isConverting = true
                conversionProgress = 0
                conversionError = null

                val result = DocumentConverter.convertOfficeToPdf(context, document) { pct ->
                    conversionProgress = pct
                }
                result.onSuccess { pdfFile ->
                    convertedDocItem = document.copy(
                        title = "${document.title.substringBeforeLast('.')}.pdf",
                        type = DocumentType.PDF,
                        uriString = Uri.fromFile(pdfFile).toString(),
                        sizeBytes = pdfFile.length()
                    )
                    isConverting = false
                }.onFailure { err ->
                    conversionError = err.message ?: "Failed to convert document to PDF"
                    isConverting = false
                }
            }

            if (convertedDocItem != null) {
                PdfViewerScreen(
                    document = convertedDocItem!!,
                    onBack = onBack,
                    recentPdfDocuments = recentPdfDocuments,
                    onOpenRecentPdf = onOpenRecentPdf,
                    modifier = modifier
                )
            } else if (isConverting) {
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(text = document.title, maxLines = 1) },
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            }
                        )
                    },
                    modifier = modifier
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                            .background(MaterialTheme.colorScheme.background),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            val animatedProgress by animateFloatAsState(
                                targetValue = (conversionProgress / 100f).coerceIn(0f, 1f),
                                animationSpec = tween(durationMillis = 220, easing = FastOutSlowInEasing),
                                label = "ViewerConversionProgress"
                            )
                            // Play Store-style install progress ring surrounding the document badge
                            Box(
                                modifier = Modifier.size(92.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    progress = { animatedProgress.coerceAtLeast(0.04f) },
                                    color = MaterialTheme.colorScheme.primary,
                                    trackColor = MaterialTheme.colorScheme.primaryContainer,
                                    strokeWidth = 3.5.dp,
                                    strokeCap = StrokeCap.Round,
                                    modifier = Modifier.fillMaxSize()
                                )
                                Surface(
                                    shape = CircleShape,
                                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                    modifier = Modifier.size(68.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.PictureAsPdf,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(32.dp)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(22.dp))

                            LinearProgressIndicator(
                                progress = { animatedProgress },
                                color = MaterialTheme.colorScheme.primary,
                                trackColor = MaterialTheme.colorScheme.primaryContainer,
                                strokeCap = StrokeCap.Round,
                                modifier = Modifier
                                    .fillMaxWidth(0.65f)
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp))
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "${conversionProgress}%",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )

                            Spacer(modifier = Modifier.height(10.dp))

                            Text(
                                text = "Converting to PDF...",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(6.dp))

                            val formatLabel = when (document.type) {
                                DocumentType.WORD -> "Word document"
                                DocumentType.EXCEL -> "Excel spreadsheet"
                                DocumentType.POWERPOINT -> "PowerPoint presentation"
                                else -> "document"
                            }

                            Text(
                                text = "Preparing PDF of your $formatLabel.\nUsing online API when connected, offline engine when offline.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                }
            } else {
                // Conversion error screen
                Scaffold(
                    topBar = {
                        TopAppBar(
                            title = { Text(text = document.title, maxLines = 1) },
                            navigationIcon = {
                                IconButton(onClick = onBack) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = "Back"
                                    )
                                }
                            }
                        )
                    },
                    modifier = modifier
                ) { innerPadding ->
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center,
                            modifier = Modifier.padding(32.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ErrorOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(54.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                text = "Unable to Convert to PDF",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                text = conversionError ?: "Unknown conversion error.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(onClick = onBack) {
                                Text("Back to Library")
                            }
                        }
                    }
                }
            }
        }

        DocumentType.UNKNOWN -> {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = { Text(text = document.title) },
                        navigationIcon = {
                            IconButton(onClick = onBack) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                    contentDescription = "Back"
                                )
                            }
                        }
                    )
                },
                modifier = modifier
            ) { innerPadding ->
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = null,
                            tint = Color(0xFFF57C00),
                            modifier = Modifier.size(54.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "File Format Not Supported",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            text = "Yomi is a PDF reader and converter. We support PDF (.pdf), Word (.docx), Excel (.xlsx), and PowerPoint (.pptx).",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(onClick = onBack) {
                            Text("Back")
                        }
                    }
                }
            }
        }
    }
}
