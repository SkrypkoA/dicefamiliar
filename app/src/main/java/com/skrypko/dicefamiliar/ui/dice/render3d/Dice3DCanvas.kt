package com.skrypko.dicefamiliar.ui.dice.render3d

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.skrypko.dicefamiliar.ui.dice.skin.DiceSkin
import com.skrypko.dicefamiliar.ui.dice.skin.DieColors
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * One die to draw: its type, the value it shows, and whether it counts (kh/kl).
 * A d100 result is drawn as two d10s: [DieKind.PERCENTILE_TENS] (sides 100, value = tens digit)
 * and [DieKind.PERCENTILE_UNITS] (sides 10, value = units digit).
 */
data class Die3D(val sides: Int, val value: Int, val kept: Boolean = true, val kind: DieKind = DieKind.STANDARD) {
    companion object {
        /** Expands one rolled die into what is drawn (two dice for a d100); returned as one slot. */
        fun of(sides: Int, value: Int, kept: Boolean): List<Die3D> = if (sides == 100) {
            val (tens, units) = Percentile.split(value)
            listOf(
                Die3D(100, tens, kept, DieKind.PERCENTILE_TENS),
                Die3D(10, units, kept, DieKind.PERCENTILE_UNITS),
            )
        } else {
            listOf(Die3D(sides, value, kept))
        }
    }
}

/** Resolved colours per die type, computed by the caller inside the theme. */
typealias DieColorsFor = (sides: Int) -> DieColors

/**
 * Pseudo-3D dice drawn on a Canvas: shaded convex polyhedra with numbers warped onto faces.
 * [progress] runs 0..1 during the tumble; 1 means at rest showing each die's value.
 */
@Composable
fun Dice3DCanvas(
    /** One slot per rolled die; a d100 slot holds its tens and units d10s. */
    slots: List<List<Die3D>>,
    progress: Float,
    directionX: Float,
    directionY: Float,
    durationMs: Int,
    skin: DiceSkin,
    colorsFor: DieColorsFor,
    description: String,
    modifier: Modifier = Modifier,
    /** Slots drawn on a shared tint (a dice pool's selected group). */
    highlighted: Set<Int> = emptySet(),
    highlightColor: Color = Color.Transparent,
) {
    val paints = remember { DicePaints() }
    Canvas(modifier.semantics { contentDescription = description }) {
        val layout = gridLayout(slots.size, size.width, size.height, maxDie = 150.dp.toPx())
        val t = progress.toDouble()
        slots.forEachIndexed { index, group ->
            val (cx, cy) = layout.slots[index]
            val s = layout.dieSize
            if (index in highlighted) {
                val half = s * 0.62f
                drawRoundRect(
                    color = highlightColor,
                    topLeft = androidx.compose.ui.geometry.Offset(cx - half, cy - half),
                    size = androidx.compose.ui.geometry.Size(half * 2, half * 2),
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(half * 0.45f),
                )
            }
            fun draw(die: Die3D, x: Float, y: Float, dieSize: Float) = drawDie(
                die = die,
                index = index, // dice of one slot tumble together
                cx = x,
                cy = y,
                dieSize = dieSize,
                t = t,
                dirX = directionX.toDouble(),
                dirY = directionY.toDouble(),
                durationMs = durationMs,
                skin = skin,
                colors = colorsFor(die.sides),
                paints = paints,
            )
            if (group.size == 2) {
                // d100: two smaller d10s close together, moving as one (no backdrop).
                val pair = s * PAIR_SCALE
                draw(group[1], cx + s * 0.27f, cy + s * 0.05f, pair) // units die, behind
                draw(group[0], cx - s * 0.27f, cy - s * 0.03f, pair) // tens die, in front
            } else {
                group.forEach { draw(it, cx, cy, s) }
            }
        }
    }
}

private class DicePaints {
    val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val edge = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.LINEAR_TEXT_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        textAlign = Paint.Align.CENTER
    }
    val bitmap = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    val path = Path()
    val matrix = Matrix()
    val src = FloatArray(6)
    val dst = FloatArray(6)
}

private data class GridLayout(val dieSize: Float, val slots: List<Pair<Float, Float>>)

private fun gridLayout(count: Int, width: Float, height: Float, maxDie: Float): GridLayout {
    if (count == 0) return GridLayout(0f, emptyList())
    val cols = ceil(sqrt(count * width / height.coerceAtLeast(1f))).toInt().coerceIn(1, count)
    val rows = ceil(count / cols.toFloat()).toInt()
    val cell = min(width / cols, height / rows)
    val dieSize = min(cell * 0.78f, maxDie)
    val left = (width - cols * cell) / 2
    val top = (height - rows * cell) / 2
    val slots = List(count) { i ->
        val row = i / cols
        val inRow = if (row == rows - 1) count - row * cols else cols
        val rowLeft = left + (cols - inRow) * cell / 2
        Pair(rowLeft + (i % cols + 0.5f) * cell, top + (row + 0.5f) * cell)
    }
    return GridLayout(dieSize, slots)
}

private val LIGHT = Vec3(-0.35, 0.55, 0.75).normalized()

/** Fixed viewing angle (seen slightly from above-right) so resting dice show their depth. */
private val VIEW_TILT = Mat3.rotation(Vec3.X, -0.42) * Mat3.rotation(Vec3.Y, -0.36)
private const val PERSPECTIVE = 5.0
private const val PAIR_SCALE = 0.64f
private const val SPHERE_SCALE = 0.9f
private const val TEMPLATE_SCALE = 100f
private const val LABEL_MIN_FACING = 0.35

private fun DrawScope.drawDie(
    die: Die3D,
    index: Int,
    cx: Float,
    cy: Float,
    dieSize: Float,
    t: Double,
    dirX: Double,
    dirY: Double,
    durationMs: Int,
    skin: DiceSkin,
    colors: DieColors,
    paints: DicePaints,
) {
    val model = DieModel.forSides(if (die.kind == DieKind.STANDARD) die.sides else 10)
    val dieSkin = skin.forSides(die.sides)
    val restFace = model.restFace(die.value, die.kind)
    val rotation = VIEW_TILT * if (t >= 1.0) {
        model.restRotation(restFace)
    } else {
        DieAnimation.orientation(
            t,
            model.restRotation(restFace),
            DieAnimation.spinAxis(dirX, -dirY, index),
            DieAnimation.turns(durationMs, index),
        )
    }
    val eased = DieAnimation.ease(t)
    val travel = size.width * 0.6
    val x = cx - (dirX * travel * (1 - eased)).toFloat()
    val y = cy - (dirY * travel * (1 - eased)).toFloat() - (DieAnimation.bounce(t) * dieSize * 0.8).toFloat()
    val radius = dieSize / 2f
    val alpha = if (die.kept) 1f else 0.4f

    fun project(v: Vec3): Pair<Float, Float> {
        val p = rotation * v
        val k = PERSPECTIVE / (PERSPECTIVE - p.z)
        return Pair(x + (p.x * k * radius).toFloat(), y - (p.y * k * radius).toFloat())
    }

    val typeface = dieSkin.typeface ?: Typeface.DEFAULT_BOLD
    paints.edge.strokeWidth = dieSkin.edgeWidth.toPx()
    paints.edge.color = colors.edge.copy(alpha = colors.edge.alpha * alpha).toArgb()

    drawIntoCanvas { canvas ->
        val native = canvas.nativeCanvas
        if (model.isSphere) {
            drawSphere(native, model, rotation, restFace, die, x, y, radius, colors, alpha, typeface, paints, ::project)
            return@drawIntoCanvas
        }
        model.mesh.faces.forEachIndexed { f, face ->
            val normal = rotation * face.normal
            if (normal.z <= 0.01) return@forEachIndexed
            val projected = face.indices.map { project(model.mesh.vertices[it]) }
            paints.path.reset()
            projected.forEachIndexed { i, (px, py) ->
                if (i == 0) paints.path.moveTo(px, py) else paints.path.lineTo(px, py)
            }
            paints.path.close()

            val light = 0.55 + 0.45 * max(0.0, normal dot LIGHT)
            val shaded = lerp(Color.Black, colors.fill, light.toFloat())
            paints.fill.color = shaded.copy(alpha = colors.fill.alpha * alpha).toArgb()
            native.drawPath(paints.path, paints.fill)

            if (normal.z > LABEL_MIN_FACING) {
                val facing = ((normal.z - LABEL_MIN_FACING) / (1 - LABEL_MIN_FACING)).coerceIn(0.0, 1.0).toFloat()
                drawFaceContent(
                    native, model, f, projected, die, restFace, dieSkin.pips && die.sides == 6,
                    dieSkin.faceTexture3d?.asAndroidBitmap(), typeface, colors.text, alpha * facing, paints,
                    faceClip = paints.path,
                )
            }
            native.drawPath(paints.path, paints.edge)
        }
    }
}

/**
 * Custom dN: a shaded sphere. The hidden icosahedron only positions the rolled number, which
 * turns with the sphere and settles facing the viewer.
 */
private fun drawSphere(
    native: android.graphics.Canvas,
    model: DieModel,
    rotation: Mat3,
    restFace: Int,
    die: Die3D,
    x: Float,
    y: Float,
    radius: Float,
    colors: DieColors,
    alpha: Float,
    typeface: Typeface,
    paints: DicePaints,
    project: (Vec3) -> Pair<Float, Float>,
) {
    val r = radius * SPHERE_SCALE
    val fill = colors.fill.copy(alpha = colors.fill.alpha * alpha)
    paints.fill.shader = RadialGradient(
        x - r * 0.35f,
        y - r * 0.4f,
        r * 1.45f,
        intArrayOf(
            lerp(fill, Color.White, 0.55f).toArgb(),
            fill.toArgb(),
            lerp(Color.Black, fill, 0.55f).copy(alpha = fill.alpha).toArgb(),
        ),
        floatArrayOf(0f, 0.5f, 1f),
        Shader.TileMode.CLAMP,
    )
    native.drawCircle(x, y, r, paints.fill)
    paints.fill.shader = null
    native.drawCircle(x, y, r, paints.edge)

    val normal = rotation * model.mesh.faces[restFace].normal
    if (normal.z > LABEL_MIN_FACING) {
        val facing = ((normal.z - LABEL_MIN_FACING) / (1 - LABEL_MIN_FACING)).coerceIn(0.0, 1.0).toFloat()
        val projected = model.mesh.faces[restFace].indices.map { project(model.mesh.vertices[it]) }
        drawFaceContent(
            native, model, restFace, projected, die, restFace, pips = false, texture = null,
            typeface = typeface, textColor = colors.text, alpha = alpha * facing, paints = paints,
            faceClip = null,
        )
    }
}

/**
 * Draws texture and number (or pips) in the face's template space, warped onto the projected face.
 * The warp is affine (three corners), which is exact for triangles but not under perspective for
 * faces with more corners (e.g. the long d10 kites), so everything is also clipped to [faceClip],
 * the face as actually projected on screen.
 */
private fun drawFaceContent(
    native: android.graphics.Canvas,
    model: DieModel,
    faceIndex: Int,
    projected: List<Pair<Float, Float>>,
    die: Die3D,
    restFace: Int,
    pips: Boolean,
    texture: android.graphics.Bitmap?,
    typeface: Typeface,
    textColor: Color,
    alpha: Float,
    paints: DicePaints,
    faceClip: android.graphics.Path?,
) {
    val template = FaceTemplate.of(model, faceIndex)
    for (i in 0 until 3) {
        paints.src[i * 2] = template.points[i].first
        paints.src[i * 2 + 1] = template.points[i].second
        paints.dst[i * 2] = projected[i].first
        paints.dst[i * 2 + 1] = projected[i].second
    }
    if (!paints.matrix.setPolyToPoly(paints.src, 0, paints.dst, 0, 3)) return
    native.save()
    faceClip?.let { native.clipPath(it) }
    native.concat(paints.matrix)
    if (texture != null) {
        native.save()
        native.clipPath(template.path)
        val r = template.circumradius
        paints.bitmap.alpha = (255 * alpha).toInt()
        native.drawBitmap(texture, null, RectF(-r, -r, r, r), paints.bitmap)
        native.restore()
    }
    paints.text.color = textColor.copy(alpha = textColor.alpha * alpha).toArgb()
    val label = model.label(faceIndex, die.value, restFace, die.kind)
    if (pips) {
        val value = if (faceIndex == restFace) die.value else model.faceValues[faceIndex]
        drawPips(native, value, template.inradius, paints.text)
    } else if (label.isNotEmpty()) {
        paints.text.typeface = typeface
        val scale = when (label.length) {
            1 -> 1.15f
            2 -> 0.95f
            else -> 0.72f
        }
        paints.text.textSize = template.inradius * scale
        val metrics = paints.text.fontMetrics
        val baseline = -(metrics.ascent + metrics.descent) / 2
        native.drawText(label, 0f, baseline, paints.text)
        if (label == "6" || label == "9") {
            // Underline 6 and 9 so they can be told apart, like real dice.
            val w = paints.text.measureText(label) * 0.6f
            val yLine = baseline + template.inradius * 0.12f
            native.drawRect(-w / 2, yLine, w / 2, yLine + template.inradius * 0.07f, paints.text)
        }
    }
    native.restore()
}

private fun drawPips(native: android.graphics.Canvas, value: Int, inradius: Float, paint: Paint) {
    val d = inradius * 0.55f
    val r = inradius * 0.16f
    val positions = when (value) {
        1 -> listOf(0f to 0f)
        2 -> listOf(-d to -d, d to d)
        3 -> listOf(-d to -d, 0f to 0f, d to d)
        4 -> listOf(-d to -d, d to -d, -d to d, d to d)
        5 -> listOf(-d to -d, d to -d, 0f to 0f, -d to d, d to d)
        else -> listOf(-d to -d, d to -d, -d to 0f, d to 0f, -d to d, d to d)
    }
    positions.forEach { (px, py) -> native.drawCircle(px, py, r, paint) }
}

/**
 * A face laid out flat in "template space": centred on the face centroid, y pointing down,
 * the face's up direction toward -y, scaled so the mesh's unit radius is 100 units.
 * docs/dice-templates are generated from the same layout.
 */
internal class FaceTemplate private constructor(
    val points: List<Pair<Float, Float>>,
    val inradius: Float,
    val circumradius: Float,
    val path: Path,
) {
    companion object {
        private val cache = HashMap<Pair<Int, Int>, FaceTemplate>()

        fun of(model: DieModel, faceIndex: Int): FaceTemplate = synchronized(cache) {
            cache.getOrPut(model.sides to faceIndex) { build(model, faceIndex) }
        }

        private fun build(model: DieModel, faceIndex: Int): FaceTemplate {
            val face = model.mesh.faces[faceIndex]
            val up = model.faceUp[faceIndex]
            val right = up cross face.normal
            val pts = face.indices.map { i ->
                val v = model.mesh.vertices[i] - face.centroid
                Pair((v dot right).toFloat() * TEMPLATE_SCALE, -(v dot up).toFloat() * TEMPLATE_SCALE)
            }
            val circumradius = pts.maxOf { (a, b) -> sqrt(a * a + b * b) }
            val inradius = pts.indices.minOf { i ->
                val (x1, y1) = pts[i]
                val (x2, y2) = pts[(i + 1) % pts.size]
                val len = sqrt((x2 - x1) * (x2 - x1) + (y2 - y1) * (y2 - y1))
                kotlin.math.abs(x1 * y2 - x2 * y1) / len
            }
            val path = Path().apply {
                pts.forEachIndexed { i, (a, b) -> if (i == 0) moveTo(a, b) else lineTo(a, b) }
                close()
            }
            return FaceTemplate(pts, inradius, circumradius, path)
        }
    }
}
