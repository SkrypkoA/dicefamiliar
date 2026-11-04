package com.skrypko.dicefamiliar.ui.lan

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.outlined.Link
import androidx.compose.material.icons.outlined.VpnLock
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skrypko.dicefamiliar.R
import com.skrypko.dicefamiliar.data.lan.LanPlayer
import com.skrypko.dicefamiliar.data.lan.LanRole
import com.skrypko.dicefamiliar.data.lan.transport.LanHostInfo
import com.skrypko.dicefamiliar.domain.lan.LanRollEntry
import com.skrypko.dicefamiliar.domain.lan.LanRollState
import com.skrypko.dicefamiliar.ui.common.HelpButton
import com.skrypko.dicefamiliar.ui.common.HelpSheet
import com.skrypko.dicefamiliar.ui.common.HelpTopic
import java.text.DateFormat
import java.util.Date

const val LAN_HOST_BUTTON_TAG = "lan_host_button"
const val LAN_HOST_ROW_TAG = "lan_host_row"
const val LAN_PLAYER_ROW_TAG = "lan_player_row"
const val LAN_REQUEST_CARD_TAG = "lan_request_card"

@Composable
fun LanScreen(onOpenPlayer: (String) -> Unit, onOpenRequest: () -> Unit, viewModel: LanViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val resources = LocalResources.current
    val error = state.session.error
    LaunchedEffect(error) {
        if (error != null) {
            snackbar.showSnackbar(resources.getString(LanViewModel.errorText(error)))
            viewModel.clearError()
        }
    }
    Box(Modifier.fillMaxSize()) {
        LanContent(
            state = state,
            actions = LanActions(
                onHost = viewModel::host,
                onJoin = viewModel::join,
                onJoinAddress = viewModel::joinAddress,
                onLeave = viewModel::leave,
                onKick = viewModel::kick,
                onOpenPlayer = onOpenPlayer,
                onOpenRequest = onOpenRequest,
            ),
        )
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

data class LanActions(
    val onHost: () -> Unit = {},
    val onJoin: (LanHostInfo) -> Unit = {},
    val onJoinAddress: (String) -> Boolean = { true },
    val onLeave: () -> Unit = {},
    val onKick: (String) -> Unit = {},
    val onOpenPlayer: (String) -> Unit = {},
    val onOpenRequest: () -> Unit = {},
)

/** The LAN tab: host a table, join one (found on the network or by address), the players and the roll logs. */
@Composable
fun LanContent(state: LanUiState, actions: LanActions, modifier: Modifier = Modifier) {
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val session = state.session
    Column(modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp)) {
            Text(stringResource(R.string.lan_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            HelpButton(stringResource(R.string.lan_help), onClick = { showHelp = true })
        }
        when (session.role) {
            LanRole.NONE -> NoSession(state, actions)
            LanRole.HOST -> Hosting(state, actions)
            LanRole.PLAYER -> Playing(state, actions)
        }
    }
    if (showHelp) HelpSheet(R.string.lan_title, LAN_HELP, onDismiss = { showHelp = false })
}

@Composable
private fun NoSession(state: LanUiState, actions: LanActions) {
    var joinByAddress by rememberSaveable { mutableStateOf(false) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        item {
            Button(onClick = actions.onHost, modifier = Modifier.fillMaxWidth().height(56.dp).testTag(LAN_HOST_BUTTON_TAG)) {
                Text(stringResource(R.string.lan_host), style = MaterialTheme.typography.titleMedium)
            }
            Text(
                stringResource(R.string.lan_host_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp, bottom = 16.dp),
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.lan_tables_nearby), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                if (state.session.joining) CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
            }
        }
        if (state.hosts.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.lan_no_tables),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
        items(state.hosts, key = { it.id }) { host ->
            ListItem(
                headlineContent = { Text(host.name) },
                supportingContent = { Text(stringResource(R.string.lan_tap_to_join)) },
                leadingContent = { Icon(Icons.Filled.Wifi, contentDescription = null) },
                modifier = Modifier.testTag(LAN_HOST_ROW_TAG).combinedClickable(enabled = !state.session.joining, onClick = { actions.onJoin(host) }),
            )
        }
        item {
            OutlinedButton(onClick = { joinByAddress = true }, modifier = Modifier.padding(top = 8.dp)) {
                Icon(Icons.Outlined.Link, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text(stringResource(R.string.lan_join_by_address))
            }
        }
    }
    if (joinByAddress) JoinByAddressDialog(onJoin = actions.onJoinAddress, onDismiss = { joinByAddress = false })
}

@Composable
private fun JoinByAddressDialog(onJoin: (String) -> Boolean, onDismiss: () -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    var invalid by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.lan_join_by_address)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.lan_join_by_address_hint), style = MaterialTheme.typography.bodyMedium)
                OutlinedTextField(
                    value = text,
                    onValueChange = {
                        text = it
                        invalid = false
                    },
                    label = { Text(stringResource(R.string.lan_address)) },
                    placeholder = { Text("192.168.1.20") },
                    isError = invalid,
                    supportingText = if (invalid) ({ Text(stringResource(R.string.lan_address_invalid)) }) else null,
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(onClick = { if (onJoin(text)) onDismiss() else invalid = true }, enabled = text.isNotBlank()) {
                Text(stringResource(R.string.lan_join))
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Hosting(state: LanUiState, actions: LanActions) {
    val session = state.session
    var kicking by remember { mutableStateOf<LanPlayer?>(null) }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.lan_your_table, session.me?.name.orEmpty()),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = actions.onLeave) { Text(stringResource(R.string.lan_stop_hosting)) }
            }
            if (state.addresses.isNotEmpty()) {
                Text(
                    stringResource(R.string.lan_your_addresses),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 8.dp),
                )
                state.addresses.forEach { local ->
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(vertical = 2.dp)) {
                        Icon(if (local.vpn) Icons.Outlined.VpnLock else Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(local.address.toString(), fontFamily = FontFamily.Monospace, style = MaterialTheme.typography.bodyMedium)
                        if (local.vpn) {
                            Text(" · " + stringResource(R.string.lan_vpn), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(stringResource(R.string.lan_players), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            if (session.players.isEmpty()) {
                Text(
                    stringResource(R.string.lan_no_players),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
        items(session.players, key = { it.participant.id }) { player ->
            val removeLabel = stringResource(R.string.lan_remove_player, player.participant.name)
            ListItem(
                headlineContent = { Text(player.participant.name) },
                supportingContent = { Text(session.log(player.participant.id).firstOrNull()?.let { entrySummary(it) } ?: stringResource(R.string.lan_tap_to_request)) },
                leadingContent = { ConnectionDot(player.connected) },
                modifier = Modifier
                    .testTag(LAN_PLAYER_ROW_TAG)
                    .combinedClickable(
                        enabled = player.connected,
                        onClick = { actions.onOpenPlayer(player.participant.id) },
                        onLongClick = { kicking = player },
                    )
                    .semantics { customActions = listOf(CustomAccessibilityAction(removeLabel) { kicking = player; true }) },
            )
        }
    }
    kicking?.let { player ->
        AlertDialog(
            onDismissRequest = { kicking = null },
            title = { Text(stringResource(R.string.lan_remove_title)) },
            text = { Text(stringResource(R.string.lan_remove_text, player.participant.name)) },
            confirmButton = {
                TextButton(onClick = {
                    actions.onKick(player.participant.id)
                    kicking = null
                }) { Text(stringResource(R.string.lan_remove), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { kicking = null }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
}

@Composable
private fun Playing(state: LanUiState, actions: LanActions) {
    val session = state.session
    val me = session.me
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    stringResource(R.string.lan_session_playing, session.host?.name.orEmpty()),
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                OutlinedButton(onClick = actions.onLeave) { Text(stringResource(R.string.lan_leave)) }
            }
            if (session.incoming != null) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth().padding(top = 12.dp).testTag(LAN_REQUEST_CARD_TAG),
                    onClick = actions.onOpenRequest,
                ) {
                    Text(
                        stringResource(R.string.lan_request_waiting, session.host?.name.orEmpty()),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            HorizontalDivider(Modifier.padding(vertical = 12.dp))
            Text(stringResource(R.string.lan_my_rolls), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
        }
        val log = me?.let { session.log(it.id) }.orEmpty()
        if (log.isEmpty()) {
            item {
                Text(
                    stringResource(R.string.lan_no_rolls),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(vertical = 8.dp),
                )
            }
        }
        items(log, key = { it.requestId }) { entry -> LogRow(entry) }
    }
}

@Composable
private fun LogRow(entry: LanRollEntry) {
    ListItem(
        headlineContent = { Text(entrySummary(entry)) },
        supportingContent = { Text(DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(entry.requestedAt))) },
    )
}

@Composable
private fun ConnectionDot(connected: Boolean) {
    Box(
        Modifier
            .size(12.dp)
            .background(if (connected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant, CircleShape),
    )
}

/** "3d6+2 = 11", "10d6 (pool): 6 6 5 …", "1d20 · waiting", "2d6 · declined", … */
@Composable
fun entrySummary(entry: LanRollEntry): String {
    val dice = if (entry.pool) stringResource(R.string.lan_request_pool, entry.notation) else entry.notation
    val outcome = entry.outcome
    return when (entry.state) {
        LanRollState.ROLLED -> when {
            outcome == null -> dice
            entry.pool -> "$dice: " + outcome.groups.flatMap { it.dice }.map { it.value }.sortedDescending().joinToString(" ")
            else -> "$dice = ${outcome.total}"
        }
        LanRollState.REQUESTED -> stringResource(R.string.lan_entry_waiting, dice)
        LanRollState.DECLINED -> stringResource(R.string.lan_entry_declined, dice)
        LanRollState.CANCELLED -> stringResource(R.string.lan_entry_cancelled, dice)
        LanRollState.FAILED -> stringResource(R.string.lan_entry_failed, dice)
        LanRollState.DISCONNECTED -> stringResource(R.string.lan_entry_disconnected, dice)
    }
}

val LAN_HELP = listOf(
    HelpTopic(R.string.help_lan_what_title, R.string.help_lan_what_text),
    HelpTopic(R.string.help_lan_host_title, R.string.help_lan_host_text),
    HelpTopic(R.string.help_lan_join_title, R.string.help_lan_join_text),
    HelpTopic(R.string.help_lan_request_title, R.string.help_lan_request_text),
    HelpTopic(R.string.help_lan_roll_title, R.string.help_lan_roll_text),
    HelpTopic(R.string.help_lan_notifications_title, R.string.help_lan_notifications_text),
    HelpTopic(R.string.help_lan_vpn_title, R.string.help_lan_vpn_text),
    HelpTopic(R.string.help_lan_trouble_title, R.string.help_lan_trouble_text),
)
