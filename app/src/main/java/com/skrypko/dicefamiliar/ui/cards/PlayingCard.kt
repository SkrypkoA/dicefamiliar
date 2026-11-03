package com.skrypko.dicefamiliar.ui.cards

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.skrypko.dicefamiliar.R
import com.skrypko.dicefamiliar.domain.cards.CardFace
import com.skrypko.dicefamiliar.domain.cards.Rank
import com.skrypko.dicefamiliar.domain.cards.StandardCard
import com.skrypko.dicefamiliar.domain.cards.StandardCards
import com.skrypko.dicefamiliar.domain.cards.Suit
import java.io.File

/** Portrait playing-card proportions (63 × 88 mm). */
const val CARD_ASPECT = 63f / 88f

private val CardStock = Color(0xFFFFFDF7)
private val CardRed = Color(0xFFC62828)
private val CardBlack = Color(0xFF1C1B1F)

/**
 * One card, face up: a classic face for standard cards, the image for image cards, or name / value /
 * suit as text. Card faces use fixed card-stock colours (cards are physical objects); the back
 * follows the theme. The content description is the card name.
 */
@Composable
fun PlayingCard(face: CardFace, modifier: Modifier = Modifier, elevation: Dp = 2.dp) {
    BoxWithConstraints(modifier.aspectRatio(CARD_ASPECT).semantics { contentDescription = face.name }) {
        val width = maxWidth
        val corner = RoundedCornerShape(width * 0.07f)
        Surface(
            shape = corner,
            color = CardStock,
            border = BorderStroke(1.dp, Color(0x33000000)),
            shadowElevation = elevation,
            modifier = Modifier.fillMaxSize(),
        ) {
            val standard = face.standardId?.let { StandardCards.parse(it) }
            when {
                standard != null -> StandardFace(standard, width)
                face.image != null -> AsyncImage(
                    model = File(face.image),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(corner),
                )
                else -> TextFace(face, width)
            }
        }
    }
}

@Composable
private fun StandardFace(card: StandardCard, width: Dp) {
    val scale = width.value / 100f
    when (card) {
        is StandardCard.Joker -> {
            val color = if (card.red) CardRed else CardBlack
            Box(Modifier.fillMaxSize().padding(width * 0.06f)) {
                Text(
                    stringResource(R.string.cards_joker).toList().joinToString("\n"),
                    color = color,
                    fontWeight = FontWeight.Bold,
                    fontSize = (11 * scale).sp,
                    lineHeight = (11.5f * scale).sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.align(Alignment.TopStart),
                )
                Text("★", color = color, fontSize = (54 * scale).sp, modifier = Modifier.align(Alignment.Center))
            }
        }
        is StandardCard.Regular -> {
            val color = if (card.suit.isRed) CardRed else CardBlack
            Box(Modifier.fillMaxSize().padding(width * 0.06f)) {
                Corner(card.rank.label, card.suit, color, scale, Modifier.align(Alignment.TopStart))
                Corner(card.rank.label, card.suit, color, scale, Modifier.align(Alignment.BottomEnd).rotate(180f))
                val court = card.rank in setOf(Rank.JACK, Rank.QUEEN, Rank.KING)
                if (court) {
                    CourtPortrait(
                        card.rank,
                        card.suit,
                        color,
                        scale,
                        Modifier.align(Alignment.Center).padding(horizontal = (15 * scale).dp, vertical = (3 * scale).dp).fillMaxSize(),
                    )
                } else {
                    Pips(card.rank, card.suit, color, scale)
                }
            }
        }
    }
}

@Composable
private fun Corner(value: String, suit: Suit, color: Color, scale: Float, modifier: Modifier) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = color, fontWeight = FontWeight.Bold, fontSize = (15 * scale).sp, lineHeight = (15 * scale).sp)
        SuitIcon(suit, color, Modifier.padding(top = (1 * scale).dp).size((10 * scale).dp))
    }
}

/** An uploaded card without image: value top-left, the name in the middle, the suit below it. */
@Composable
private fun TextFace(face: CardFace, width: Dp) {
    val scale = width.value / 100f
    val color = if (face.suit?.any { it == '♥' || it == '♦' } == true) CardRed else CardBlack
    Box(Modifier.fillMaxSize().padding(width * 0.07f)) {
        face.value?.let {
            Text(it, color = color, fontWeight = FontWeight.Bold, fontSize = (14 * scale).sp, maxLines = 1, modifier = Modifier.align(Alignment.TopStart))
        }
        Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                face.name,
                color = CardBlack,
                fontWeight = FontWeight.SemiBold,
                fontSize = fitSize(face.name, scale),
                textAlign = TextAlign.Center,
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
            face.suit?.let { Text(textPresentation(it), color = color, fontSize = (13 * scale).sp, textAlign = TextAlign.Center, maxLines = 2) }
        }
    }
}

private fun fitSize(text: String, scale: Float): TextUnit = when {
    text.length <= 6 -> 22 * scale
    text.length <= 14 -> 16 * scale
    else -> 12 * scale
}.sp

/** The back of a card: the deck's back image, or a lattice in the theme's primary colours. */
@Composable
fun CardBack(backImage: String?, modifier: Modifier = Modifier, elevation: Dp = 2.dp) {
    BoxWithConstraints(modifier.aspectRatio(CARD_ASPECT)) {
        val corner = RoundedCornerShape(maxWidth * 0.07f)
        val scheme = MaterialTheme.colorScheme
        Surface(
            shape = corner,
            color = scheme.primary,
            border = BorderStroke(1.dp, Color(0x33000000)),
            shadowElevation = elevation,
            modifier = Modifier.fillMaxSize(),
        ) {
            if (backImage != null) {
                AsyncImage(
                    model = File(backImage),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(corner),
                )
            } else {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(maxWidth * 0.07f)
                        .drawBehind {
                            val line = scheme.onPrimary.copy(alpha = 0.35f)
                            val step = size.width / 6
                            var x = -size.height
                            while (x < size.width) {
                                drawLine(line, Offset(x, 0f), Offset(x + size.height, size.height), strokeWidth = 1.dp.toPx())
                                drawLine(line, Offset(x + size.height, 0f), Offset(x, size.height), strokeWidth = 1.dp.toPx())
                                x += step
                            }
                            drawRect(scheme.onPrimary.copy(alpha = 0.8f), style = Stroke(1.5.dp.toPx()))
                        },
                )
            }
        }
    }
}

/** Adds the text-presentation selector after card-suit characters so they never render as emoji. */
internal fun textPresentation(text: String): String =
    text.replace(Regex("([\u2660-\u2667])(?!\uFE0E)"), "$1\uFE0E")
