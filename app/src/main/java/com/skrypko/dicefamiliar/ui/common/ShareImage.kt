package com.skrypko.dicefamiliar.ui.common

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.core.content.FileProvider
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext

/**
 * Renders [content] off screen (laid out at full width, never drawn on screen) and hands the picture
 * to [onCaptured] once. The short delay lets images (e.g. uploaded card art) finish loading first.
 */
@Composable
fun CaptureAsImage(onCaptured: (ImageBitmap) -> Unit, content: @Composable () -> Unit) {
    val layer = rememberGraphicsLayer()
    val done by rememberUpdatedState(onCaptured)
    Box(
        Modifier
            .fillMaxWidth()
            .wrapContentHeight(align = Alignment.Top, unbounded = true)
            .drawWithContent {
                // Record only; the layer is not drawn, so nothing appears on screen.
                layer.record { this@drawWithContent.drawContent() }
            },
    ) { content() }
    LaunchedEffect(Unit) {
        delay(CAPTURE_DELAY_MS)
        done(layer.toImageBitmap())
    }
}

/** Saves [image] as a PNG in the cache's shared folder and opens the share sheet for it. */
suspend fun shareImage(context: Context, image: ImageBitmap, fileName: String, chooserTitle: String) {
    val file = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        File(dir, fileName).also { out -> out.outputStream().use { image.asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it) } }
    }
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType("image/png")
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(fileName, uri)
    context.startActivity(Intent.createChooser(send, chooserTitle))
}

private const val CAPTURE_DELAY_MS = 350L


/** A file in the cache's shared folder, ready to be written and passed to [shareFile]. */
fun sharedCacheFile(context: Context, fileName: String): File =
    File(File(context.cacheDir, "shared").apply { mkdirs() }, fileName)

/** Opens the share sheet for [file] (from [sharedCacheFile]) through the app's FileProvider. */
fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType(mimeType)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    send.clipData = ClipData.newRawUri(file.name, uri)
    context.startActivity(Intent.createChooser(send, chooserTitle))
}
