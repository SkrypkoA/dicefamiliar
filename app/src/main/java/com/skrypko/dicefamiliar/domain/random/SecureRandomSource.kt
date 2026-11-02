package com.skrypko.dicefamiliar.domain.random

import java.security.SecureRandom
import kotlin.random.asKotlinRandom

/** Production [RandomSource] backed by [SecureRandom]. */
class SecureRandomSource : KotlinRandomSource(SecureRandom().asKotlinRandom())
