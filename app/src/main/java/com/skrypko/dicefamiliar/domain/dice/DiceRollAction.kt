package com.skrypko.dicefamiliar.domain.dice

import com.skrypko.dicefamiliar.domain.remote.ActionContext
import com.skrypko.dicefamiliar.domain.remote.HandlerResult
import com.skrypko.dicefamiliar.domain.remote.RemoteAction
import com.skrypko.dicefamiliar.domain.remote.RemoteActionHandler
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable

/** Request payload; [notation] is the wire format and is re-parsed by the device that rolls. */
@Serializable
data class DiceRollPayload(val notation: String, val mode: RollMode = RollMode.NORMAL)

@Serializable
data class DiceRollResult(val outcome: RollOutcome)

object DiceRollAction : RemoteAction<DiceRollPayload, DiceRollResult> {
    override val type = "dice.roll"
    override val version = 1
    override val requestSerializer: KSerializer<DiceRollPayload> = DiceRollPayload.serializer()
    override val resultSerializer: KSerializer<DiceRollResult> = DiceRollResult.serializer()
}

/** Rolls on this device; used for local rolls and, later, for requests from other users. */
class DiceRollHandler(private val roller: DiceRoller) : RemoteActionHandler<DiceRollPayload, DiceRollResult> {
    override val action = DiceRollAction

    override suspend fun handle(request: DiceRollPayload, context: ActionContext): HandlerResult<DiceRollResult> =
        when (val parsed = DiceNotationParser.parse(request.notation)) {
            is ParseResult.Error -> HandlerResult.Rejected(parsed.error.remoteCode)
            is ParseResult.Success -> {
                val expression = request.mode.applyTo(parsed.expression)
                HandlerResult.Completed(DiceRollResult(roller.roll(expression)))
            }
        }
}
