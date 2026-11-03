package com.skrypko.dicefamiliar.ui.dice

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.ui.zIndex
import androidx.compose.material3.MaterialTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.skrypko.dicefamiliar.ui.dice.skin.LocalDiceSkin
import com.skrypko.dicefamiliar.ui.dice.skin.colors

/**
 * A 2D die with [text] inside (the die name in the palette, the value in results).
 * The look comes from the active dice set: a designer image if provided, otherwise a drawn silhouette.
 */
@Composable
fun DiceIcon(
    sides: Int,
    text: String,
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    strikeThrough: Boolean = false,
    /** d100 only: labels for the tens and units kites (e.g. "70" and "3"); null shows [text] over both. */
    pairLabels: Pair<String, String>? = null,
    /** Which die type's style to use; defaults to [sides]. */
    skinSides: Int = sides,
) {
    val skin = LocalDiceSkin.current.forSides(skinSides)
    if (sides == 100 && skin.image2d == null) {
        PercentilePair(text, pairLabels, size, strikeThrough, modifier)
        return
    }
    val colors = skin.colors()
    val shape = DiceShapes.forSides(sides)
    val image = skin.image2d
    val base = modifier.size(size)
    val decorated = if (image != null) {
        base
    } else {
        base
            .clip(shape)
            .background(colors.fill)
            .drawBehind { drawFacets(sides, colors.edge, labelled = text.isNotEmpty()) }
            .border(skin.edgeWidth, colors.edge, shape)
    }
    Box(decorated, contentAlignment = Alignment.Center) {
        if (image != null) {
            Image(image, contentDescription = null, contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
        }
        val fontSize = when {
            text.length >= 4 -> (size.value * 0.22f).sp
            text.length == 3 -> (size.value * 0.27f).sp
            else -> (size.value * 0.32f).sp
        }
        Text(
            text = text,
            color = colors.text,
            fontSize = fontSize,
            fontWeight = FontWeight.Bold,
            fontFamily = skin.typeface?.let { FontFamily(it) },
            textAlign = TextAlign.Center,
            textDecoration = if (strikeThrough) TextDecoration.LineThrough else null,
            // Labels sit in each silhouette's safe area (see docs/dice-assets.md).
            modifier = when (sides) {
                4 -> Modifier.padding(top = size * 0.28f)
                10 -> Modifier.padding(bottom = size * 0.16f)
                // Only reached with a set image: the caption goes under the two kites, centred at 78%.
                100 -> Modifier.padding(top = size * 0.56f)
                else -> Modifier
            },
            maxLines = 1,
        )
    }
}

/**
 * d100 drawn as two smaller d10 kites side by side (tens die and units die). With [labels]
 * each kite shows its digit label; without, [text] is shown as a caption under the kites.
 */
@Composable
private fun PercentilePair(
    text: String,
    labels: Pair<String, String>?,
    size: Dp,
    strikeThrough: Boolean,
    modifier: Modifier,
) {
    val kite = size * 0.58f
    Column(
        modifier.size(width = size * 1.15f, height = size),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // On the palette button (no labels) one pill groups both kites into a single d100;
        // roll results show the two dice without a backdrop.
        Box(
            if (labels == null) {
                Modifier
                    .clip(RoundedCornerShape(50))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .padding(horizontal = size * 0.05f, vertical = size * 0.03f)
            } else {
                Modifier
            },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(-(kite * 0.3f))) {
                DiceIcon(
                    sides = 10,
                    text = labels?.first.orEmpty(),
                    size = kite,
                    strikeThrough = strikeThrough,
                    skinSides = 100,
                    modifier = Modifier.zIndex(1f),
                )
                DiceIcon(
                    sides = 10,
                    text = labels?.second.orEmpty(),
                    size = kite,
                    strikeThrough = strikeThrough,
                    skinSides = 10,
                    modifier = Modifier.padding(top = kite * 0.12f),
                )
            }
        }
        if (labels == null) {
            Text(
                text = text,
                color = LocalDiceSkin.current.forSides(100).colors().edge,
                fontSize = (size.value * 0.22f).sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
        }
    }
}

private fun androidx.compose.ui.graphics.drawscope.DrawScope.drawFacets(sides: Int, edge: Color, labelled: Boolean) {
    DiceShapes.facets(sides, size.width, size.height, labelled)?.let { path ->
        drawPath(path, color = edge.copy(alpha = 0.35f), style = Stroke(width = 1.5.dp.toPx()))
    }
}
