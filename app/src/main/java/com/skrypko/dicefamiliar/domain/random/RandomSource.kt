package com.skrypko.dicefamiliar.domain.random

/**
 * Single source of randomness for the whole app.
 *
 * Production code gets [SecureRandomSource] via Hilt; tests use a seeded implementation.
 * Never use `kotlin.random.Random` or `java.util.Random` directly from UI or ViewModels.
 */
interface RandomSource {
    /** Uniform integer in [from, until). */
    fun nextInt(from: Int, until: Int): Int

    /** Uniform long in [from, until). */
    fun nextLong(from: Long, until: Long): Long

    /** Uniform double in [from, until). */
    fun nextDouble(from: Double, until: Double): Double

    /** Returns a new list with the elements of [items] in random order (Fisher–Yates). */
    fun <T> shuffle(items: List<T>): List<T> {
        val result = items.toMutableList()
        for (i in result.lastIndex downTo 1) {
            val j = nextInt(0, i + 1)
            val tmp = result[i]
            result[i] = result[j]
            result[j] = tmp
        }
        return result
    }
}
