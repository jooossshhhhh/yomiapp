package com.yomi.reader.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import com.yomi.reader.R
import com.yomi.reader.ui.theme.YomiHeroEndLight
import com.yomi.reader.ui.theme.YomiHeroStartLight

const val YOMI_BRAND_NAME = "YOMI"
const val YOMI_TAGLINE = "Read & Convert, Effortlessly"

@Composable
fun YomiLogo(
    modifier: Modifier = Modifier,
    size: Dp = 72.dp,
    shapeRadius: Dp = 20.dp,
    showContainer: Boolean = true
) {
    val isLightTheme = MaterialTheme.colorScheme.background.red > 0.5f
    val shape = RoundedCornerShape(shapeRadius)
    val containerBrush = if (isLightTheme) {
        Brush.linearGradient(colors = listOf(YomiHeroStartLight, YomiHeroEndLight))
    } else {
        Brush.linearGradient(
            colors = listOf(
                MaterialTheme.colorScheme.surfaceVariant,
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f)
            )
        )
    }

    val boxModifier = if (showContainer) {
        modifier
            .size(size)
            .clip(shape)
            .background(containerBrush)
            .border(
                BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f)),
                shape
            )
    } else {
        modifier.size(size)
    }

    Box(
        modifier = boxModifier,
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.ic_yomi_emblem),
            contentDescription = "Yomi Logo",
            contentScale = ContentScale.Fit,
            modifier = Modifier.size(if (showContainer) size * 0.72f else size)
        )
    }
}

@Composable
fun YomiSplashScreen(
    modifier: Modifier = Modifier
) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { visible = true }

    val transition = rememberInfiniteTransition(label = "SplashPulse")
    val pulseScale by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1.035f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "SplashLogoPulse"
    )

    val enterScale by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0.92f,
        animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
        label = "SplashEnterScale"
    )
    val enterAlpha by androidx.compose.animation.core.animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(durationMillis = 280, easing = LinearEasing),
        label = "SplashEnterAlpha"
    )

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp)
                    .graphicsLayer {
                        scaleX = enterScale
                        scaleY = enterScale
                        alpha = enterAlpha
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    modifier = Modifier.graphicsLayer {
                        scaleX = pulseScale
                        scaleY = pulseScale
                    }
                ) {
                    YomiLogo(
                        size = 96.dp,
                        shapeRadius = 28.dp,
                        showContainer = true
                    )
                }

                Spacer(modifier = Modifier.height(22.dp))

                Text(
                    text = YOMI_BRAND_NAME,
                    style = MaterialTheme.typography.headlineLarge,
                    fontSize = 32.sp,
                    letterSpacing = 6.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = YOMI_TAGLINE,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    letterSpacing = 0.3.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            YomiExpressiveLoadingIndicator(
                size = 38.dp,
                color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 44.dp)
            )
        }
    }
}

/**
 * Play Store–inspired Material 3 Expressive scalloped spinner.
 * Renders a smooth 10-lobe rounded rosette silhouette with continuous rotation and
 * subtle organic breathing motion. Pass [rotationOverride] to scrub rotation smoothly
 * during pull-to-refresh gestures before release.
 */
@Composable
fun YomiExpressiveLoadingIndicator(
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    color: Color = MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
    rotationOverride: Float? = null
) {
    val transition = rememberInfiniteTransition(label = "YomiExpressiveLoader")
    val animatedRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2800, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "YomiLoaderRotation"
    )
    val morphPhase by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "YomiLoaderMorph"
    )
    val path = remember { Path() }
    val activeRotation = rotationOverride ?: animatedRotation
    val breatheScale = if (rotationOverride != null) 1f else (0.94f + (morphPhase * 0.08f))

    Canvas(
        modifier = modifier
            .size(size)
            .graphicsLayer {
                rotationZ = activeRotation
                scaleX = breatheScale
                scaleY = breatheScale
            }
    ) {
        val radius = this.size.minDimension / 2f
        val centerX = this.size.width / 2f
        val centerY = this.size.height / 2f
        val lobeAmplitude = if (rotationOverride != null) 0.095f else (0.085f + (morphPhase * 0.03f))
        val baseRadius = radius / (1f + lobeAmplitude)
        val steps = 140
        val lobes = 10

        path.reset()
        for (i in 0 until steps) {
            val angle = (2.0 * PI * i) / steps
            val r = baseRadius * (1f + lobeAmplitude * cos(lobes * angle).toFloat())
            val x = centerX + r * cos(angle).toFloat()
            val y = centerY + r * sin(angle).toFloat()
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        drawPath(path = path, color = color)
    }
}
