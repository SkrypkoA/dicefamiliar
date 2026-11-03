package com.skrypko.dicefamiliar.ui.dice.skin

import com.skrypko.dicefamiliar.ui.theme.themeDiceColors
import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import com.skrypko.dicefamiliar.domain.dice.skin.DiceSetFormat

/** Resolved look of one die type. Null colours mean "use the Material theme". */
@Immutable
data class DieSkin(
    val fill: Color? = null,
    val edge: Color? = null,
    val text: Color? = null,
    val edgeWidth: Dp = 2.dp,
    val typeface: Typeface? = null,
    val image2d: ImageBitmap? = null,
    val faceTexture3d: ImageBitmap? = null,
    val pips: Boolean = false,
)

/** A loaded dice set, ready for drawing. */
@Immutable
data class DiceSkin(val id: String, val name: String, val base: DieSkin, val perDie: Map<String, DieSkin>) {
    fun forSides(sides: Int): DieSkin = perDie[DiceSetFormat.keyFor(sides)] ?: base

    companion object {
        val Default = DiceSkin("default", "Default", DieSkin(), emptyMap())
    }
}

/** Colours after resolving `theme` against the current Material colour scheme. */
data class DieColors(val fill: Color, val edge: Color, val text: Color)

@Composable
fun DieSkin.colors(): DieColors {
    val defaults = themeDiceColors()
    return DieColors(
        fill = fill ?: defaults.fill,
        edge = edge ?: defaults.edge,
        text = text ?: defaults.text,
    )
}

val LocalDiceSkin = staticCompositionLocalOf { DiceSkin.Default }
