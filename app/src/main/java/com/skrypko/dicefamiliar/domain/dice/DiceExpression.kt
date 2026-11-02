package com.skrypko.dicefamiliar.domain.dice

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
enum class Sign(val factor: Int, val symbol: Char) {
    PLUS(1, '+'),
    MINUS(-1, '-'),
}

@Serializable
enum class KeepType(val code: String) {
    HIGHEST("kh"),
    LOWEST("kl"),
}

/** Keep the [count] highest or lowest dice of a group (e.g. `4d6kh3`). */
@Serializable
data class Keep(val type: KeepType, val count: Int)

@Serializable
sealed interface Term {
    val sign: Sign
}

/** `NdM[kh|klK]`. */
@Serializable
@SerialName("dice")
data class DiceTerm(
    val count: Int,
    val sides: Int,
    val keep: Keep? = null,
    override val sign: Sign = Sign.PLUS,
) : Term {
    val isPlain: Boolean get() = keep == null
}

/** A flat modifier such as `+2`. */
@Serializable
@SerialName("const")
data class ConstantTerm(val value: Int, override val sign: Sign = Sign.PLUS) : Term

/** Parsed dice notation, e.g. `2d6+1d4kh1-1`. */
@Serializable
data class DiceExpression(val terms: List<Term>) {

    val diceCount: Int get() = terms.filterIsInstance<DiceTerm>().sumOf { it.count }

    /** Canonical notation; parsing it again yields an equal expression. */
    fun toNotation(): String = buildString {
        terms.forEachIndexed { index, term ->
            if (index > 0 || term.sign == Sign.MINUS) append(term.sign.symbol)
            when (term) {
                is DiceTerm -> {
                    append(term.count).append('d').append(term.sides)
                    term.keep?.let { append(it.type.code).append(it.count) }
                }
                is ConstantTerm -> append(term.value)
            }
        }
    }

    override fun toString(): String = toNotation()
}
