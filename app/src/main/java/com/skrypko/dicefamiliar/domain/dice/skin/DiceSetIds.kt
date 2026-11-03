package com.skrypko.dicefamiliar.domain.dice.skin

/** Builds valid, unique dice-set ids (`a-z`, `0-9`, `_`, `-`, 1–40 chars). */
object DiceSetIds {
    const val MAX_LENGTH = 40
    const val FALLBACK = "custom"
    private val VALID = Regex("^[a-z0-9_\\-]{1,$MAX_LENGTH}$")

    fun isValid(id: String): Boolean = VALID.matches(id)

    /** A valid id derived from [name]: lower-cased, other characters become `_`. */
    fun fromName(name: String): String {
        val slug = name.lowercase()
            .map { if (it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-') it else '_' }
            .joinToString("")
            .replace(Regex("_+"), "_")
            .trim('_')
            .take(MAX_LENGTH)
            .trim('_')
        return slug.ifEmpty { FALLBACK }
    }

    /** [base] if free, otherwise `base_2`, `base_3` … (kept within [MAX_LENGTH]). */
    fun unique(base: String, taken: Set<String>): String {
        if (base !in taken) return base
        var n = 2
        while (true) {
            val suffix = "_$n"
            val candidate = base.take(MAX_LENGTH - suffix.length) + suffix
            if (candidate !in taken) return candidate
            n++
        }
    }
}
