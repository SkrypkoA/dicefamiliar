package com.skrypko.dicefamiliar.ui.pool

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.skrypko.dicefamiliar.data.pool.PoolSummary
import com.skrypko.dicefamiliar.domain.pool.Pool
import com.skrypko.dicefamiliar.domain.pool.PoolError
import com.skrypko.dicefamiliar.domain.pool.PoolItem
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import com.skrypko.dicefamiliar.ui.pool.common.POOL_ITEM_IMAGE_TAG
import com.skrypko.dicefamiliar.ui.pool.common.POOL_ITEM_LABEL_TAG
import com.skrypko.dicefamiliar.ui.pool.common.POOL_ITEM_PLACEHOLDER_TAG
import com.skrypko.dicefamiliar.ui.pool.edit.DraftItem
import com.skrypko.dicefamiliar.ui.pool.edit.ITEM_SAVE_TAG
import com.skrypko.dicefamiliar.ui.pool.edit.ItemEditorState
import com.skrypko.dicefamiliar.ui.pool.edit.POOL_SHOW_NAMES_TAG
import com.skrypko.dicefamiliar.ui.pool.edit.PoolEditActions
import com.skrypko.dicefamiliar.ui.pool.edit.PoolEditContent
import com.skrypko.dicefamiliar.ui.pool.edit.PoolEditUiState
import com.skrypko.dicefamiliar.ui.pool.list.POOL_ADD_TAG
import com.skrypko.dicefamiliar.ui.pool.list.POOL_ROW_TAG
import com.skrypko.dicefamiliar.ui.pool.list.PoolListContent
import com.skrypko.dicefamiliar.ui.pool.list.PoolListUiState
import com.skrypko.dicefamiliar.ui.pool.pick.PICK_BUTTON_TAG
import com.skrypko.dicefamiliar.ui.pool.pick.PICK_PUT_BACK_TAG
import com.skrypko.dicefamiliar.ui.pool.pick.PickRecord
import com.skrypko.dicefamiliar.ui.pool.pick.PoolPickActions
import com.skrypko.dicefamiliar.ui.pool.pick.PoolPickContent
import com.skrypko.dicefamiliar.ui.pool.pick.PoolPickUiState
import com.skrypko.dicefamiliar.ui.theme.DiceFamiliarTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PoolScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val noAnimations = AppSettings(animationsEnabled = false)

    // --- List -----------------------------------------------------------------------------------

    @Test
    fun listOpensEditsCreatesAndConfirmsDelete() {
        val opened = mutableListOf<String>()
        val edited = mutableListOf<Long>()
        val deleted = mutableListOf<Long>()
        var created = 0
        compose.setContent {
            DiceFamiliarTheme {
                PoolListContent(
                    PoolListUiState(listOf(PoolSummary(7, "Drinks", 24, 24, null)), loaded = true),
                    onOpenPool = { opened += it.name },
                    onEditPool = { edited += it },
                    onNewPool = { created++ },
                    onDelete = { deleted += it },
                )
            }
        }
        compose.onNodeWithText("24 items").assertIsDisplayed()
        compose.onNodeWithTag(POOL_ROW_TAG).performClick()
        compose.onNodeWithContentDescription("Edit Drinks").performClick()
        compose.onNodeWithTag(POOL_ADD_TAG).performClick()
        compose.onNodeWithTag(POOL_ROW_TAG).performTouchInput { longClick() }
        compose.onNodeWithText("Delete pool?").assertIsDisplayed()
        compose.onNodeWithText("Delete").performClick()
        assertEquals(listOf("Drinks"), opened)
        assertEquals(listOf(7L), edited)
        assertEquals(1, created)
        assertEquals(listOf(7L), deleted)
    }

    @Test
    fun emptyListShowsAHint() {
        compose.setContent { DiceFamiliarTheme { PoolListContent(PoolListUiState(loaded = true), {}, {}, {}, {}) } }
        compose.onNodeWithText("No pools yet", substring = true).assertIsDisplayed()
    }

    // --- Edit -----------------------------------------------------------------------------------

    private fun draft(vararg items: PoolItem) = items.mapIndexed { i, item -> DraftItem(i + 1L, item) }

    private fun showEdit(state: PoolEditUiState, actions: PoolEditActions = PoolEditActions()) =
        compose.setContent { DiceFamiliarTheme { PoolEditContent(state, actions) } }

    @Test
    fun namesOnlyPoolHasNoThumbnailsAndShowNamesIsLocked() {
        showEdit(PoolEditUiState(loaded = true, name = "Drinks", items = draft(PoolItem(name = "Tea"), PoolItem(name = "Milk"))))
        compose.onAllNodesWithTag(POOL_ITEM_LABEL_TAG, useUnmergedTree = true).assertCountEquals(2)
        compose.onAllNodesWithTag(POOL_ITEM_PLACEHOLDER_TAG, useUnmergedTree = true).assertCountEquals(0)
        compose.onNodeWithTag(POOL_SHOW_NAMES_TAG).assertIsNotEnabled().assertIsOn()
        compose.onNodeWithText("Names are always shown", substring = true).assertIsDisplayed()
    }

    @Test
    fun mixedPoolShowsPlaceholders() {
        showEdit(PoolEditUiState(loaded = true, name = "Mix", items = draft(PoolItem(name = "Pic", imagePath = "x.png"), PoolItem(name = "Plain"))))
        compose.onAllNodesWithTag(POOL_ITEM_PLACEHOLDER_TAG, useUnmergedTree = true).assertCountEquals(1)
        compose.onAllNodesWithTag(POOL_ITEM_LABEL_TAG, useUnmergedTree = true).assertCountEquals(2)
    }

    @Test
    fun allImagesCanHideNames() {
        val changes = mutableListOf<Boolean>()
        val items = draft(PoolItem(name = "Guards", imagePath = "g.png"), PoolItem(name = "Rifles", imagePath = "r.png"))
        showEdit(PoolEditUiState(loaded = true, name = "Doctrines", items = items, showNames = false), PoolEditActions(onShowNamesChange = { changes += it }))
        compose.onAllNodesWithTag(POOL_ITEM_LABEL_TAG, useUnmergedTree = true).assertCountEquals(0)
        compose.onAllNodesWithTag(POOL_ITEM_IMAGE_TAG, useUnmergedTree = true).assertCountEquals(2)
        compose.onNodeWithContentDescription("Guards", useUnmergedTree = true).assertExists()
        compose.onNodeWithTag(POOL_SHOW_NAMES_TAG).assertIsEnabled().assertIsOff().performClick()
        assertEquals(listOf(true), changes)
    }

    @Test
    fun duplicateNameErrorBlocksDone() {
        showEdit(
            PoolEditUiState(
                loaded = true,
                name = "Fruit",
                items = draft(PoolItem(name = "Apple")),
                editor = ItemEditorState(name = "apple", error = PoolError.DUPLICATE_ITEM_NAME),
            ),
        )
        compose.onNodeWithText("An item named “apple” already exists in this pool").assertIsDisplayed()
        compose.onNodeWithTag(ITEM_SAVE_TAG).assertIsNotEnabled()
    }

    @Test
    fun blankPoolNameShowsError() {
        showEdit(PoolEditUiState(loaded = true, name = "", showNameError = true))
        compose.onNodeWithText("Enter a name for the pool").assertIsDisplayed()
    }

    @Test
    fun exportIsDisabledForAnEmptyPool() {
        showEdit(PoolEditUiState(loaded = true, name = "Empty"))
        compose.onNodeWithContentDescription("Export").assertIsNotEnabled()
        compose.onNodeWithContentDescription("Import").assertIsEnabled().performClick()
        compose.onNodeWithText("CSV: one item per line", substring = true).assertIsDisplayed()
    }

    // --- Pick -----------------------------------------------------------------------------------

    private val fruit = Pool(1, "Fruit", listOf("Apple", "Pear", "Plum").mapIndexed { i, n -> PoolItem(i + 1L, n) })

    @Test
    fun pickShowsResultAndCallsPick() {
        var picks = 0
        compose.setContent {
            DiceFamiliarTheme {
                PoolPickContent(
                    PoolPickUiState(loaded = true, pool = fruit, settings = noAnimations, latest = PickRecord("1", listOf(fruit.items[1]), 0)),
                    PoolPickActions(onPick = { picks++ }),
                )
            }
        }
        compose.onNodeWithText("Pear").assertIsDisplayed()
        compose.onNodeWithTag(PICK_BUTTON_TAG).performClick()
        assertEquals(1, picks)
    }

    @Test
    fun removePickedShowsLeftAndPutBack() {
        var putBack = 0
        val pool = fruit.copy(items = fruit.items.mapIndexed { i, it -> if (i == 0) it.copy(setAside = true) else it })
        compose.setContent {
            DiceFamiliarTheme {
                PoolPickContent(
                    PoolPickUiState(loaded = true, pool = pool, removePicked = true, settings = noAnimations),
                    PoolPickActions(onPutBackAll = { putBack++ }),
                )
            }
        }
        compose.onNodeWithText("2 of 3 left").assertIsDisplayed()
        compose.onNodeWithTag(PICK_PUT_BACK_TAG).performClick()
        assertEquals(1, putBack)
    }

    @Test
    fun collapsedPanelHidesOptionsButKeepsShareAndPick() {
        var toggles = 0
        compose.setContent {
            DiceFamiliarTheme {
                PoolPickContent(
                    PoolPickUiState(loaded = true, pool = fruit, settings = noAnimations, panelVisible = false),
                    PoolPickActions(onTogglePanel = { toggles++ }),
                )
            }
        }
        compose.onNodeWithText("How many").assertDoesNotExist()
        compose.onNodeWithContentDescription("Share result").assertIsDisplayed()
        compose.onNodeWithTag(PICK_BUTTON_TAG).assertIsDisplayed()
        compose.onNodeWithContentDescription("Show options").performClick()
        assertEquals(1, toggles)
    }

    @Test
    fun everyItemPickedDisablesPick() {
        val pool = fruit.copy(items = fruit.items.map { it.copy(setAside = true) })
        compose.setContent {
            DiceFamiliarTheme { PoolPickContent(PoolPickUiState(loaded = true, pool = pool, removePicked = true, settings = noAnimations), PoolPickActions()) }
        }
        compose.onNodeWithText("Every item has been picked", substring = true).assertIsDisplayed()
        compose.onNodeWithTag(PICK_BUTTON_TAG).assertIsNotEnabled()
    }
}
