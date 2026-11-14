package com.yomi.reader.data.local

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.yomi.reader.ui.viewer.annotation.DrawingPoint
import com.yomi.reader.ui.viewer.annotation.DrawingStroke
import com.yomi.reader.ui.viewer.annotation.NormalizedRect
import com.yomi.reader.ui.viewer.annotation.PdfPageAnnotation
import com.yomi.reader.ui.viewer.annotation.StickyNote
import com.yomi.reader.ui.viewer.annotation.TextHighlight
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

data class SavedPdfAnnotations(
    val marksByPage: Map<Int, List<PdfPageAnnotation>>,
    val stickyNotes: List<StickyNote>,
    val bookmarkedPages: List<Int> = emptyList()
)

class PdfAnnotationsRepository(context: Context) {
    private val annotationsDir = File(context.filesDir, "pdf_annotations").apply { mkdirs() }
    private val snippetsDir = File(annotationsDir, "snippets").apply { mkdirs() }

    private fun snippetFileFor(noteId: String): File {
        val safeName = noteId.replace(Regex("[^A-Za-z0-9_-]"), "_")
        return File(snippetsDir, "$safeName.jpg")
    }

    suspend fun saveNoteSnippet(noteId: String, bitmap: Bitmap) = withContext(Dispatchers.IO) {
        runCatching {
            val target = snippetFileFor(noteId)
            val temp = File(snippetsDir, "${target.name}.tmp")
            FileOutputStream(temp).use { out ->
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            if (target.exists()) target.delete()
            if (!temp.renameTo(target)) temp.delete()
        }
    }

    suspend fun loadNoteSnippet(noteId: String): Bitmap? = withContext(Dispatchers.IO) {
        val file = snippetFileFor(noteId)
        if (!file.exists() || file.length() == 0L) return@withContext null
        runCatching { BitmapFactory.decodeFile(file.absolutePath) }.getOrNull()
    }

    fun deleteNoteSnippet(noteId: String) {
        runCatching { snippetFileFor(noteId).delete() }
    }

    private fun annotationFileFor(documentKey: String): File {
        val digest = MessageDigest.getInstance("SHA-256")
            .digest(documentKey.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
        return File(annotationsDir, "$digest.json")
    }

    suspend fun loadAnnotations(documentKey: String): SavedPdfAnnotations = withContext(Dispatchers.IO) {
        val file = annotationFileFor(documentKey)
        if (!file.exists() || file.length() == 0L) {
            return@withContext SavedPdfAnnotations(emptyMap(), emptyList())
        }
        try {
            val root = JSONObject(file.readText(Charsets.UTF_8))
            val marksMap = mutableMapOf<Int, MutableList<PdfPageAnnotation>>()
            val marksArray = root.optJSONArray("marks") ?: JSONArray()
            for (i in 0 until marksArray.length()) {
                val obj = marksArray.optJSONObject(i) ?: continue
                val pageIndex = obj.optInt("pageIndex", 0)
                val type = obj.optString("type")
                when (type) {
                    "highlight" -> {
                        val id = obj.optString("id", System.nanoTime().toString())
                        val colorInt = obj.optInt("color", 0xFFFACC15.toInt())
                        val rectsJson = obj.optJSONArray("rects") ?: JSONArray()
                        val rects = mutableListOf<NormalizedRect>()
                        for (r in 0 until rectsJson.length()) {
                            val rObj = rectsJson.optJSONObject(r) ?: continue
                            rects.add(
                                NormalizedRect(
                                    left = rObj.optDouble("l", 0.0).toFloat(),
                                    top = rObj.optDouble("t", 0.0).toFloat(),
                                    right = rObj.optDouble("r", 0.0).toFloat(),
                                    bottom = rObj.optDouble("b", 0.0).toFloat()
                                )
                            )
                        }
                        if (rects.isNotEmpty()) {
                            marksMap.getOrPut(pageIndex) { mutableListOf() }.add(
                                PdfPageAnnotation.Highlight(
                                    pageIndex = pageIndex,
                                    highlight = TextHighlight(
                                        id = id,
                                        pageIndex = pageIndex,
                                        rects = rects,
                                        color = Color(colorInt)
                                    )
                                )
                            )
                        }
                    }
                    "stroke" -> {
                        val id = obj.optString("id", System.nanoTime().toString())
                        val colorInt = obj.optInt("color", 0xFF1E293B.toInt())
                        val strokeWidth = obj.optDouble("strokeWidth", 4.0).toFloat()
                        val isHighlighter = obj.optBoolean("isHighlighter", false)
                        val ptsJson = obj.optJSONArray("points") ?: JSONArray()
                        val points = mutableListOf<DrawingPoint>()
                        for (p in 0 until ptsJson.length()) {
                            val pObj = ptsJson.optJSONObject(p) ?: continue
                            points.add(
                                DrawingPoint(
                                    xRatio = pObj.optDouble("x", 0.0).toFloat(),
                                    yRatio = pObj.optDouble("y", 0.0).toFloat()
                                )
                            )
                        }
                        if (points.isNotEmpty()) {
                            marksMap.getOrPut(pageIndex) { mutableListOf() }.add(
                                PdfPageAnnotation.Stroke(
                                    pageIndex = pageIndex,
                                    stroke = DrawingStroke(
                                        id = id,
                                        points = points,
                                        color = Color(colorInt),
                                        strokeWidth = strokeWidth,
                                        isHighlighter = isHighlighter
                                    )
                                )
                            )
                        }
                    }
                }
            }

            val notesList = mutableListOf<StickyNote>()
            val notesArray = root.optJSONArray("notes") ?: JSONArray()
            for (i in 0 until notesArray.length()) {
                val obj = notesArray.optJSONObject(i) ?: continue
                val rectsJson = obj.optJSONArray("anchorRects") ?: JSONArray()
                val rects = mutableListOf<NormalizedRect>()
                for (r in 0 until rectsJson.length()) {
                    val rObj = rectsJson.optJSONObject(r) ?: continue
                    rects.add(
                        NormalizedRect(
                            left = rObj.optDouble("l", 0.0).toFloat(),
                            top = rObj.optDouble("t", 0.0).toFloat(),
                            right = rObj.optDouble("r", 0.0).toFloat(),
                            bottom = rObj.optDouble("b", 0.0).toFloat()
                        )
                    )
                }
                notesList.add(
                    StickyNote(
                        id = obj.optString("id", System.currentTimeMillis().toString()),
                        pageIndex = obj.optInt("pageIndex", 0),
                        text = obj.optString("text", ""),
                        author = obj.optString("author", "You"),
                        timestamp = obj.optLong("timestamp", System.currentTimeMillis()),
                        colorValue = obj.optLong("colorValue", 0xFFFDE047L),
                        anchorRects = rects
                    )
                )
            }

            val bookmarksSet = LinkedHashSet<Int>()
            val bookmarksArray = root.optJSONArray("bookmarks") ?: JSONArray()
            for (i in 0 until bookmarksArray.length()) {
                val pageIdx = bookmarksArray.optInt(i, -1)
                if (pageIdx >= 0) bookmarksSet.add(pageIdx)
            }

            SavedPdfAnnotations(
                marksByPage = marksMap,
                stickyNotes = notesList,
                bookmarkedPages = bookmarksSet.toList()
            )
        } catch (_: Exception) {
            SavedPdfAnnotations(emptyMap(), emptyList(), emptyList())
        }
    }

    suspend fun saveAnnotations(
        documentKey: String,
        marksByPage: Map<Int, List<PdfPageAnnotation>>,
        stickyNotes: List<StickyNote>,
        bookmarkedPages: List<Int> = emptyList()
    ) = withContext(Dispatchers.IO) {
        saveAnnotationsSync(documentKey, marksByPage, stickyNotes, bookmarkedPages)
    }

    fun saveAnnotationsSync(
        documentKey: String,
        marksByPage: Map<Int, List<PdfPageAnnotation>>,
        stickyNotes: List<StickyNote>,
        bookmarkedPages: List<Int> = emptyList()
    ) {
        try {
            val file = annotationFileFor(documentKey)
            val totalMarks = marksByPage.values.sumOf { it.size }
            if (totalMarks == 0 && stickyNotes.isEmpty() && bookmarkedPages.isEmpty()) {
                if (file.exists()) file.delete()
                return
            }

            val marksArray = JSONArray()
            marksByPage.toSortedMap().forEach { (pageIdx, pageList) ->
                pageList.forEach { annotation ->
                    val obj = JSONObject()
                    obj.put("pageIndex", pageIdx)
                    when (annotation) {
                        is PdfPageAnnotation.Highlight -> {
                            obj.put("type", "highlight")
                            obj.put("id", annotation.highlight.id)
                            obj.put("color", annotation.highlight.color.toArgb())
                            val rectsArray = JSONArray()
                            annotation.highlight.rects.forEach { r ->
                                rectsArray.put(
                                    JSONObject()
                                        .put("l", r.left.toDouble())
                                        .put("t", r.top.toDouble())
                                        .put("r", r.right.toDouble())
                                        .put("b", r.bottom.toDouble())
                                )
                            }
                            obj.put("rects", rectsArray)
                        }
                        is PdfPageAnnotation.Stroke -> {
                            obj.put("type", "stroke")
                            obj.put("id", annotation.stroke.id)
                            obj.put("color", annotation.stroke.color.toArgb())
                            obj.put("strokeWidth", annotation.stroke.strokeWidth.toDouble())
                            obj.put("isHighlighter", annotation.stroke.isHighlighter)
                            val ptsArray = JSONArray()
                            annotation.stroke.points.forEach { p ->
                                ptsArray.put(
                                    JSONObject()
                                        .put("x", p.xRatio.toDouble())
                                        .put("y", p.yRatio.toDouble())
                                )
                            }
                            obj.put("points", ptsArray)
                        }
                    }
                    marksArray.put(obj)
                }
            }

            val notesArray = JSONArray()
            stickyNotes.forEach { note ->
                val obj = JSONObject()
                obj.put("id", note.id)
                obj.put("pageIndex", note.pageIndex)
                obj.put("text", note.text)
                obj.put("author", note.author)
                obj.put("timestamp", note.timestamp)
                obj.put("colorValue", note.colorValue)
                val rectsArray = JSONArray()
                note.anchorRects.forEach { r ->
                    rectsArray.put(
                        JSONObject()
                            .put("l", r.left.toDouble())
                            .put("t", r.top.toDouble())
                            .put("r", r.right.toDouble())
                            .put("b", r.bottom.toDouble())
                    )
                }
                obj.put("anchorRects", rectsArray)
                notesArray.put(obj)
            }

            val bookmarksArray = JSONArray()
            bookmarkedPages.distinct().sorted().forEach { pageIdx ->
                if (pageIdx >= 0) bookmarksArray.put(pageIdx)
            }

            val root = JSONObject()
                .put("marks", marksArray)
                .put("notes", notesArray)
                .put("bookmarks", bookmarksArray)

            val tempFile = File(annotationsDir, "${file.name}.tmp")
            tempFile.writeText(root.toString(), Charsets.UTF_8)
            if (!tempFile.renameTo(file)) {
                file.writeText(root.toString(), Charsets.UTF_8)
                tempFile.delete()
            }
        } catch (_: Exception) {
            // Avoid crashing UI on transient filesystem write failures
        }
    }
}

