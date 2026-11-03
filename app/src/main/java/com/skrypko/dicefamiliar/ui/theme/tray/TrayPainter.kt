package com.skrypko.dicefamiliar.ui.theme.tray

import androidx.compose.foundation.background
import androidx.compose.material3.ColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PointMode
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawOutline
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.inset
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.skrypko.dicefamiliar.ui.theme.LocalThemeStyle
import kotlin.math.max

/** Text and icon colours that stay readable on a tray. */
data class TrayColors(
    /** Main text. */
    val content: Color,
    /** Hints and captions. */
    val muted: Color,
    /** Big numbers and highlighted icons. */
    val accent: Color,
)

/**
 * Paints a play area (the dice tray, the number result) for a theme. Painters are parameterised by
 * colours, so a new theme can reuse one with its own palette or bring a new implementation.
 * Decoration only: anything that looks random comes from a fixed hash, never from RandomSource.
 */
interface TrayPainter {
    /** Extra padding so content clears decorations such as a rim. */
    val contentInset: Dp get() = 0.dp

    /** Paints the tray behind this modifier's content, clipped to [shape]. */
    fun Modifier.paintTray(shape: Shape, scheme: ColorScheme): Modifier

    fun colors(scheme: ColorScheme): TrayColors
}

/** A plain tonal surface (`surfaceContainerHigh`) with Material text colours. */
object PlainTray : TrayPainter {
    override fun Modifier.paintTray(shape: Shape, scheme: ColorScheme): Modifier =
        background(scheme.surfaceContainerHigh, shape)

    override fun colors(scheme: ColorScheme) = TrayColors(scheme.onSurface, scheme.onSurfaceVariant, scheme.primary)
}

/** Billiard cloth inside a wooden rim, with a soft cushion shadow and fine cloth grain. */
data class FeltTray(
    val feltLight: Color,
    val felt: Color,
    val feltDark: Color,
    val woodLight: Color,
    val woodDark: Color,
    val text: TrayColors,
    val rim: Dp = 10.dp,
) : TrayPainter {
    override val contentInset: Dp get() = rim / 2

    override fun colors(scheme: ColorScheme) = text

    override fun Modifier.paintTray(shape: Shape, scheme: ColorScheme): Modifier = clip(shape).drawWithCache {
        val rimPx = rim.toPx()
        val wood = Brush.linearGradient(listOf(woodLight, woodDark, woodLight, woodDark), Offset.Zero, Offset(size.width, size.height))
        val feltWidth = (size.width - 2 * rimPx).coerceAtLeast(1f)
        val feltHeight = (size.height - 2 * rimPx).coerceAtLeast(1f)
        val cloth = Brush.radialGradient(
            listOf(feltLight, felt, feltDark),
            center = Offset(feltWidth / 2, feltHeight / 2),
            radius = max(feltWidth, feltHeight) * 0.7f,
        )
        val spacing = 5.dp.toPx()
        val count = ((feltWidth * feltHeight) / (spacing * spacing)).toInt().coerceIn(0, MAX_SPECKS)
        val light = ArrayList<Offset>(count / 2)
        val dark = ArrayList<Offset>(count / 2)
        for (i in 0 until count) {
            val point = Offset(hash01(i * 3) * feltWidth, hash01(i * 3 + 1) * feltHeight)
            if (hash01(i * 3 + 2) > 0.5f) light += point else dark += point
        }
        val corner = CornerRadius(18.dp.toPx())
        val speck = 1.2.dp.toPx()
        val grain = 1.dp.toPx()
        onDrawBehind {
            drawRect(wood)
            var y = grain * 3
            while (y < size.height) {
                drawLine(Color.Black.copy(alpha = 0.12f), Offset(0f, y), Offset(size.width, y + rimPx), strokeWidth = grain)
                y += grain * 7
            }
            inset(rimPx) {
                drawRoundRect(cloth, cornerRadius = corner)
                drawPoints(light, PointMode.Points, Color.White.copy(alpha = 0.07f), strokeWidth = speck, cap = StrokeCap.Round)
                drawPoints(dark, PointMode.Points, Color.Black.copy(alpha = 0.12f), strokeWidth = speck, cap = StrokeCap.Round)
                drawRoundRect(Color.Black.copy(alpha = 0.35f), cornerRadius = corner, style = Stroke(4.dp.toPx()))
                drawRoundRect(Color.Black.copy(alpha = 0.15f), cornerRadius = corner, style = Stroke(10.dp.toPx()))
            }
        }
    }
}

/** A dark panel with a neon grid and a glowing two-colour edge. */
data class NeonGridTray(
    val top: Color,
    val bottom: Color,
    val lineA: Color,
    val lineB: Color,
    val text: TrayColors,
    val cell: Dp = 24.dp,
) : TrayPainter {
    override fun colors(scheme: ColorScheme) = text

    override fun Modifier.paintTray(shape: Shape, scheme: ColorScheme): Modifier = clip(shape).drawWithCache {
        val background = Brush.verticalGradient(listOf(top, bottom))
        val step = cell.toPx()
        val line = 1.dp.toPx()
        val outline = shape.createOutline(size, layoutDirection, this)
        val edge = Brush.linearGradient(listOf(lineA, lineB), Offset.Zero, Offset(size.width, size.height))
        onDrawBehind {
            drawRect(background)
            var x = step / 2
            while (x < size.width) {
                drawLine(lineA.copy(alpha = 0.10f), Offset(x, 0f), Offset(x, size.height), strokeWidth = line)
                x += step
            }
            var y = step / 2
            while (y < size.height) {
                drawLine(lineB.copy(alpha = 0.08f), Offset(0f, y), Offset(size.width, y), strokeWidth = line)
                y += step
            }
            // Glow: wide faint strokes under a thin bright one (the outer halves are clipped away).
            drawOutline(outline, edge, alpha = 0.12f, style = Stroke(14.dp.toPx()))
            drawOutline(outline, edge, alpha = 0.30f, style = Stroke(6.dp.toPx()))
            drawOutline(outline, edge, alpha = 0.95f, style = Stroke(2.dp.toPx()))
        }
    }
}

/** Paints this play area with the active theme's tray. */
@Composable
fun Modifier.themedTray(shape: Shape): Modifier {
    val scheme = MaterialTheme.colorScheme
    return with(LocalThemeStyle.current.tray) { paintTray(shape, scheme) }
}

/** Readable text colours for the active theme's tray. */
@Composable
@ReadOnlyComposable
fun trayColors(): TrayColors = LocalThemeStyle.current.tray.colors(MaterialTheme.colorScheme)

/** A fixed integer hash mapped to [0, 1): decoration only, never used for results. */
private fun hash01(n: Int): Float {
    var x = n * -0x61c88647 + 0x27d4eb2d
    x = x xor (x ushr 15)
    x *= -0x7a143595
    x = x xor (x ushr 13)
    x *= -0x3d4d51cb
    x = x xor (x ushr 16)
    return (x ushr 8) / 16_777_216f
}

private const val MAX_SPECKS = 8000
