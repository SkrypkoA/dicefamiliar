package com.skrypko.dicefamiliar.ui.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.skrypko.dicefamiliar.R
import com.skrypko.dicefamiliar.data.lan.LanNotifier
import com.skrypko.dicefamiliar.data.lan.LanSessionManager
import com.skrypko.dicefamiliar.data.lan.transport.LanTransports
import com.skrypko.dicefamiliar.data.remote.ParticipantRepository
import com.skrypko.dicefamiliar.data.settings.AppSettingsRepository
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import com.skrypko.dicefamiliar.domain.settings.LanNotifications
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

const val LAN_SWITCH_TAG = "settings_lan_switch"
const val LAN_NAME_FIELD_TAG = "settings_lan_name"

@HiltViewModel
class LanSettingsViewModel @Inject constructor(
    private val settings: AppSettingsRepository,
    private val participants: ParticipantRepository,
    private val session: LanSessionManager,
    private val notifier: LanNotifier,
    transports: LanTransports,
) : ViewModel() {
    val appSettings: StateFlow<AppSettings> = settings.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AppSettings())

    /** Transport ids this build ships; the picker shows only with more than one. */
    val transportIds: List<String> = transports.all.keys.sorted()

    private val _name = MutableStateFlow("")
    val name: StateFlow<String> = _name

    init {
        viewModelScope.launch { _name.value = participants.self().name }
    }

    fun setEnabled(enabled: Boolean) = viewModelScope.launch {
        if (!enabled) session.leave()
        settings.setLanEnabled(enabled)
    }

    fun onNameChange(name: String) {
        _name.value = name.take(MAX_NAME)
    }

    fun saveName() = viewModelScope.launch {
        participants.rename(_name.value)
        _name.value = participants.self().name
    }

    fun setNotifications(mode: LanNotifications) = viewModelScope.launch { settings.setLanNotifications(mode) }

    fun setTransport(id: String) = viewModelScope.launch { settings.setLanTransport(id) }

    fun systemSettingsIntent(mode: LanNotifications) = notifier.systemSettingsIntent(mode)

    private companion object {
        const val MAX_NAME = 40
    }
}

/** Settings → LAN play: the switch that adds the LAN tab, this device's name and how requests are announced. */
@Composable
fun LanSection(viewModel: LanSettingsViewModel = hiltViewModel()) {
    val settings by viewModel.appSettings.collectAsStateWithLifecycle()
    val name by viewModel.name.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LanSectionContent(
        settings = settings,
        name = name,
        transportIds = viewModel.transportIds,
        onEnabled = { enabled ->
            viewModel.setEnabled(enabled)
            if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) askPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        },
        onNameChange = viewModel::onNameChange,
        onNameDone = { viewModel.saveName() },
        onNotifications = viewModel::setNotifications,
        onSystemSettings = { context.startActivity(viewModel.systemSettingsIntent(settings.lanNotifications)) },
        onTransport = viewModel::setTransport,
    )
}

@Composable
fun LanSectionContent(
    settings: AppSettings,
    name: String,
    transportIds: List<String>,
    onEnabled: (Boolean) -> Unit,
    onNameChange: (String) -> Unit,
    onNameDone: () -> Unit,
    onNotifications: (LanNotifications) -> Unit,
    onSystemSettings: () -> Unit,
    onTransport: (String) -> Unit,
) {
    ListItem(
        headlineContent = { Text(stringResource(R.string.settings_lan)) },
        supportingContent = { Text(stringResource(R.string.settings_lan_summary)) },
        trailingContent = { Switch(checked = settings.lanEnabled, onCheckedChange = null) },
        modifier = Modifier.testTag(LAN_SWITCH_TAG).toggleable(value = settings.lanEnabled, role = Role.Switch, onValueChange = onEnabled),
    )
    if (!settings.lanEnabled) return
    var editing by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.padding(horizontal = 16.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = {
                editing = true
                onNameChange(it)
            },
            label = { Text(stringResource(R.string.settings_lan_name)) },
            supportingText = { Text(stringResource(R.string.settings_lan_name_hint)) },
            singleLine = true,
            trailingIcon = {
                if (editing) {
                    TextButton(onClick = {
                        editing = false
                        onNameDone()
                    }, enabled = name.isNotBlank()) { Text(stringResource(R.string.settings_lan_name_save)) }
                }
            },
            modifier = Modifier.fillMaxWidth().testTag(LAN_NAME_FIELD_TAG),
        )
        Text(
            stringResource(R.string.settings_lan_notifications),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(top = 12.dp, bottom = 4.dp),
        )
        listOf(
            LanNotifications.SOUND_AND_VIBRATION to R.string.settings_lan_notify_sound,
            LanNotifications.VIBRATION_ONLY to R.string.settings_lan_notify_vibrate,
            LanNotifications.OFF to R.string.settings_lan_notify_off,
        ).forEach { (mode, label) ->
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .selectable(selected = settings.lanNotifications == mode, role = Role.RadioButton, onClick = { onNotifications(mode) })
                    .padding(vertical = 4.dp),
            ) {
                RadioButton(selected = settings.lanNotifications == mode, onClick = null)
                Text(stringResource(label), modifier = Modifier.padding(start = 8.dp))
            }
        }
        OutlinedButton(onClick = onSystemSettings, modifier = Modifier.padding(top = 4.dp)) {
            Text(stringResource(R.string.settings_lan_system_notifications))
        }
        if (transportIds.size > 1) {
            Text(stringResource(R.string.settings_lan_connection), style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
            transportIds.forEach { id ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .selectable(selected = settings.lanTransport == id, role = Role.RadioButton, onClick = { onTransport(id) })
                        .padding(vertical = 4.dp),
                ) {
                    RadioButton(selected = settings.lanTransport == id, onClick = null)
                    Text(id, modifier = Modifier.padding(start = 8.dp))
                }
            }
        }
    }
}
