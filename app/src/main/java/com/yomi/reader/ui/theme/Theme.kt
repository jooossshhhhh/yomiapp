package com.yomi.reader.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val DarkColorScheme = darkColorScheme(
    primary = SagePrimaryDark,
    onPrimary = SageOnPrimaryDark,
    primaryContainer = SagePrimaryContainerDark,
    onPrimaryContainer = SageOnPrimaryContainerDark,
    secondary = SageSecondaryDark,
    onSecondary = SageOnSecondaryDark,
    secondaryContainer = SageSecondaryContainerDark,
    onSecondaryContainer = SageOnSecondaryContainerDark,
    tertiary = SageTertiaryDark,
    onTertiary = SageOnTertiaryDark,
    tertiaryContainer = SageTertiaryContainerDark,
    onTertiaryContainer = SageOnTertiaryContainerDark,
    background = SageBackgroundDark,
    onBackground = SageOnBackgroundDark,
    surface = SageSurfaceDark,
    onSurface = SageOnSurfaceDark,
    surfaceVariant = SageSurfaceVariantDark,
    onSurfaceVariant = SageOnSurfaceVariantDark,
    surfaceContainerLowest = SageSurfaceContainerLowestDark,
    surfaceContainerLow = SageSurfaceContainerLowDark,
    surfaceContainer = SageSurfaceContainerDark,
    surfaceContainerHigh = SageSurfaceContainerHighDark,
    surfaceContainerHighest = SageSurfaceContainerHighestDark,
    outline = SageOutlineDark,
    outlineVariant = SageOutlineVariantDark
)

private val LightColorScheme = lightColorScheme(
    primary = SagePrimaryLight,
    onPrimary = SageOnPrimaryLight,
    primaryContainer = SagePrimaryContainerLight,
    onPrimaryContainer = SageOnPrimaryContainerLight,
    secondary = SageSecondaryLight,
    onSecondary = SageOnSecondaryLight,
    secondaryContainer = SageSecondaryContainerLight,
    onSecondaryContainer = SageOnSecondaryContainerLight,
    tertiary = SageTertiaryLight,
    onTertiary = SageOnTertiaryLight,
    tertiaryContainer = SageTertiaryContainerLight,
    onTertiaryContainer = SageOnTertiaryContainerLight,
    background = SageBackgroundLight,
    onBackground = SageOnBackgroundLight,
    surface = SageSurfaceLight,
    onSurface = SageOnSurfaceLight,
    surfaceVariant = SageSurfaceVariantLight,
    onSurfaceVariant = SageOnSurfaceVariantLight,
    outline = SageOutlineLight,
    outlineVariant = SageOutlineVariantLight
)

@Composable
fun YomiTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    // Dynamic color is available on Android 12+
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    val colorScheme = when {
        dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
            if (darkTheme) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        }
        darkTheme -> DarkColorScheme
        else -> LightColorScheme
    }

    val appTypography = remember(context) {
        val assets = context.assets
        // Soften weight contrast across Google Sans Flex: map SemiBold -> 500 and Bold -> 600
        // so card titles and headers remain distinct without feeling heavy or stark.
        val appFontFamily = FontFamily(
            Font(path = "fonts/googlesansflex_regular.ttf", assetManager = assets, weight = FontWeight.Light, style = FontStyle.Normal),
            Font(path = "fonts/googlesansflex_regular.ttf", assetManager = assets, weight = FontWeight.Normal, style = FontStyle.Normal),
            Font(path = "fonts/googlesansflex_medium.ttf", assetManager = assets, weight = FontWeight.Medium, style = FontStyle.Normal),
            Font(path = "fonts/googlesansflex_medium.ttf", assetManager = assets, weight = FontWeight.SemiBold, style = FontStyle.Normal),
            Font(path = "fonts/googlesansflex_semibold.ttf", assetManager = assets, weight = FontWeight.Bold, style = FontStyle.Normal),
            Font(path = "fonts/googlesansflex_semibold.ttf", assetManager = assets, weight = FontWeight.ExtraBold, style = FontStyle.Normal),
            Font(path = "fonts/googlesansflex_semibold.ttf", assetManager = assets, weight = FontWeight.Black, style = FontStyle.Normal)
        )
        Typography.copy(
            displayLarge = Typography.displayLarge.copy(fontFamily = appFontFamily, fontWeight = FontWeight.SemiBold),
            displayMedium = Typography.displayMedium.copy(fontFamily = appFontFamily, fontWeight = FontWeight.SemiBold),
            displaySmall = Typography.displaySmall.copy(fontFamily = appFontFamily, fontWeight = FontWeight.SemiBold),
            headlineLarge = Typography.headlineLarge.copy(fontFamily = appFontFamily, fontWeight = FontWeight.SemiBold),
            headlineMedium = Typography.headlineMedium.copy(fontFamily = appFontFamily, fontWeight = FontWeight.Medium),
            headlineSmall = Typography.headlineSmall.copy(fontFamily = appFontFamily, fontWeight = FontWeight.Medium),
            titleLarge = Typography.titleLarge.copy(fontFamily = appFontFamily, fontWeight = FontWeight.Medium),
            titleMedium = Typography.titleMedium.copy(fontFamily = appFontFamily, fontWeight = FontWeight.Medium),
            titleSmall = Typography.titleSmall.copy(fontFamily = appFontFamily, fontWeight = FontWeight.Medium),
            bodyLarge = Typography.bodyLarge.copy(fontFamily = appFontFamily, fontWeight = FontWeight.Normal, letterSpacing = 0.15.sp),
            bodyMedium = Typography.bodyMedium.copy(fontFamily = appFontFamily, fontWeight = FontWeight.Normal, letterSpacing = 0.15.sp),
            bodySmall = Typography.bodySmall.copy(fontFamily = appFontFamily, fontWeight = FontWeight.Normal, letterSpacing = 0.2.sp),
            labelLarge = Typography.labelLarge.copy(fontFamily = appFontFamily, fontWeight = FontWeight.Medium),
            labelMedium = Typography.labelMedium.copy(fontFamily = appFontFamily, fontWeight = FontWeight.Medium),
            labelSmall = Typography.labelSmall.copy(fontFamily = appFontFamily, fontWeight = FontWeight.Medium)
        )
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = appTypography,
        content = content
    )
}