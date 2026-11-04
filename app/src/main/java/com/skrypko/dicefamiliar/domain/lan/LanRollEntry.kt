package com.skrypko.dicefamiliar.domain.lan

import com.skrypko.dicefamiliar.domain.dice.RollOutcome
import com.skrypko.dicefamiliar.domain.remote.Participant

enum class LanRollState { REQUESTED, ROLLED, DECLINED, CANCELLED, FAILED, DISCONNECTED }

/** One request in a player's roll log (the host keeps one log per player; a player keeps their own). */
data class LanRollEntry(
    val requestId: String,
    val player: Participant,
    val notation: String,
    val pool: Boolean,
    val state: LanRollState,
    val requestedAt: Long,
    val outcome: RollOutcome? = null,
    val errorCode: String? = null,
)
