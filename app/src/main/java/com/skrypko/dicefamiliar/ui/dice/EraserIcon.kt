package com.skrypko.dicefamiliar.ui.dice

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** Eraser icon (tilted eraser over a baseline), used for "Clear formula". Tinted by Icon. */
val EraserIcon: ImageVector by lazy {
    ImageVector.Builder(name = "Eraser", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        // Rubber tip, filled.
        .path(fill = SolidColor(Color.Black)) {
            moveTo(5.2f, 13.2f)
            lineTo(9.6f, 8.8f)
            lineTo(15.2f, 14.4f)
            lineTo(10.8f, 18.8f)
            close()
        }
        // Eraser outline, divider and the baseline it erases along.
        .path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            moveTo(14.6f, 3.8f)
            lineTo(20.2f, 9.4f)
            lineTo(10.8f, 18.8f)
            lineTo(5.2f, 13.2f)
            close()
            moveTo(10.8f, 18.8f)
            lineTo(20f, 18.8f)
        }
        .build()
}
