package com.skrypko.dicefamiliar.ui.common

import android.content.ClipData
import android.os.Build
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.Clipboard
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.platform.toClipEntry
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.skrypko.dicefamiliar.R

const val FEATURE_PANEL_TAG = "feature_panel"
const val FEATURE_CHEVRON_TAG = "feature_chevron"

/**
 * The collapsible panel at the top of every feature screen (inputs, options, navigation). It sits above a
 * [FeatureHandleBar], whose chevron shows or hides it; the visibility is persisted by each feature.
 */
@Composable
fun FeaturePanel(visible: Boolean, animate: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    if (animate) {
        AnimatedVisibility(visible = visible, enter = expandVertically(), exit = shrinkVertically(), modifier = modifier.testTag(FEATURE_PANEL_TAG)) {
            content()
        }
    } else if (visible) {
        Box(modifier.testTag(FEATURE_PANEL_TAG)) { content() }
    }
}

/** What the share menu on the left of [FeatureHandleBar] offers; `enabled` is false while there is nothing to share. */
data class FeatureShare(
    val description: String,
    val onCopy: () -> Unit,
    val onShareText: () -> Unit,
    val onShareImage: () -> Unit,
    val enabled: Boolean,
)

/** The history button next to the share menu; `onClick` is `null` (disabled) while there is no history. */
data class FeatureHistory(val description: String, val onClick: (() -> Unit)?)

/**
 * The row under every feature's [FeaturePanel], always visible and laid out the same in every feature:
 * share menu (Copy · Share as text · Share as image) and history on the left, the chevron in the centre,
 * the "?" help on the right.
 */
@Composable
fun FeatureHandleBar(
    panelVisible: Boolean,
    onTogglePanel: () -> Unit,
    hideDescription: String,
    showDescription: String,
    helpDescription: String,
    onHelp: () -> Unit,
    modifier: Modifier = Modifier,
    share: FeatureShare? = null,
    history: FeatureHistory? = null,
) {
    Box(modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Row(Modifier.align(Alignment.CenterStart), verticalAlignment = Alignment.CenterVertically) {
            share?.let {
                ShareMenuButton(
                    contentDescription = it.description,
                    onShareText = it.onShareText,
                    onShareImage = it.onShareImage,
                    onCopy = it.onCopy,
                    enabled = it.enabled,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            history?.let {
                IconButton(onClick = { it.onClick?.invoke() }, enabled = it.onClick != null) {
                    Icon(Icons.Filled.History, contentDescription = it.description, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        IconButton(onClick = onTogglePanel, modifier = Modifier.size(width = 72.dp, height = 40.dp).testTag(FEATURE_CHEVRON_TAG)) {
            Icon(
                if (panelVisible) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                contentDescription = if (panelVisible) hideDescription else showDescription,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp),
            )
        }
        HelpButton(
            contentDescription = helpDescription,
            onClick = onHelp,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.align(Alignment.CenterEnd),
        )
    }
}

/**
 * Puts [text] on the clipboard. Returns `true` when the caller should confirm with a snackbar
 * (Android 13+ shows its own confirmation).
 */
suspend fun copyText(clipboard: Clipboard, label: String, text: String): Boolean {
    clipboard.setClipEntry(ClipData.newPlainText(label, text).toClipEntry())
    return Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU
}
