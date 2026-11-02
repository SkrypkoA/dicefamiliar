package com.skrypko.dicefamiliar.domain.remote

import kotlinx.serialization.Serializable

/** Identity of a device/user taking part in local or remote actions. */
@Serializable
data class Participant(val id: String, val name: String)
