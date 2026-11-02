package com.skrypko.dicefamiliar.ui.dice

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** "ƒx" formula icon: an italic f followed by a small x. Tinted by Icon like any material icon. */
val FormulaIcon: ImageVector by lazy {
    ImageVector.Builder(name = "Formula", defaultWidth = 24.dp, defaultHeight = 24.dp, viewportWidth = 24f, viewportHeight = 24f)
        .path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 2f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round,
        ) {
            // italic f
            moveTo(13.5f, 4.5f)
            curveTo(11.5f, 3.5f, 10.2f, 5f, 9.8f, 7.5f)
            lineTo(8.2f, 17.5f)
            curveTo(7.8f, 20f, 6.2f, 20.8f, 4.5f, 19.8f)
            moveTo(6.5f, 9.5f)
            lineTo(12.5f, 9.5f)
            // x
            moveTo(14.5f, 12.5f)
            lineTo(19.5f, 18.5f)
            moveTo(19.5f, 12.5f)
            lineTo(14.5f, 18.5f)
        }
        .build()
}
