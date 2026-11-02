package com.skrypko.dicefamiliar.domain.remote

import java.util.UUID

/** Produces unique ids for requests; injectable so tests can be deterministic. */
fun interface IdGenerator {
    fun newId(): String
}

class UuidIdGenerator : IdGenerator {
    override fun newId(): String = UUID.randomUUID().toString()
}
