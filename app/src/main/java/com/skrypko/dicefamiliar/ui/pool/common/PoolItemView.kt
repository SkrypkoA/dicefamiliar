package com.skrypko.dicefamiliar.ui.pool.common

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import com.skrypko.dicefamiliar.domain.pool.ItemColors
import com.skrypko.dicefamiliar.domain.pool.PoolDisplayMode
import com.skrypko.dicefamiliar.domain.pool.PoolItem
import java.io.File

const val POOL_ITEM_IMAGE_TAG = "pool_item_image"
const val POOL_ITEM_PLACEHOLDER_TAG = "pool_item_placeholder"
const val POOL_ITEM_LABEL_TAG = "pool_item_label"

/** The item's colour as a Compose colour, or `null`. */
fun PoolItem.color(): Color? = colorArgb?.let { Color(it) }

/** Black or white, whichever reads better on [background]. */
fun contentColorOn(argb: Int): Color =
    if (ItemColors.contrastText(argb) == ItemColors.TextTone.BLACK) Color(0xFF111111) else Color.White

/**
 * One item as the pool's [mode] dictates (the mode is worked out for the whole pool):
 * - [PoolDisplayMode.NAMES_ONLY]: the name label only;
 * - [PoolDisplayMode.THUMBNAIL_AND_NAME]: a thumbnail (or a neutral placeholder) above the name label;
 * - [PoolDisplayMode.IMAGES_ONLY]: the image only, the name kept as its content description.
 * The thumbnail border and the label background use the item's colour.
 */
@Composable
fun PoolItemTile(
    item: PoolItem,
    mode: PoolDisplayMode,
    imageDir: File,
    modifier: Modifier = Modifier,
    imageSize: Dp = 72.dp,
    labelStyle: TextStyle = MaterialTheme.typography.labelLarge,
) {
    Column(
        modifier.widthIn(max = maxOf(imageSize, 72.dp) + 16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (mode != PoolDisplayMode.NAMES_ONLY) {
            ItemThumbnail(item, imageDir, imageSize, describe = mode == PoolDisplayMode.IMAGES_ONLY)
        }
        if (mode != PoolDisplayMode.IMAGES_ONLY) {
            ItemNameLabel(item.name, item.colorArgb, style = labelStyle)
        }
    }
}

/** The item's image in a frame of its colour; a placeholder when it has none. */
@Composable
fun ItemThumbnail(item: PoolItem, imageDir: File, size: Dp, modifier: Modifier = Modifier, describe: Boolean = false) {
    val shape = RoundedCornerShape(size * 0.14f)
    val border = item.color() ?: MaterialTheme.colorScheme.outlineVariant
    val frame = modifier
        .size(size)
        .clip(shape)
        .border(if (item.colorArgb != null) 3.dp else 1.dp, border, shape)
    val path = item.imagePath
    if (path != null) {
        AsyncImage(
            model = File(imageDir, path),
            contentDescription = if (describe) item.name else null,
            contentScale = ContentScale.Crop,
            modifier = frame.padding(if (item.colorArgb != null) 3.dp else 1.dp).clip(shape).semantics {
                if (describe) contentDescription = item.name
            }.testTag(POOL_ITEM_IMAGE_TAG),
        )
    } else {
        Box(
            frame.background(MaterialTheme.colorScheme.surfaceVariant).testTag(POOL_ITEM_PLACEHOLDER_TAG),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Outlined.Image,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(size * 0.45f),
            )
        }
    }
}

/** The name on the item's colour (contrasting text), or on a neutral chip when it has no colour. */
@Composable
fun ItemNameLabel(
    name: String,
    colorArgb: Int?,
    modifier: Modifier = Modifier,
    style: TextStyle = MaterialTheme.typography.labelLarge,
    maxLines: Int = 2,
) {
    val background = colorArgb?.let { Color(it) } ?: MaterialTheme.colorScheme.secondaryContainer
    val content = colorArgb?.let { contentColorOn(it) } ?: MaterialTheme.colorScheme.onSecondaryContainer
    Surface(shape = RoundedCornerShape(8.dp), color = background, contentColor = content, modifier = modifier.testTag(POOL_ITEM_LABEL_TAG)) {
        Text(
            name,
            style = style,
            textAlign = TextAlign.Center,
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
        )
    }
}
