package com.skrypko.dicefamiliar.ui.common

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.TextSnippet
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Image
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import com.skrypko.dicefamiliar.R

/**
 * The share icon used by every feature: it opens a menu with "Copy" (when [onCopy] is given),
 * "Share as text" and "Share as image". It lives on the left of [FeatureHandleBar].
 * For the image, render the result with [CaptureAsImage] and send it with [shareImage].
 */
@Composable
fun ShareMenuButton(
    contentDescription: String,
    onShareText: () -> Unit,
    onShareImage: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    tint: Color = Color.Unspecified,
    onCopy: (() -> Unit)? = null,
) {
    var open by remember { mutableStateOf(false) }
    Box(modifier) {
        IconButton(onClick = { open = true }, enabled = enabled) {
            Icon(
                Icons.Outlined.Share,
                contentDescription = contentDescription,
                tint = if (tint == Color.Unspecified || !enabled) LocalContentColor.current else tint,
            )
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            if (onCopy != null) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.share_copy)) },
                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, contentDescription = null) },
                    onClick = {
                        open = false
                        onCopy()
                    },
                )
            }
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share_as_text)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Outlined.TextSnippet, contentDescription = null) },
                onClick = {
                    open = false
                    onShareText()
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.share_as_image)) },
                leadingIcon = { Icon(Icons.Outlined.Image, contentDescription = null) },
                onClick = {
                    open = false
                    onShareImage()
                },
            )
        }
    }
}
