package com.skrypko.dicefamiliar.ui.cards

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.skrypko.dicefamiliar.domain.cards.Rank
import com.skrypko.dicefamiliar.domain.cards.Suit

/**
 * Pip positions of number cards, as fractions of the pip area (x: 0 = left column … 1 = right column,
 * y: 0 = top row … 1 = bottom row), following the traditional French layout. Pips below the middle
 * are drawn upside down, as on printed cards.
 */
object PipLayout {
    private const val L = 0f
    private const val C = 0.5f
    private const val R = 1f
    private const val T3 = 1f / 3
    private const val B3 = 2f / 3

    fun positions(rank: Rank): List<Offset> = when (rank) {
        Rank.ACE -> listOf(Offset(C, 0.5f))
        Rank.TWO -> listOf(Offset(C, 0f), Offset(C, 1f))
        Rank.THREE -> listOf(Offset(C, 0f), Offset(C, 0.5f), Offset(C, 1f))
        Rank.FOUR -> corners()
        Rank.FIVE -> corners() + Offset(C, 0.5f)
        Rank.SIX -> sides(0f, 0.5f, 1f)
        Rank.SEVEN -> sides(0f, 0.5f, 1f) + Offset(C, 0.25f)
        Rank.EIGHT -> sides(0f, 0.5f, 1f) + Offset(C, 0.25f) + Offset(C, 0.75f)
        Rank.NINE -> sides(0f, T3, B3, 1f) + Offset(C, 0.5f)
        Rank.TEN -> sides(0f, T3, B3, 1f) + Offset(C, 1f / 6) + Offset(C, 5f / 6)
        Rank.JACK, Rank.QUEEN, Rank.KING -> emptyList()
    }

    private fun corners() = sides(0f, 1f)

    private fun sides(vararg rows: Float) = rows.flatMap { y -> listOf(Offset(L, y), Offset(R, y)) }
}

/** The pips of a number card (or the single large pip of an ace), drawn as vector suit shapes. */
@Composable
internal fun Pips(rank: Rank, suit: Suit, color: Color, scale: Float, modifier: Modifier = Modifier) {
    val ace = rank == Rank.ACE
    // Keep clear of the corner indices on the left and right, and a little from top and bottom.
    BoxWithConstraints(modifier.fillMaxSize().padding(horizontal = (17 * scale).dp, vertical = (7 * scale).dp)) {
        val pip = ((if (ace) 44 else 15) * scale).dp
        PipLayout.positions(rank).forEach { p ->
            val x = (maxWidth - pip) * p.x
            val y = (maxHeight - pip) * p.y
            SuitIcon(suit, color, Modifier.offset(x, y).size(pip).rotate(if (p.y > 0.5f) 180f else 0f))
        }
    }
}

/** A crisp suit symbol (never an emoji), filling its square box. */
@Composable
internal fun SuitIcon(suit: Suit, color: Color, modifier: Modifier = Modifier) {
    Canvas(modifier) { drawSuit(suit, color) }
}

/** Draws [suit] centred in the current draw area, as large as fits. */
internal fun DrawScope.drawSuit(suit: Suit, color: Color) {
    val s = minOf(size.width, size.height)
    val c = Offset(size.width / 2, size.height / 2)
    fun pt(x: Float, y: Float) = Offset(c.x + x * s, c.y + y * s)
    when (suit) {
        Suit.DIAMONDS -> drawPath(
            Path().apply {
                moveTo(pt(0f, -0.5f).x, pt(0f, -0.5f).y)
                lineTo(pt(0.38f, 0f).x, pt(0.38f, 0f).y)
                lineTo(pt(0f, 0.5f).x, pt(0f, 0.5f).y)
                lineTo(pt(-0.38f, 0f).x, pt(-0.38f, 0f).y)
                close()
            },
            color,
        )
        Suit.HEARTS -> drawPath(
            Path().apply {
                moveTo(pt(0f, 0.46f).x, pt(0f, 0.46f).y)
                cubicTo(pt(-0.62f, 0.02f).x, pt(-0.62f, 0.02f).y, pt(-0.42f, -0.56f).x, pt(-0.42f, -0.56f).y, pt(0f, -0.22f).x, pt(0f, -0.22f).y)
                cubicTo(pt(0.42f, -0.56f).x, pt(0.42f, -0.56f).y, pt(0.62f, 0.02f).x, pt(0.62f, 0.02f).y, pt(0f, 0.46f).x, pt(0f, 0.46f).y)
                close()
            },
            color,
        )
        Suit.SPADES -> {
            drawPath(
                Path().apply {
                    moveTo(pt(0f, -0.5f).x, pt(0f, -0.5f).y)
                    cubicTo(pt(0.62f, -0.06f).x, pt(0.62f, -0.06f).y, pt(0.42f, 0.44f).x, pt(0.42f, 0.44f).y, pt(0f, 0.14f).x, pt(0f, 0.14f).y)
                    cubicTo(pt(-0.42f, 0.44f).x, pt(-0.42f, 0.44f).y, pt(-0.62f, -0.06f).x, pt(-0.62f, -0.06f).y, pt(0f, -0.5f).x, pt(0f, -0.5f).y)
                    close()
                },
                color,
            )
            drawStem(::pt, color)
        }
        Suit.CLUBS -> {
            drawCircle(color, radius = s * 0.2f, center = pt(0f, -0.25f))
            drawCircle(color, radius = s * 0.2f, center = pt(-0.24f, 0.07f))
            drawCircle(color, radius = s * 0.2f, center = pt(0.24f, 0.07f))
            drawCircle(color, radius = s * 0.1f, center = pt(0f, 0.02f))
            drawStem(::pt, color)
        }
    }
}

private fun DrawScope.drawStem(pt: (Float, Float) -> Offset, color: Color) {
    drawPath(
        Path().apply {
            moveTo(pt(0f, 0.02f).x, pt(0f, 0.02f).y)
            lineTo(pt(0.16f, 0.5f).x, pt(0.16f, 0.5f).y)
            lineTo(pt(-0.16f, 0.5f).x, pt(-0.16f, 0.5f).y)
            close()
        },
        color,
    )
}

private val Gold = Color(0xFFD9A528)
private val Blue = Color(0xFF2F5DA8)
private val Skin = Color(0xFFF5D7B5)
private val Outline = Color(0xFF2B2420)

/**
 * A court card picture (original artwork): a double-headed portrait in a frame, the lower half the
 * upper half turned around, in traditional court-card colours. King: crown, beard, sword. Queen:
 * rounded crown, long hair, flower. Jack: feathered cap, staff.
 */
@Composable
internal fun CourtPortrait(rank: Rank, suit: Suit, color: Color, scale: Float, modifier: Modifier = Modifier) {
    Box(modifier.border((1.2f * scale).coerceAtLeast(0.6f).dp, color.copy(alpha = 0.7f))) {
        Canvas(Modifier.fillMaxSize()) {
            drawRect(color.copy(alpha = 0.05f))
            half(rank, color)
            rotate(180f) { half(rank, color) }
            drawLine(Gold, Offset(0f, size.height / 2), Offset(size.width, size.height / 2), strokeWidth = size.width * 0.012f)
        }
        SuitIcon(suit, color, Modifier.align(Alignment.TopStart).padding((3 * scale).dp).size((11 * scale).dp))
        SuitIcon(suit, color, Modifier.align(Alignment.BottomEnd).padding((3 * scale).dp).size((11 * scale).dp).rotate(180f))
    }
}

/** The upper half of the portrait (y from 0 to the middle). */
private fun DrawScope.half(rank: Rank, robe: Color) {
    val w = size.width
    val hh = size.height / 2
    fun p(x: Float, y: Float) = Offset(w * x, hh * y)
    val line = Stroke(w * 0.012f)

    // Hair behind the head (queen's long hair).
    if (rank == Rank.QUEEN) {
        drawOval(Gold.copy(red = 0.75f, green = 0.55f), topLeft = p(0.29f, 0.14f), size = Size(w * 0.42f, hh * 0.62f))
    }
    // Robe and shoulders.
    val shoulders = Path().apply {
        moveTo(w * 0.04f, hh)
        lineTo(w * 0.04f, hh * 0.8f)
        lineTo(w * 0.3f, hh * 0.6f)
        lineTo(w * 0.7f, hh * 0.6f)
        lineTo(w * 0.96f, hh * 0.8f)
        lineTo(w * 0.96f, hh)
        close()
    }
    drawPath(shoulders, robe)
    drawPath(shoulders, Outline, style = line)
    // Blue band down the front, a gold collar, and gold trim on the shoulders.
    drawRect(Blue, topLeft = p(0.44f, 0.78f), size = Size(w * 0.12f, hh * 0.22f))
    val collar = Path().apply {
        moveTo(w * 0.34f, hh * 0.6f)
        lineTo(w * 0.66f, hh * 0.6f)
        lineTo(w * 0.5f, hh * 0.84f)
        close()
    }
    drawPath(collar, Gold)
    drawPath(collar, Outline, style = line)
    drawLine(Gold, p(0.06f, 0.86f), p(0.3f, 0.68f), strokeWidth = w * 0.025f)
    drawLine(Gold, p(0.94f, 0.86f), p(0.7f, 0.68f), strokeWidth = w * 0.025f)

    // Neck and head.
    drawRect(Skin, topLeft = p(0.45f, 0.46f), size = Size(w * 0.1f, hh * 0.16f))
    drawOval(Skin, topLeft = p(0.36f, 0.2f), size = Size(w * 0.28f, hh * 0.32f))
    drawOval(Outline, topLeft = p(0.36f, 0.2f), size = Size(w * 0.28f, hh * 0.32f), style = line)
    drawCircle(Outline, radius = w * 0.018f, center = p(0.44f, 0.34f))
    drawCircle(Outline, radius = w * 0.018f, center = p(0.56f, 0.34f))
    drawLine(Outline, p(0.46f, 0.44f), p(0.54f, 0.44f), strokeWidth = w * 0.012f, cap = StrokeCap.Round)

    when (rank) {
        Rank.KING -> {
            // Beard, crown with jewels, and a sword.
            val beard = Path().apply {
                moveTo(w * 0.37f, hh * 0.38f)
                quadraticTo(w * 0.38f, hh * 0.62f, w * 0.5f, hh * 0.66f)
                quadraticTo(w * 0.62f, hh * 0.62f, w * 0.63f, hh * 0.38f)
                quadraticTo(w * 0.5f, hh * 0.5f, w * 0.37f, hh * 0.38f)
                close()
            }
            drawPath(beard, Color(0xFF8A6A4A))
            drawLine(Color(0xFF8A6A4A), p(0.43f, 0.42f), p(0.57f, 0.42f), strokeWidth = w * 0.03f, cap = StrokeCap.Round)
            val crown = Path().apply {
                moveTo(w * 0.34f, hh * 0.24f)
                lineTo(w * 0.34f, hh * 0.08f)
                lineTo(w * 0.42f, hh * 0.16f)
                lineTo(w * 0.5f, hh * 0.02f)
                lineTo(w * 0.58f, hh * 0.16f)
                lineTo(w * 0.66f, hh * 0.08f)
                lineTo(w * 0.66f, hh * 0.24f)
                close()
            }
            drawPath(crown, Gold)
            drawPath(crown, Outline, style = line)
            drawCircle(Color(0xFFC62828), radius = w * 0.022f, center = p(0.5f, 0.19f))
            drawCircle(Blue, radius = w * 0.018f, center = p(0.4f, 0.2f))
            drawCircle(Blue, radius = w * 0.018f, center = p(0.6f, 0.2f))
            drawLine(Color(0xFF9EA4AA), p(0.84f, 0.08f), p(0.84f, 0.86f), strokeWidth = w * 0.035f)
            drawLine(Gold, p(0.76f, 0.7f), p(0.92f, 0.7f), strokeWidth = w * 0.03f, cap = StrokeCap.Round)
        }
        Rank.QUEEN -> {
            // Rounded crown with pearls, and a flower.
            drawRect(Gold, topLeft = p(0.37f, 0.14f), size = Size(w * 0.26f, hh * 0.09f))
            listOf(0.4f, 0.5f, 0.6f).forEach { x -> drawCircle(Gold, radius = w * 0.04f, center = p(x, 0.12f)) }
            listOf(0.4f, 0.5f, 0.6f).forEach { x -> drawCircle(Color.White, radius = w * 0.016f, center = p(x, 0.1f)) }
            drawRect(Outline, topLeft = p(0.37f, 0.14f), size = Size(w * 0.26f, hh * 0.09f), style = line)
            drawLine(Color(0xFF3E7D3A), p(0.18f, 0.94f), p(0.18f, 0.62f), strokeWidth = w * 0.02f)
            val petals = listOf(Offset(0f, -1f), Offset(0.95f, -0.31f), Offset(0.59f, 0.81f), Offset(-0.59f, 0.81f), Offset(-0.95f, -0.31f))
            val centre = p(0.18f, 0.56f)
            petals.forEach { d -> drawCircle(Color(0xFFC62828), radius = w * 0.04f, center = centre + Offset(d.x * w * 0.045f, d.y * w * 0.045f)) }
            drawCircle(Gold, radius = w * 0.03f, center = centre)
        }
        else -> {
            // Jack: short hair, a cap with a feather, and a staff.
            drawArc(Color(0xFF6B4A2B), 180f, 180f, useCenter = true, topLeft = p(0.36f, 0.17f), size = Size(w * 0.28f, hh * 0.2f))
            drawOval(robe, topLeft = p(0.3f, 0.1f), size = Size(w * 0.4f, hh * 0.13f))
            drawOval(Outline, topLeft = p(0.3f, 0.1f), size = Size(w * 0.4f, hh * 0.13f), style = line)
            drawRect(Gold, topLeft = p(0.34f, 0.19f), size = Size(w * 0.32f, hh * 0.04f))
            val feather = Path().apply {
                moveTo(w * 0.62f, hh * 0.12f)
                quadraticTo(w * 0.78f, hh * 0.0f, w * 0.9f, hh * 0.06f)
                quadraticTo(w * 0.78f, hh * 0.08f, w * 0.62f, hh * 0.16f)
                close()
            }
            drawPath(feather, Blue)
            drawLine(Color(0xFF7A5230), p(0.16f, 0.12f), p(0.16f, 0.96f), strokeWidth = w * 0.025f)
            val spear = Path().apply {
                moveTo(w * 0.16f, hh * 0.02f)
                lineTo(w * 0.21f, hh * 0.14f)
                lineTo(w * 0.11f, hh * 0.14f)
                close()
            }
            drawPath(spear, Color(0xFF9EA4AA))
        }
    }
}
