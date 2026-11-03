package com.skrypko.dicefamiliar.domain.pool

import java.util.Locale

/** One entry of a pool. [imagePath] is relative to the app's pool image folder; [colorArgb] is `0xAARRGGBB`. */
data class PoolItem(
    val id: Long = 0,
    val name: String,
    val imagePath: String? = null,
    val colorArgb: Int? = null,
    /** Set aside by "Remove picked items": not picked again until the pool is put back together. */
    val setAside: Boolean = false,
)

data class Pool(
    val id: Long = 0,
    val name: String,
    val items: List<PoolItem> = emptyList(),
    /** Only matters when every item has an image: `false` shows the images without names. */
    val showNames: Boolean = true,
) {
    val displayMode: PoolDisplayMode get() = PoolDisplayMode.of(items, showNames)
}

object PoolLimits {
    const val MAX_ITEMS = 1000
    const val MAX_NAME_LENGTH = 100
    const val MAX_PICKS = 100
}

/** How every item of a pool is drawn; worked out from the whole pool, never item by item. */
enum class PoolDisplayMode {
    /** No item has an image: name labels only, no thumbnail slot. */
    NAMES_ONLY,

    /** Some (or all, with names shown) items have an image: thumbnail + name, placeholders keep rows aligned. */
    THUMBNAIL_AND_NAME,

    /** Every item has an image and the pool hides names: images only (the name stays the content description). */
    IMAGES_ONLY;

    companion object {
        fun of(items: List<PoolItem>, showNames: Boolean = true): PoolDisplayMode {
            val withImage = items.count { it.imagePath != null }
            return when {
                withImage == 0 -> NAMES_ONLY
                withImage == items.size && !showNames -> IMAGES_ONLY
                else -> THUMBNAIL_AND_NAME
            }
        }

        /** Hiding names is possible only when every item has an image (an item without one would lose its label). */
        fun canHideNames(items: List<PoolItem>): Boolean = items.isNotEmpty() && items.all { it.imagePath != null }
    }
}

/** Names are compared trimmed and case-insensitively. */
object NameNormalizer {
    fun normalize(name: String): String = name.trim().lowercase(Locale.ROOT)
}

enum class PoolError { NAME_REQUIRED, ITEM_NAME_REQUIRED, DUPLICATE_ITEM_NAME, TOO_MANY_ITEMS }

data class PoolProblem(val error: PoolError, val itemIndex: Int? = null, val itemName: String? = null)

object PoolValidator {

    /** Every problem with [pool]; empty means it can be saved. */
    fun validate(pool: Pool): List<PoolProblem> = buildList {
        if (pool.name.isBlank()) add(PoolProblem(PoolError.NAME_REQUIRED))
        if (pool.items.size > PoolLimits.MAX_ITEMS) add(PoolProblem(PoolError.TOO_MANY_ITEMS))
        val seen = HashSet<String>()
        pool.items.forEachIndexed { index, item ->
            val key = NameNormalizer.normalize(item.name)
            when {
                key.isEmpty() -> add(PoolProblem(PoolError.ITEM_NAME_REQUIRED, index))
                !seen.add(key) -> add(PoolProblem(PoolError.DUPLICATE_ITEM_NAME, index, item.name.trim()))
            }
        }
    }

    /**
     * The error for a name typed into the item editor: blank, or already used by another item
     * ([existing] are the other items' names, without the one being edited).
     */
    fun checkItemName(name: String, existing: Collection<String>): PoolError? {
        val key = NameNormalizer.normalize(name)
        return when {
            key.isEmpty() -> PoolError.ITEM_NAME_REQUIRED
            existing.any { NameNormalizer.normalize(it) == key } -> PoolError.DUPLICATE_ITEM_NAME
            else -> null
        }
    }
}

/** Makes imported names unique: a clash gets ` #N` with the smallest N ≥ 2 that is free. */
class UniqueNameResolver(existing: Collection<String> = emptyList()) {
    private val taken = existing.mapTo(HashSet()) { NameNormalizer.normalize(it) }

    /** The name to use for [name] (trimmed), reserving it. */
    fun resolve(name: String): String {
        val base = name.trim()
        if (taken.add(NameNormalizer.normalize(base))) return base
        var n = 2
        while (true) {
            val candidate = "$base #$n"
            if (taken.add(NameNormalizer.normalize(candidate))) return candidate
            n++
        }
    }
}
