package com.yomi.reader.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance

// Yomi Dual-Tone Editorial Palette (Soft Lavender + Eucalyptus Sage Mint on Warm Ivory / Obsidian Slate)

// Primary: Deep Muted Lavender-Plum (derived from Logo Left Page #B8B0D0)
val SagePrimaryLight = Color(0xFF5E5180)
val SageOnPrimaryLight = Color(0xFFFFFFFF)
val SagePrimaryContainerLight = Color(0xFFEAE6F5)
val SageOnPrimaryContainerLight = Color(0xFF231A3D)

val SagePrimaryDark = Color(0xFFBBA8FF)
val SageOnPrimaryDark = Color(0xFF1D133D)
val SagePrimaryContainerDark = Color(0xFF312854)
val SageOnPrimaryContainerDark = Color(0xFFEDE8FF)

// Secondary: Eucalyptus Sage Mint (derived from Logo Right Page #C8DCD4)
val SageSecondaryLight = Color(0xFF4A6B61)
val SageOnSecondaryLight = Color(0xFFFFFFFF)
val SageSecondaryContainerLight = Color(0xFFDCEBE5)
val SageOnSecondaryContainerLight = Color(0xFF152922)

val SageSecondaryDark = Color(0xFF7FD1BA)
val SageOnSecondaryDark = Color(0xFF0E2922)
val SageSecondaryContainerDark = Color(0xFF1E3731)
val SageOnSecondaryContainerDark = Color(0xFFD5F2E9)

// Tertiary: Soft Slate Periwinkle / Sky Blue Accent
val SageTertiaryLight = Color(0xFF52647A)
val SageOnTertiaryLight = Color(0xFFFFFFFF)
val SageTertiaryContainerLight = Color(0xFFDCE5F2)
val SageOnTertiaryContainerLight = Color(0xFF122133)

val SageTertiaryDark = Color(0xFF8DB7FF)
val SageOnTertiaryDark = Color(0xFF0F2442)
val SageTertiaryContainerDark = Color(0xFF1B2E4B)
val SageOnTertiaryContainerDark = Color(0xFFDCE9FF)

// Balanced Warm Ivory Paper & Obsidian Slate Dark Hierarchy
val SageBackgroundLight = Color(0xFFF6F6F2) // Warm ivory canvas (from logo backdrop #F4F4F0)
val SageOnBackgroundLight = Color(0xFF2B2D35) // Soft warm charcoal instead of harsh pitch black
val SageSurfaceLight = Color(0xFFFFFFFF) // Pure crisp white for cards, sheets, bottom bar
val SageOnSurfaceLight = Color(0xFF2B2D35) // Soft warm charcoal for calm readability
val SageSurfaceVariantLight = Color(0xFFEEEEEC) // Warm stone neutral for search pill & chips
val SageOnSurfaceVariantLight = Color(0xFF626670) // Balanced slate for metadata & subtitles
val SageOutlineLight = Color(0xFFC8C9C2) // Soft border
val SageOutlineVariantLight = Color(0xFFE6E6E0) // Delicate card hairline

val SageBackgroundDark = Color(0xFF0E1015)
val SageOnBackgroundDark = Color(0xFFECEEF4)
val SageSurfaceDark = Color(0xFF171A22)
val SageOnSurfaceDark = Color(0xFFECEEF4)
val SageSurfaceVariantDark = Color(0xFF222734)
val SageOnSurfaceVariantDark = Color(0xFFA7AEC2)
val SageOutlineDark = Color(0xFF3E4559)
val SageOutlineVariantDark = Color(0xFF2A3040)

val SageSurfaceContainerLowestDark = Color(0xFF0A0C10)
val SageSurfaceContainerLowDark = Color(0xFF14171F)
val SageSurfaceContainerDark = Color(0xFF1B1F2A)
val SageSurfaceContainerHighDark = Color(0xFF232836)
val SageSurfaceContainerHighestDark = Color(0xFF2C3243)

// Signature Yomi Accents
val YomiLogoLavender = Color(0xFFB8B0D0)
val YomiLogoMint = Color(0xFFC8DCD4)
val YomiHeroStartLight = Color(0xFFECE8F6)
val YomiHeroEndLight = Color(0xFFE2EFEA)
val YomiHeroStartDark = Color(0xFF221E36)
val YomiHeroEndDark = Color(0xFF162726)
val DocsFabLime = Color(0xFF5E5180) // Primary Lavender FAB
val DocsFabLimeDark = Color(0xFF4A3F68)
val DocsAiPillMint = Color(0xFFC8DCD4)
val DocsGridActiveDark = Color(0xFF5E5180)
val DocsPaperWhite = Color(0xFFFFFFFF)
val DocsBadgeBadgeBg = Color(0xFF5E5180)

// Document Format Brand Badges (Exact Google Drive & Docs branding)
val WordBrandBlue = Color(0xFF2684FC) // Bold Word blue
val PdfBrandRed = Color(0xFFEA4335) // PDF red
val ExcelBrandGreen = Color(0xFF0F9D58) // Excel / Sheets green
val PptBrandOrange = Color(0xFFFF9800) // Slides orange

// Light/Default compatibility badges
val PdfBadgeContainer = Color(0xFFFDE8E7)
val PdfBadgeContent = Color(0xFFD93025)
val WordBadgeContainer = Color(0xFFE8F0FE)
val WordBadgeContent = Color(0xFF1A73E8)
val ExcelBadgeContainer = Color(0xFFE6F4EA)
val ExcelBadgeContent = Color(0xFF137333)
val PptBadgeContainer = Color(0xFFFEF7E0)
val PptBadgeContent = Color(0xFFEA8600)

val WarmDarkOnSurfaceDark = Color(0xFFE4E2E6)
val WarmDarkSurfaceVariantDark = Color(0xFF2C2D35)
val WarmDarkOnSurfaceVariantDark = Color(0xFFC7C5D0)

@Composable
fun isYomiDarkTheme(): Boolean = MaterialTheme.colorScheme.background.luminance() <= 0.5f

@Composable
fun yomiPdfBadgeColors(): Pair<Color, Color> = if (isYomiDarkTheme()) {
    Color(0xFF3E1F23) to Color(0xFFFCA5A5)
} else {
    PdfBadgeContainer to PdfBadgeContent
}

@Composable
fun yomiWordBadgeColors(): Pair<Color, Color> = if (isYomiDarkTheme()) {
    Color(0xFF192B47) to Color(0xFF93C5FD)
} else {
    WordBadgeContainer to WordBadgeContent
}

@Composable
fun yomiExcelBadgeColors(): Pair<Color, Color> = if (isYomiDarkTheme()) {
    Color(0xFF163628) to Color(0xFF6EE7B7)
} else {
    ExcelBadgeContainer to ExcelBadgeContent
}

@Composable
fun yomiPptBadgeColors(): Pair<Color, Color> = if (isYomiDarkTheme()) {
    Color(0xFF3D2A15) to Color(0xFFFCD34D)
} else {
    PptBadgeContainer to PptBadgeContent
}

@Composable
fun yomiDriveAccentColor(): Color = if (isYomiDarkTheme()) Color(0xFF60A5FA) else Color(0xFF1A73E8)

@Composable
fun yomiDriveButtonContainerColor(): Color = if (isYomiDarkTheme()) Color(0xFF2563EB) else Color(0xFF1A73E8)

@Composable
fun yomiDriveSurfaceColor(): Color = if (isYomiDarkTheme()) Color(0xFF162235) else Color(0xFFE8F0FE)

@Composable
fun yomiDriveOnSurfaceColor(): Color = if (isYomiDarkTheme()) Color(0xFFBFDBFE) else Color(0xFF174EA6)

@Composable
fun yomiDriveSubtleTextColor(): Color = if (isYomiDarkTheme()) Color(0xFF9FB5D6) else Color(0xFF3C4043)

@Composable
fun yomiDriveBorderColor(): Color = if (isYomiDarkTheme()) Color(0xFF3B82F6).copy(alpha = 0.34f) else Color(0xFF1A73E8).copy(alpha = 0.24f)

@Composable
fun yomiDriveGreenSurfaceColor(): Color = if (isYomiDarkTheme()) Color(0xFF142D23) else Color(0xFFE6F4EA)

@Composable
fun yomiDriveGreenAccentColor(): Color = if (isYomiDarkTheme()) Color(0xFF4ADE80) else Color(0xFF137333)

@Composable
fun yomiDriveGreenTitleColor(): Color = if (isYomiDarkTheme()) Color(0xFFBBF7D0) else Color(0xFF0D652D)