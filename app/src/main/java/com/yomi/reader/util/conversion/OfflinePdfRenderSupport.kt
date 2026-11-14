package com.yomi.reader.util.conversion

import android.content.Context
import android.content.res.AssetManager
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.LeadingMarginSpan
import android.text.style.MetricAffectingSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StrikethroughSpan
import android.text.style.SubscriptSpan
import android.text.style.SuperscriptSpan
import android.text.style.TabStopSpan
import android.text.style.UnderlineSpan
import java.util.concurrent.ConcurrentHashMap
import androidx.compose.ui.text.style.TextAlign
import com.yomi.reader.parser.docx.DocxContentParser
import com.yomi.reader.parser.docx.DocxElement
import com.yomi.reader.parser.docx.DocxRun
import com.yomi.reader.parser.pptx.PptxSlideParser
import com.yomi.reader.parser.pptx.SlideElement
import com.yomi.reader.parser.pptx.SlideParagraph
import com.yomi.reader.parser.xlsx.SpreadsheetSheet
import com.yomi.reader.parser.xlsx.XlsxSheetParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.max
import kotlin.math.min

internal object OfflinePdfRenderSupport {
    const val A4_PORTRAIT_WIDTH = 595
    const val A4_PORTRAIT_HEIGHT = 842
    const val A4_LANDSCAPE_WIDTH = 842
    const val A4_LANDSCAPE_HEIGHT = 595

    internal fun parseColorSafe(hex: String?): Int? {
        if (hex.isNullOrBlank()) return null
        val cleaned = hex.trim().removePrefix("#")
        if (cleaned.length != 6) return null
        return runCatching { Color.parseColor("#$cleaned") }.getOrNull()
    }

    internal fun mapLayoutAlignment(alignment: TextAlign): Layout.Alignment {
        return when (alignment) {
            TextAlign.Center -> Layout.Alignment.ALIGN_CENTER
            TextAlign.End, TextAlign.Right -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }
    }

    internal fun mapLayoutAlignmentStr(alignment: String): Layout.Alignment {
        return when (alignment.lowercase()) {
            "center" -> Layout.Alignment.ALIGN_CENTER
            "right", "end" -> Layout.Alignment.ALIGN_OPPOSITE
            else -> Layout.Alignment.ALIGN_NORMAL
        }
    }

    internal data class ResolvedTypeface(
        val typeface: Typeface,
        val needsFakeBold: Boolean,
        val needsFakeItalic: Boolean
    )

    internal data class AssetFontSet(
        val regular: String,
        val bold: String? = null,
        val italic: String? = null,
        val boldItalic: String? = null,
        val inherentlyBold: Boolean = false
    )

    internal val officeFontAssets = mapOf(
        "arimo" to AssetFontSet(
            regular = "fonts/arimo_regular.ttf",
            bold = "fonts/arimo_bold.ttf",
            italic = "fonts/arimo_italic.ttf",
            boldItalic = "fonts/arimo_bold_italic.ttf"
        ),
        "tinos" to AssetFontSet(
            regular = "fonts/tinos_regular.ttf",
            bold = "fonts/tinos_bold.ttf",
            italic = "fonts/tinos_italic.ttf",
            boldItalic = "fonts/tinos_bold_italic.ttf"
        ),
        "cousine" to AssetFontSet(
            regular = "fonts/cousine_regular.ttf",
            bold = "fonts/cousine_bold.ttf",
            italic = "fonts/cousine_italic.ttf",
            boldItalic = "fonts/cousine_bold_italic.ttf"
        ),
        "carlito" to AssetFontSet(
            regular = "fonts/carlito_regular.ttf",
            bold = "fonts/carlito_bold.ttf",
            italic = "fonts/carlito_italic.ttf",
            boldItalic = "fonts/carlito_bold_italic.ttf"
        ),
        "caladea" to AssetFontSet(
            regular = "fonts/caladea_regular.ttf",
            bold = "fonts/caladea_bold.ttf",
            italic = "fonts/caladea_italic.ttf",
            boldItalic = "fonts/caladea_bold_italic.ttf"
        ),
        "gelasio" to AssetFontSet(
            regular = "fonts/gelasio_regular.ttf",
            bold = "fonts/gelasio_bold.ttf",
            italic = "fonts/gelasio_italic.ttf",
            boldItalic = "fonts/gelasio_bold_italic.ttf"
        ),
        "inter" to AssetFontSet(
            regular = "fonts/inter_regular.ttf",
            bold = "fonts/inter_bold.ttf",
            italic = "fonts/inter_italic.ttf",
            boldItalic = "fonts/inter_bold_italic.ttf"
        ),
        "inter_light" to AssetFontSet(
            regular = "fonts/inter_light.ttf",
            bold = "fonts/inter_semibold.ttf",
            italic = "fonts/inter_italic.ttf",
            boldItalic = "fonts/inter_bold_italic.ttf"
        ),
        "selawik" to AssetFontSet(
            regular = "fonts/selawik_regular.ttf",
            bold = "fonts/selawik_bold.ttf"
        ),
        "selawik_light" to AssetFontSet(
            regular = "fonts/selawik_light.ttf",
            bold = "fonts/selawik_semibold.ttf"
        ),
        "ebgaramond" to AssetFontSet(
            regular = "fonts/ebgaramond_regular.ttf",
            bold = "fonts/ebgaramond_bold.ttf",
            italic = "fonts/ebgaramond_italic.ttf",
            boldItalic = "fonts/ebgaramond_bold_italic.ttf"
        ),
        "crimsonpro" to AssetFontSet(
            regular = "fonts/crimsonpro_regular.ttf",
            bold = "fonts/crimsonpro_bold.ttf",
            italic = "fonts/crimsonpro_italic.ttf",
            boldItalic = "fonts/crimsonpro_bold_italic.ttf"
        ),
        "opensans" to AssetFontSet(
            regular = "fonts/opensans_regular.ttf",
            bold = "fonts/opensans_bold.ttf",
            italic = "fonts/opensans_italic.ttf",
            boldItalic = "fonts/opensans_bold_italic.ttf"
        ),
        "firasans" to AssetFontSet(
            regular = "fonts/firasans_regular.ttf",
            bold = "fonts/firasans_bold.ttf",
            italic = "fonts/firasans_italic.ttf",
            boldItalic = "fonts/firasans_bold_italic.ttf"
        ),
        "inconsolata" to AssetFontSet(
            regular = "fonts/inconsolata_regular.ttf",
            bold = "fonts/inconsolata_bold.ttf"
        ),
        "comicneue" to AssetFontSet(
            regular = "fonts/comicneue_regular.ttf",
            bold = "fonts/comicneue_bold.ttf",
            italic = "fonts/comicneue_italic.ttf",
            boldItalic = "fonts/comicneue_bold_italic.ttf"
        ),
        "anton" to AssetFontSet(
            regular = "fonts/anton_regular.ttf",
            inherentlyBold = true
        )
    )

    internal val typefaceCache = ConcurrentHashMap<String, ResolvedTypeface>()

    internal fun selectOfficeFontKey(family: String?): String {
        val lower = family?.trim()?.lowercase() ?: ""
        if (lower.isBlank()) return "carlito"
        return when {
            // Exact Microsoft Office & standard sans-serif families
            lower.contains("arial black") || lower == "impact" ||
                lower.contains("franklin gothic heavy") -> "anton"
            lower.contains("arial") || lower.contains("helvetica") ||
                lower.contains("arimo") || lower.contains("liberation sans") -> "arimo"
            lower.contains("aptos") || lower.contains("bierstadt") ||
                lower == "inter" || lower.contains("calibri light") -> {
                if (lower.contains("light")) "inter_light" else "inter"
            }
            lower.contains("calibri") || lower.contains("carlito") ||
                lower.contains("candara") || lower.contains("corbel") -> "carlito"
            lower.contains("segoe") || lower.contains("selawik") -> {
                if (lower.contains("light")) "selawik_light" else "selawik"
            }
            lower.contains("trebuchet") || lower.contains("fira sans") ||
                lower.contains("gill") || lower.contains("century gothic") -> "firasans"
            lower.contains("verdana") || lower.contains("tahoma") ||
                lower.contains("open sans") || lower.contains("lucida sans") ||
                lower.contains("dejavu sans") -> "opensans"

            // Exact Microsoft Office & classic serif families
            lower.contains("georgia") || lower.contains("gelasio") -> "gelasio"
            lower.contains("times") || lower.contains("tinos") ||
                lower.contains("liberation serif") -> "tinos"
            lower.contains("cambria") || lower.contains("caladea") -> "caladea"
            lower.contains("garamond") || lower.contains("baskerville") ||
                lower.contains("bookman") || lower.contains("didot") ||
                lower.contains("century") -> "ebgaramond"
            lower.contains("palatino") || lower.contains("book antiqua") ||
                lower.contains("sylfaen") || lower.contains("crimson") -> "crimsonpro"

            // Exact monospace families
            lower.contains("courier") || lower.contains("cousine") ||
                lower.contains("liberation mono") -> "cousine"
            lower.contains("consolas") || lower.contains("lucida console") ||
                lower.contains("cascadia") || lower.contains("monaco") ||
                lower.contains("menlo") || lower.contains("inconsolata") ||
                lower.contains("jetbrains") || lower.contains("mono") ||
                lower.contains("code") -> "inconsolata"

            // Casual / Handwritten
            lower.contains("comic") || lower.contains("kristen") ||
                lower.contains("ink free") || lower.contains("bradley") -> "comicneue"

            // Fallback within our bundled library (NEVER Android's device font!)
            lower.contains("serif") -> "tinos"
            lower.contains("sans") -> "arimo"
            else -> "carlito"
        }
    }

    internal fun resolveOfficeTypeface(
        assets: AssetManager,
        family: String?,
        style: Int,
        embeddedFonts: Map<String, Map<Int, String>> = emptyMap()
    ): ResolvedTypeface {
        val wantBold = (style and Typeface.BOLD) != 0
        val wantItalic = (style and Typeface.ITALIC) != 0

        // 1. Check embedded fonts extracted directly from the DOCX package
        val lowerFamily = family?.trim()?.lowercase()
        if (!lowerFamily.isNullOrBlank()) {
            val embMap = embeddedFonts[lowerFamily]
            if (!embMap.isNullOrEmpty()) {
                val exactPath = embMap[style]
                val fallbackPath = exactPath
                    ?: (if (wantBold) embMap[Typeface.BOLD] else null)
                    ?: (if (wantItalic) embMap[Typeface.ITALIC] else null)
                    ?: embMap[Typeface.NORMAL]
                    ?: embMap.values.firstOrNull()
                if (fallbackPath != null) {
                    val key = "emb_${fallbackPath}_$style"
                    typefaceCache[key]?.let { return it }
                    val tf = runCatching { Typeface.createFromFile(fallbackPath) }.getOrNull()
                    if (tf != null) {
                        val hasBold = exactPath != null || (embMap.containsKey(Typeface.BOLD) && !wantItalic)
                        val hasItalic = exactPath != null || (embMap.containsKey(Typeface.ITALIC) && !wantBold)
                        val res = ResolvedTypeface(
                            typeface = tf,
                            needsFakeBold = wantBold && !hasBold,
                            needsFakeItalic = wantItalic && !hasItalic
                        )
                        typefaceCache[key] = res
                        return res
                    }
                }
            }
        }

        // 2. Load from our dedicated bundled Office Font Library in assets/fonts/
        val key = selectOfficeFontKey(family)
        val cacheKey = "${key}_$style"
        typefaceCache[cacheKey]?.let { return it }

        val set = officeFontAssets[key] ?: officeFontAssets.getValue("carlito")
        val assetPath = when {
            wantBold && wantItalic -> set.boldItalic ?: set.italic ?: set.bold ?: set.regular
            wantBold -> set.bold ?: set.regular
            wantItalic -> set.italic ?: set.regular
            else -> set.regular
        }

        val hasExactBold = when {
            set.inherentlyBold -> true
            wantBold && wantItalic -> set.boldItalic != null || (set.bold != null && assetPath == set.bold)
            wantBold -> set.bold != null
            else -> false
        }
        val hasExactItalic = when {
            wantBold && wantItalic -> set.boldItalic != null || (set.italic != null && assetPath == set.italic)
            wantItalic -> set.italic != null
            else -> false
        }

        val tf = runCatching { Typeface.createFromAsset(assets, assetPath) }
            .getOrElse { Typeface.createFromAsset(assets, "fonts/carlito_regular.ttf") }

        val resolved = ResolvedTypeface(
            typeface = tf,
            needsFakeBold = wantBold && !hasExactBold,
            needsFakeItalic = wantItalic && !hasExactItalic
        )
        typefaceCache[cacheKey] = resolved
        return resolved
    }

    internal class OfficeTypefaceSpan(
        private val resolved: ResolvedTypeface
    ) : MetricAffectingSpan() {
        override fun updateDrawState(ds: TextPaint) = apply(ds)
        override fun updateMeasureState(paint: TextPaint) = apply(paint)

        private fun apply(paint: TextPaint) {
            paint.typeface = resolved.typeface
            paint.isFakeBoldText = resolved.needsFakeBold
            paint.textSkewX = if (resolved.needsFakeItalic) -0.25f else 0f
        }
    }

    internal fun buildDocxParagraphSpannable(
        assets: AssetManager,
        element: DocxElement.Paragraph,
        baseTextSizePt: Float,
        embeddedFonts: Map<String, Map<Int, String>> = emptyMap(),
        blockOffsetPt: Float = 0f,
        includeFirstLineIndent: Boolean = true
    ): SpannableStringBuilder {
        val builder = SpannableStringBuilder()

        // Prepend exact list prefix (e.g. "1.", "a.", "•") with matching font/size/color/style
        val prefix = element.listPrefix
        if (!prefix.isNullOrBlank()) {
            val pStart = builder.length
            builder.append(prefix).append("  ")
            val pEnd = builder.length
            val refRun = element.runs.firstOrNull()
            val prefixSize = refRun?.fontSizeSp ?: element.fontSizeSp ?: baseTextSizePt
            val prefixRatio = (prefixSize / baseTextSizePt).coerceIn(0.4f, 6.0f)
            if (kotlin.math.abs(prefixRatio - 1.0f) > 0.02f) {
                builder.setSpan(RelativeSizeSpan(prefixRatio), pStart, pEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val prefixBold = refRun?.isBold == true || element.isHeading
            val prefixItalic = refRun?.isItalic == true
            val prefixStyle = when {
                prefixBold && prefixItalic -> Typeface.BOLD_ITALIC
                prefixBold -> Typeface.BOLD
                prefixItalic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            val resolvedPrefix = resolveOfficeTypeface(assets, refRun?.fontFamily ?: element.fontFamily, prefixStyle, embeddedFonts)
            builder.setSpan(OfficeTypefaceSpan(resolvedPrefix), pStart, pEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            (parseColorSafe(refRun?.colorHex) ?: parseColorSafe(element.colorHex))?.let { col ->
                builder.setSpan(ForegroundColorSpan(col), pStart, pEnd, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        if (element.runs.isNotEmpty()) {
            for (run in element.runs) {
                val start = builder.length
                builder.append(run.text)
                val end = builder.length
                if (end <= start) continue

                val runStyle = when {
                    run.isBold && run.isItalic -> Typeface.BOLD_ITALIC
                    run.isBold -> Typeface.BOLD
                    run.isItalic -> Typeface.ITALIC
                    else -> Typeface.NORMAL
                }
                val resolvedFamily = run.fontFamily ?: element.fontFamily
                val resolvedTf = resolveOfficeTypeface(assets, resolvedFamily, runStyle, embeddedFonts)
                builder.setSpan(OfficeTypefaceSpan(resolvedTf), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)

                if (run.isUnderline) {
                    builder.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (run.isStrikeThrough) {
                    builder.setSpan(StrikethroughSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
                if (run.isSuperscript) {
                    builder.setSpan(SuperscriptSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                } else if (run.isSubscript) {
                    builder.setSpan(SubscriptSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                val runSize = run.fontSizeSp ?: element.fontSizeSp ?: baseTextSizePt
                val ratio = (runSize / baseTextSizePt).coerceIn(0.4f, 6.0f)
                if (kotlin.math.abs(ratio - 1.0f) > 0.02f) {
                    builder.setSpan(RelativeSizeSpan(ratio), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                (parseColorSafe(run.colorHex) ?: parseColorSafe(element.colorHex))?.let { colorInt ->
                    builder.setSpan(ForegroundColorSpan(colorInt), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }

                parseColorSafe(run.highlightColorHex)?.let { bgColorInt ->
                    builder.setSpan(BackgroundColorSpan(bgColorInt), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                }
            }
        } else if (element.text.isNotEmpty()) {
            val start = builder.length
            builder.append(element.text)
            val end = builder.length
            val paraStyle = when {
                element.isBold && element.isItalic -> Typeface.BOLD_ITALIC
                element.isBold -> Typeface.BOLD
                element.isItalic -> Typeface.ITALIC
                else -> Typeface.NORMAL
            }
            val resolvedTf = resolveOfficeTypeface(assets, element.fontFamily, paraStyle, embeddedFonts)
            builder.setSpan(OfficeTypefaceSpan(resolvedTf), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            if (element.isUnderline) {
                builder.setSpan(UnderlineSpan(), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            val size = element.fontSizeSp ?: baseTextSizePt
            val ratio = (size / baseTextSizePt).coerceIn(0.4f, 6.0f)
            if (kotlin.math.abs(ratio - 1.0f) > 0.02f) {
                builder.setSpan(RelativeSizeSpan(ratio), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
            parseColorSafe(element.colorHex)?.let { colorInt ->
                builder.setSpan(ForegroundColorSpan(colorInt), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            }
        }

        if (builder.isNotEmpty() && (element.alignment == TextAlign.Start || element.alignment == TextAlign.Justify)) {
            val firstPx = if (includeFirstLineIndent && element.firstLineIndentDp > 0) element.firstLineIndentDp else 0
            val restPx = if (element.hangingIndentDp > 0) element.hangingIndentDp else 0
            if (firstPx > 0 || restPx > 0) {
                builder.setSpan(
                    LeadingMarginSpan.Standard(firstPx, restPx),
                    0,
                    builder.length,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
        }

        if (builder.contains('\t')) {
            val firstLeftStop = element.tabStopsPt
                .firstOrNull { it.align == "left" || it.align == "start" }
                ?.let { (it.posPt - blockOffsetPt).toInt() }
                ?: 36
            builder.setSpan(
                TabStopSpan.Standard(firstLeftStop.coerceAtLeast(18)),
                0,
                builder.length,
                Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        return builder
    }

    internal fun buildStaticLayout(
        text: CharSequence,
        paint: TextPaint,
        widthPx: Int,
        alignment: Layout.Alignment,
        lineMultiplier: Float = 1.15f,
        isJustify: Boolean = false
    ): StaticLayout {
        val builder = StaticLayout.Builder.obtain(text, 0, text.length, paint, widthPx.coerceAtLeast(1))
            .setAlignment(alignment)
            .setLineSpacing(0f, lineMultiplier)
            .setIncludePad(false)
        if (isJustify && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            builder.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
        }
        return builder.build()
    }
}