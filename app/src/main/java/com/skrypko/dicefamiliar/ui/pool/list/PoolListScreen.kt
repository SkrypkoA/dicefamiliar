package com.skrypko.dicefamiliar.ui.pool.list

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.skrypko.dicefamiliar.R
import com.skrypko.dicefamiliar.data.pool.PoolSummary
import com.skrypko.dicefamiliar.ui.common.HelpButton
import com.skrypko.dicefamiliar.ui.common.HelpSheet
import com.skrypko.dicefamiliar.ui.common.HelpTopic
import com.skrypko.dicefamiliar.ui.theme.DiceFamiliarTheme
import java.io.File

const val POOL_ROW_TAG = "pool_row"
const val POOL_ADD_TAG = "pool_add"

@Composable
fun PoolListScreen(
    onOpenPool: (PoolSummary) -> Unit,
    onEditPool: (Long) -> Unit,
    onNewPool: () -> Unit,
    viewModel: PoolListViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    PoolListContent(state, onOpenPool = onOpenPool, onEditPool = onEditPool, onNewPool = onNewPool, onDelete = viewModel::delete)
}

/** Every pool with its item count; tap opens Pick (or Edit when empty), the pencil opens Edit, long-press deletes. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PoolListContent(
    state: PoolListUiState,
    onOpenPool: (PoolSummary) -> Unit,
    onEditPool: (Long) -> Unit,
    onNewPool: () -> Unit,
    onDelete: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    var showHelp by rememberSaveable { mutableStateOf(false) }
    var pendingDelete by remember { mutableStateOf<PoolSummary?>(null) }
    Box(modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp),
            ) {
                Text(stringResource(R.string.title_pool), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                HelpButton(stringResource(R.string.pool_help), onClick = { showHelp = true })
            }
            if (state.loaded && state.pools.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    Text(
                        stringResource(R.string.pool_list_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            } else {
                LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 88.dp)) {
                    items(state.pools, key = { it.id }) { pool ->
                        val deleteLabel = stringResource(R.string.pool_delete)
                        ListItem(
                            headlineContent = { Text(pool.name, maxLines = 1) },
                            supportingContent = { Text(countText(pool)) },
                            leadingContent = { PoolCover(pool, state.imageDir) },
                            trailingContent = {
                                IconButton(onClick = { onEditPool(pool.id) }) {
                                    Icon(Icons.Outlined.Edit, contentDescription = stringResource(R.string.pool_edit, pool.name))
                                }
                            },
                            modifier = Modifier
                                .testTag(POOL_ROW_TAG)
                                .combinedClickable(onClick = { onOpenPool(pool) }, onLongClick = { pendingDelete = pool })
                                .semantics {
                                    customActions = listOf(CustomAccessibilityAction(deleteLabel) { pendingDelete = pool; true })
                                },
                        )
                    }
                }
            }
        }
        FloatingActionButton(
            onClick = onNewPool,
            modifier = Modifier.align(Alignment.BottomEnd).padding(16.dp).testTag(POOL_ADD_TAG),
        ) {
            Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.pool_new))
        }
    }
    pendingDelete?.let { pool ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            icon = { Icon(Icons.Outlined.Delete, contentDescription = null) },
            title = { Text(stringResource(R.string.pool_delete_title)) },
            text = { Text(stringResource(R.string.pool_delete_text, pool.name)) },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(pool.id)
                    pendingDelete = null
                }) { Text(stringResource(R.string.pool_delete_confirm)) }
            },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (showHelp) HelpSheet(R.string.pool_help_title, POOL_LIST_HELP, onDismiss = { showHelp = false })
}

@Composable
private fun countText(pool: PoolSummary): String {
    val items = pluralStringResource(R.plurals.pool_items, pool.itemCount, pool.itemCount)
    return if (pool.leftCount < pool.itemCount) pluralStringResource(R.plurals.pool_items_left, pool.leftCount, items, pool.leftCount) else items
}

@Composable
private fun PoolCover(pool: PoolSummary, imageDir: File) {
    val shape = RoundedCornerShape(10.dp)
    if (pool.cover != null) {
        AsyncImage(
            model = File(imageDir, pool.cover),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier.size(44.dp).clip(shape),
        )
    } else {
        Surface(shape = shape, color = MaterialTheme.colorScheme.secondaryContainer, modifier = Modifier.size(44.dp)) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    pool.name.trim().take(1).uppercase(),
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                )
            }
        }
    }
}

val POOL_LIST_HELP = listOf(
    HelpTopic(R.string.help_pool_list_title, R.string.help_pool_list_text),
    HelpTopic(R.string.help_pool_new_title, R.string.help_pool_new_text),
    HelpTopic(R.string.help_pool_delete_title, R.string.help_pool_delete_text),
)

@Preview(showBackground = true)
@Composable
private fun PoolListContentPreview() {
    DiceFamiliarTheme {
        PoolListContent(
            PoolListUiState(listOf(PoolSummary(1, "Drinks", 24, 24, null), PoolSummary(2, "Classes", 13, 10, null)), loaded = true),
            {}, {}, {}, {},
        )
    }
}
