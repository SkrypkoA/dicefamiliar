package com.skrypko.dicefamiliar.domain.pool

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PoolDisplayModeTest {
    private val plain = PoolItem(name = "A")
    private val pictured = PoolItem(name = "B", imagePath = "b.png")

    @Test
    fun noImagesIsNamesOnly() {
        assertEquals(PoolDisplayMode.NAMES_ONLY, PoolDisplayMode.of(listOf(plain, plain.copy(name = "C"))))
        assertEquals(PoolDisplayMode.NAMES_ONLY, PoolDisplayMode.of(listOf(plain), showNames = false))
        assertEquals(PoolDisplayMode.NAMES_ONLY, PoolDisplayMode.of(emptyList()))
    }

    @Test
    fun mixedIsThumbnailAndNameEvenWhenNamesHidden() {
        assertEquals(PoolDisplayMode.THUMBNAIL_AND_NAME, PoolDisplayMode.of(listOf(plain, pictured)))
        assertEquals(PoolDisplayMode.THUMBNAIL_AND_NAME, PoolDisplayMode.of(listOf(plain, pictured), showNames = false))
    }

    @Test
    fun allImagesFollowsShowNames() {
        val items = listOf(pictured, pictured.copy(name = "C"))
        assertEquals(PoolDisplayMode.THUMBNAIL_AND_NAME, PoolDisplayMode.of(items, showNames = true))
        assertEquals(PoolDisplayMode.IMAGES_ONLY, PoolDisplayMode.of(items, showNames = false))
        assertEquals(PoolDisplayMode.IMAGES_ONLY, Pool(name = "P", items = items, showNames = false).displayMode)
    }

    @Test
    fun namesCanBeHiddenOnlyWhenEveryItemHasAnImage() {
        assertTrue(PoolDisplayMode.canHideNames(listOf(pictured)))
        assertFalse(PoolDisplayMode.canHideNames(listOf(pictured, plain)))
        assertFalse(PoolDisplayMode.canHideNames(emptyList()))
    }
}

class PoolValidatorTest {
    @Test
    fun validPool() {
        assertTrue(PoolValidator.validate(Pool(name = "Fruit", items = listOf(PoolItem(name = "Apple")))).isEmpty())
    }

    @Test
    fun nameRequired() {
        assertEquals(listOf(PoolProblem(PoolError.NAME_REQUIRED)), PoolValidator.validate(Pool(name = "  ")))
    }

    @Test
    fun duplicatesAreTrimmedAndCaseInsensitive() {
        val pool = Pool(name = "P", items = listOf(PoolItem(name = "Apple"), PoolItem(name = " apple "), PoolItem(name = "")))
        assertEquals(
            listOf(PoolProblem(PoolError.DUPLICATE_ITEM_NAME, 1, "apple"), PoolProblem(PoolError.ITEM_NAME_REQUIRED, 2)),
            PoolValidator.validate(pool),
        )
    }

    @Test
    fun tooManyItems() {
        val items = (0..PoolLimits.MAX_ITEMS).map { PoolItem(name = "i$it") }
        assertEquals(listOf(PoolProblem(PoolError.TOO_MANY_ITEMS)), PoolValidator.validate(Pool(name = "P", items = items)))
    }

    @Test
    fun checkItemName() {
        assertEquals(PoolError.DUPLICATE_ITEM_NAME, PoolValidator.checkItemName("APPLE ", listOf("apple", "Pear")))
        assertEquals(PoolError.ITEM_NAME_REQUIRED, PoolValidator.checkItemName("  ", emptyList()))
        assertNull(PoolValidator.checkItemName("Plum", listOf("apple")))
    }
}

class UniqueNameResolverTest {
    @Test
    fun appendsSmallestFreeNumber() {
        val resolver = UniqueNameResolver(listOf("Apple", "apple #3"))
        assertEquals("Apple #2", resolver.resolve("Apple"))
        assertEquals("Apple #4", resolver.resolve(" Apple "))
        assertEquals("Pear", resolver.resolve("Pear"))
        assertEquals("pear #2", resolver.resolve("pear"))
    }
}

class ItemColorsTest {
    @Test
    fun parseAndFormat() {
        assertEquals(0xFFE53935.toInt(), ItemColors.parse("#E53935"))
        assertEquals(0x80E53935.toInt(), ItemColors.parse("#80e53935"))
        assertEquals("#E53935", ItemColors.format(0xFFE53935.toInt()))
        assertEquals("#80E53935", ItemColors.format(0x80E53935.toInt()))
        assertNull(ItemColors.parse("red"))
        assertNull(ItemColors.parse("#12345"))
        assertNull(ItemColors.parse(null))
    }

    @Test
    fun contrastText() {
        assertEquals(ItemColors.TextTone.BLACK, ItemColors.contrastText(0xFFFDD835.toInt()))
        assertEquals(ItemColors.TextTone.BLACK, ItemColors.contrastText(0xFFFFFFFF.toInt()))
        assertEquals(ItemColors.TextTone.WHITE, ItemColors.contrastText(0xFF000000.toInt()))
        assertEquals(ItemColors.TextTone.WHITE, ItemColors.contrastText(0xFF3949AB.toInt()))
    }

    @Test
    fun paletteIsSixteenOpaqueColours() {
        assertEquals(16, ItemColors.palette.toSet().size)
        assertTrue(ItemColors.palette.all { it ushr 24 == 0xFF })
    }
}
