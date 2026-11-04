package com.skrypko.dicefamiliar.ui.lan

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skrypko.dicefamiliar.data.lan.LanError
import com.skrypko.dicefamiliar.data.lan.LanRole
import com.skrypko.dicefamiliar.data.lan.LanSessionManager
import com.skrypko.dicefamiliar.data.lan.LanState
import com.skrypko.dicefamiliar.data.lan.transport.LanHostInfo
import com.skrypko.dicefamiliar.data.lan.transport.LocalAddress
import com.skrypko.dicefamiliar.data.lan.transport.WifiNsdTransport
import com.skrypko.dicefamiliar.domain.lan.LanAddress
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LanUiState(
    val session: LanState = LanState(),
    /** Tables found on the network (only looked for while not hosting or playing). */
    val hosts: List<LanHostInfo> = emptyList(),
    /** Host: this device's addresses for "Join by address" (Wi-Fi first, then VPN). */
    val addresses: List<LocalAddress> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class LanViewModel @Inject constructor(private val session: LanSessionManager) : ViewModel() {

    private val hosts = session.state.map { it.role }.distinctUntilChanged().flatMapLatest { role ->
        if (role != LanRole.NONE) {
            flowOf(emptyList())
        } else {
            flow { emitAll(session.discoverHosts()) }.catch { emit(emptyList()) }
        }
    }

    val uiState: StateFlow<LanUiState> = combine(session.state, hosts) { state, found ->
        LanUiState(
            session = state,
            hosts = found,
            addresses = state.port?.takeIf { state.role == LanRole.HOST }?.let(WifiNsdTransport::localAddresses).orEmpty(),
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), LanUiState(session.state.value))

    fun host() = viewModelScope.launch { session.host() }

    fun join(host: LanHostInfo) = viewModelScope.launch { session.join(host) }

    /** "Join by address": `false` when [text] isn't an address (the dialog stays open with an error). */
    fun joinAddress(text: String): Boolean {
        val address = LanAddress.parse(text) ?: return false
        viewModelScope.launch { session.joinAddress(address) }
        return true
    }

    fun leave() = viewModelScope.launch { session.leave() }

    fun kick(playerId: String) = viewModelScope.launch { session.kick(playerId) }

    fun clearError() = session.clearError()

    companion object {
        fun errorText(error: LanError): Int = when (error) {
            LanError.CONNECT_FAILED -> com.skrypko.dicefamiliar.R.string.lan_error_connect
            LanError.HOST_LOST -> com.skrypko.dicefamiliar.R.string.lan_error_host_lost
            LanError.HOST_STOPPED -> com.skrypko.dicefamiliar.R.string.lan_error_host_stopped
            LanError.KICKED -> com.skrypko.dicefamiliar.R.string.lan_error_kicked
            LanError.VERSION_MISMATCH -> com.skrypko.dicefamiliar.R.string.lan_error_version
            LanError.HOSTING_FAILED -> com.skrypko.dicefamiliar.R.string.lan_error_hosting
        }
    }
}
