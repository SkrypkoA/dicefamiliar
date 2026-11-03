package com.skrypko.dicefamiliar.ui.dice

import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** 2D silhouettes of physical dice. Points are fractions of the bounding box. */
object DiceShapes {
    private val D4 = polygon(0.5f to 0.04f, 0.97f to 0.92f, 0.03f to 0.92f)
    private val D6 = polygon(0.04f to 0.04f, 0.96f to 0.04f, 0.96f to 0.96f, 0.04f to 0.96f)
    private val D8 = polygon(0.5f to 0.02f, 0.98f to 0.5f, 0.5f to 0.98f, 0.02f to 0.5f)
    private val D10 = polygon(0.5f to 0.02f, 0.98f to 0.36f, 0.98f to 0.5f, 0.5f to 0.98f, 0.02f to 0.5f, 0.02f to 0.36f)
    private val D12 = regularPolygon(sides = 5, rotationDeg = -90f)
    private val D20 = regularPolygon(sides = 6, rotationDeg = -90f)

    fun forSides(sides: Int): Shape = when (sides) {
        4 -> D4
        6 -> D6
        8 -> D8
        10, 100 -> D10
        12 -> D12
        20 -> D20
        else -> CircleShape
    }

    /**
     * Facet lines drawn inside some dice so the silhouettes read as 3D solids (null = none).
     * [labelled] is false when nothing is written inside the die (the d100 palette kites): then a
     * d10 also gets the upper edges, which otherwise stay clear for the label.
     */
    fun facets(sides: Int, width: Float, height: Float, labelled: Boolean = true): Path? = when (sides) {
        20 -> Path().apply {
            val points = regularPoints(6, -90f).map { Offset(it.first * width, it.second * height) }
            moveTo(points[0].x, points[0].y)
            lineTo(points[2].x, points[2].y)
            lineTo(points[4].x, points[4].y)
            close()
        }
        10, 100 -> Path().apply {
            // A "V" from the shoulders to a lower vertex, plus the ridge down to the bottom point.
            moveTo(0.02f * width, 0.5f * height)
            lineTo(0.5f * width, 0.66f * height)
            lineTo(0.98f * width, 0.5f * height)
            moveTo(0.5f * width, 0.66f * height)
            lineTo(0.5f * width, 0.98f * height)
            if (!labelled) {
                // Two edges from the top point down to the "V", splitting the upper half into
                // three faces like a real d10.
                moveTo(0.26f * width, 0.58f * height)
                lineTo(0.5f * width, 0.02f * height)
                lineTo(0.74f * width, 0.58f * height)
            }
        }
        else -> null
    }

    private fun polygon(vararg points: Pair<Float, Float>): Shape = GenericShape { size, _ ->
        points.forEachIndexed { index, (x, y) ->
            if (index == 0) moveTo(x * size.width, y * size.height) else lineTo(x * size.width, y * size.height)
        }
        close()
    }

    private fun regularPolygon(sides: Int, rotationDeg: Float): Shape =
        polygon(*regularPoints(sides, rotationDeg).toTypedArray())

    private fun regularPoints(sides: Int, rotationDeg: Float): List<Pair<Float, Float>> = List(sides) { i ->
        val angle = (rotationDeg + 360f * i / sides) * PI / 180.0
        (0.5f + 0.48f * cos(angle).toFloat()) to (0.5f + 0.48f * sin(angle).toFloat())
    }
}
