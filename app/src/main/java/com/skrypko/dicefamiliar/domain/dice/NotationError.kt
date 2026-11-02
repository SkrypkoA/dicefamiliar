package com.skrypko.dicefamiliar.domain.dice

/** Why a notation is invalid. Mapped to localized text by the UI. */
enum class NotationError {
    EMPTY,
    TOO_LONG,
    UNEXPECTED_CHAR,
    INCOMPLETE,
    MISSING_SIDES,
    COUNT_OUT_OF_RANGE,
    SIDES_OUT_OF_RANGE,
    KEEP_OUT_OF_RANGE,
    CONSTANT_OUT_OF_RANGE,
    TOO_MANY_TERMS,
    TOO_MANY_DICE,
    ;

    /** Error code used in remote responses. */
    val remoteCode: String get() = "dice.${name.lowercase()}"

    companion object {
        fun fromRemoteCode(code: String?): NotationError? = entries.firstOrNull { it.remoteCode == code }
    }
}

sealed interface ParseResult {
    data class Success(val expression: DiceExpression) : ParseResult
    data class Error(val error: NotationError, val position: Int) : ParseResult
}
