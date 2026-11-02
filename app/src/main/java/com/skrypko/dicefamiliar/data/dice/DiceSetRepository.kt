package com.skrypko.dicefamiliar.data.dice

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.util.Log
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import com.skrypko.dicefamiliar.domain.dice.skin.DiceSetFormat
import com.skrypko.dicefamiliar.domain.dice.skin.DiceSetParseResult
import com.skrypko.dicefamiliar.domain.dice.skin.DiceSetValidator
import com.skrypko.dicefamiliar.domain.dice.skin.DieStyleSpec
import com.skrypko.dicefamiliar.ui.dice.skin.DiceSkin
import com.skrypko.dicefamiliar.ui.dice.skin.DieSkin
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/** Provides the active dice set. Only the bundled `default` set exists for now. */
interface DiceSetRepository {
    suspend fun activeSkin(): DiceSkin
}

@Singleton
class AssetDiceSetRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) : DiceSetRepository {
    private val mutex = Mutex()
    private var cached: DiceSkin? = null

    override suspend fun activeSkin(): DiceSkin = mutex.withLock {
        cached ?: withContext(Dispatchers.IO) { load(DEFAULT_SET) }.also { cached = it }
    }

    /** Loads `assets/dice_sets/<id>`; anything missing or invalid falls back to the built-in look. */
    private fun load(id: String): DiceSkin {
        val dir = "$ROOT/$id"
        val text = try {
            context.assets.open("$dir/${DiceSetFormat.MANIFEST}").bufferedReader().use { it.readText() }
        } catch (e: IOException) {
            Log.w(TAG, "No manifest for dice set '$id'", e)
            return DiceSkin.Default
        }
        val parsed = when (val result = DiceSetValidator.parse(text)) {
            is DiceSetParseResult.Failed -> {
                Log.w(TAG, "Dice set '$id': ${result.issue}")
                return DiceSkin.Default
            }
            is DiceSetParseResult.Parsed -> result
        }
        val badPaths = parsed.issues.map { it.path }.toSet()
        parsed.issues.forEach { Log.w(TAG, "Dice set '$id': ${it.path}: ${it.message}") }

        // image2d is per die only; a silhouette image never applies to every die type.
        val base = resolve(dir, "style", parsed.manifest.style, DieSkin(), badPaths).copy(image2d = null)
        val perDie = parsed.manifest.dice
            .filterKeys { it in DiceSetFormat.DIE_KEYS }
            .mapValues { (key, spec) -> resolve(dir, "dice.$key", spec, base, badPaths) }
        return DiceSkin(parsed.manifest.id, parsed.manifest.name, base, perDie)
    }

    private fun resolve(dir: String, path: String, spec: DieStyleSpec, parent: DieSkin, bad: Set<String>): DieSkin {
        fun ok(field: String) = "$path.$field" !in bad
        return DieSkin(
            fill = if (ok("fill")) color(spec.fill, parent.fill) else parent.fill,
            edge = if (ok("edge")) color(spec.edge, parent.edge) else parent.edge,
            text = if (ok("text")) color(spec.text, parent.text) else parent.text,
            edgeWidth = spec.edgeWidth?.takeIf { ok("edgeWidth") }?.dp ?: parent.edgeWidth,
            typeface = spec.font?.takeIf { ok("font") }?.let { font(dir, it) } ?: parent.typeface,
            image2d = spec.image2d?.takeIf { ok("image2d") }?.let { image(dir, it) },
            faceTexture3d = spec.faceTexture3d?.takeIf { ok("faceTexture3d") }?.let { image(dir, it) }
                ?: parent.faceTexture3d,
            pips = spec.pips ?: parent.pips,
        )
    }

    /** Omitted inherits [parent]; `theme` gives null (Material colours); otherwise the parsed colour. */
    private fun color(value: String?, parent: Color?): Color? {
        if (value == null) return parent
        if (value == DiceSetFormat.THEME) return null
        val hex = value.removePrefix("#")
        val argb = if (hex.length == 6) "FF$hex" else hex
        return Color(argb.toLong(16).toInt())
    }

    private fun image(dir: String, path: String): ImageBitmap? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.assets.open("$dir/$path").use { BitmapFactory.decodeStream(it, null, bounds) }
        var sample = 1
        while (maxOf(bounds.outWidth, bounds.outHeight) / sample > MAX_IMAGE_PX) sample *= 2
        val options = BitmapFactory.Options().apply { inSampleSize = sample }
        context.assets.open("$dir/$path").use { BitmapFactory.decodeStream(it, null, options) }?.asImageBitmap()
    } catch (e: IOException) {
        Log.w(TAG, "Missing image $dir/$path", e)
        null
    }

    private fun font(dir: String, path: String): Typeface? = try {
        Typeface.createFromAsset(context.assets, "$dir/$path")
    } catch (e: RuntimeException) {
        Log.w(TAG, "Bad font $dir/$path", e)
        null
    }

    private companion object {
        const val TAG = "DiceSets"
        const val ROOT = "dice_sets"
        const val DEFAULT_SET = "default"
        const val MAX_IMAGE_PX = 1024
    }
}
