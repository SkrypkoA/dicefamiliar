package com.skrypko.dicefamiliar.domain.random

import kotlin.random.Random

/** [RandomSource] backed by any [kotlin.random.Random]; its range functions are unbiased. */
open class KotlinRandomSource(private val random: Random) : RandomSource {
    override fun nextInt(from: Int, until: Int): Int = random.nextInt(from, until)

    override fun nextLong(from: Long, until: Long): Long = random.nextLong(from, until)

    override fun nextDouble(from: Double, until: Double): Double = random.nextDouble(from, until)
}
