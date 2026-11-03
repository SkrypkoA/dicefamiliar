package com.skrypko.dicefamiliar.domain.pool

import com.skrypko.dicefamiliar.domain.random.RandomSource

enum class PickError { EMPTY_POOL, COUNT_TOO_SMALL, COUNT_TOO_LARGE, COUNT_EXCEEDS_POOL }

sealed interface PickResult<out T> {
    data class Picked<T>(val items: List<T>) : PickResult<T>
    data class Failed(val error: PickError) : PickResult<Nothing>
}

/** Picks random items; all randomness goes through [RandomSource] (unbiased `nextInt`, Fisher–Yates). */
class PoolPicker(private val random: RandomSource) {

    fun <T> pick(items: List<T>, count: Int, withReplacement: Boolean): PickResult<T> {
        val error = check(items.size, count, withReplacement)
        if (error != null) return PickResult.Failed(error)
        val picked = if (withReplacement) {
            List(count) { items[random.nextInt(0, items.size)] }
        } else {
            random.shuffle(items).take(count)
        }
        return PickResult.Picked(picked)
    }

    companion object {
        /** Why [count] picks from [available] items can't be made, or `null`. */
        fun check(available: Int, count: Int, withReplacement: Boolean): PickError? = when {
            available == 0 -> PickError.EMPTY_POOL
            count < 1 -> PickError.COUNT_TOO_SMALL
            count > PoolLimits.MAX_PICKS -> PickError.COUNT_TOO_LARGE
            !withReplacement && count > available -> PickError.COUNT_EXCEEDS_POOL
            else -> null
        }
    }
}
