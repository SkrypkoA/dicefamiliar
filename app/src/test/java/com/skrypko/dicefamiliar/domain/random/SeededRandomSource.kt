package com.skrypko.dicefamiliar.domain.random

import kotlin.random.Random

/** Deterministic [RandomSource] for tests. */
class SeededRandomSource(seed: Long = 42L) : KotlinRandomSource(Random(seed))
