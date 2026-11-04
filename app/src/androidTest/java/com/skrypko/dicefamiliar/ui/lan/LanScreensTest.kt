package com.skrypko.dicefamiliar.ui.lan

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.skrypko.dicefamiliar.data.lan.LanPlayer
import com.skrypko.dicefamiliar.data.lan.LanRole
import com.skrypko.dicefamiliar.data.lan.LanState
import com.skrypko.dicefamiliar.data.lan.transport.LanHostInfo
import com.skrypko.dicefamiliar.data.lan.transport.LocalAddress
import com.skrypko.dicefamiliar.domain.dice.DiceNotationParser
import com.skrypko.dicefamiliar.domain.dice.DiceRoller
import com.skrypko.dicefamiliar.domain.dice.ParseResult
import com.skrypko.dicefamiliar.domain.lan.LanAddress
import com.skrypko.dicefamiliar.domain.lan.LanRollEntry
import com.skrypko.dicefamiliar.domain.lan.LanRollState
import com.skrypko.dicefamiliar.domain.random.RandomSource
import com.skrypko.dicefamiliar.domain.remote.ActionRequest
import com.skrypko.dicefamiliar.domain.remote.Participant
import com.skrypko.dicefamiliar.domain.settings.AppSettings
import com.skrypko.dicefamiliar.ui.dice.DiceContent
import com.skrypko.dicefamiliar.ui.dice.DiceLanActions
import com.skrypko.dicefamiliar.ui.dice.DiceUiState
import com.skrypko.dicefamiliar.ui.dice.IncomingRoll
import com.skrypko.dicefamiliar.ui.dice.LAN_BANNER_TAG
import com.skrypko.dicefamiliar.ui.dice.LAN_LOCKED_TAG
import com.skrypko.dicefamiliar.ui.dice.LAN_SEND_TAG
import com.skrypko.dicefamiliar.ui.theme.DiceFamiliarTheme
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LanScreensTest {
    @get:Rule
    val compose = createComposeRule()

    private val alex = Participant("a", "Alex")
    private val bob = Participant("b", "Bob")
    private val calls = mutableListOf<String>()

    private val actions = LanActions(
        onHost = { calls += "host" },
        onJoin = { calls += "join ${it.name}" },
        onJoinAddress = { text -> (LanAddress.parse(text) != null).also { if (it) calls += "address $text" } },
        onLeave = { calls += "leave" },
        onKick = { calls += "kick $it" },
        onOpenPlayer = { calls += "open $it" },
        onOpenRequest = { calls += "request" },
    )

    private fun show(state: LanUiState) = compose.setContent { DiceFamiliarTheme { LanContent(state, actions) } }

    private fun outcome(notation: String, vararg values: Int) = DiceRoller(object : RandomSource {
        val queue = ArrayDeque(values.toList())
        override fun nextInt(from: Int, until: Int) = queue.removeFirst()
        override fun nextLong(from: Long, until: Long) = error("unused")
        override fun nextDouble(from: Double, until: Double) = error("unused")
    }).roll((DiceNotationParser.parse(notation) as ParseResult.Success).expression)

    @Test
    fun noSessionHostsJoinsAndJoinsByAddress() {
        show(LanUiState(hosts = listOf(LanHostInfo("addr:1", "Alex"))))
        compose.onNodeWithText("Host a table").performClick()
        compose.onNodeWithText("Alex").performClick()
        compose.onNodeWithText("Join by address").performClick()
        compose.onNodeWithText("Host address").performTextInput("not an address!")
        compose.onNodeWithText("Join").performClick()
        compose.onNodeWithText("That isn't an address", substring = true).assertIsDisplayed()
        compose.onNodeWithText("not an address!").performTextInput("")
        assertEquals(listOf("host", "join Alex"), calls)
    }

    @Test
    fun emptyNetworkExplainsWhatToDo() {
        show(LanUiState())
        compose.onNodeWithText("No tables found yet", substring = true).assertIsDisplayed()
    }

    @Test
    fun hostSeesAddressesPlayersAndLastRolls() {
        val log = listOf(LanRollEntry("r1", bob, "2d6+1", false, LanRollState.ROLLED, 0, outcome("2d6+1", 3, 5)))
        show(
            LanUiState(
                session = LanState(role = LanRole.HOST, me = alex, players = listOf(LanPlayer(bob, true)), logs = mapOf(bob.id to log), port = 47321),
                addresses = listOf(LocalAddress(LanAddress("192.168.1.20", 47321), vpn = false), LocalAddress(LanAddress("100.101.2.3", 47321), vpn = true)),
            ),
        )
        compose.onNodeWithText("Your table: Alex").assertIsDisplayed()
        compose.onNodeWithText("192.168.1.20:47321").assertIsDisplayed()
        compose.onNodeWithText("100.101.2.3:47321").assertIsDisplayed()
        compose.onNodeWithText("2d6+1 = 9").assertIsDisplayed()
        compose.onNodeWithText("Bob").performClick()
        compose.onNodeWithText("Bob").performTouchInput { longClick() }
        compose.onNodeWithText("Remove player?").assertIsDisplayed()
        compose.onNodeWithText("Remove").performClick()
        compose.onNodeWithText("Stop").performClick()
        assertEquals(listOf("open b", "kick b", "leave"), calls)
    }

    @Test
    fun playerSeesTheRequestCardAndTheirLog() {
        val request = ActionRequest("r2", "dice.roll", 1, JsonPrimitive(0), null, alex, bob, 0)
        val log = listOf(
            LanRollEntry("r2", bob, "10d6", true, LanRollState.REQUESTED, 0),
            LanRollEntry("r1", bob, "3d6", true, LanRollState.ROLLED, 0, outcome("3d6", 1, 6, 4)),
        )
        show(LanUiState(session = LanState(role = LanRole.PLAYER, me = bob, host = alex, incoming = request, logs = mapOf(bob.id to log))))
        compose.onNodeWithText("Connected to Alex's table").assertIsDisplayed()
        compose.onNodeWithText("3d6 (pool): 6 4 1").assertIsDisplayed()
        compose.onNodeWithText("10d6 (pool) · waiting").assertIsDisplayed()
        compose.onNodeWithText("Alex asks you to roll. Tap to open the dice.").performClick()
        compose.onNodeWithText("Leave").performClick()
        assertEquals(listOf("request", "leave"), calls)
    }

    // --- Dice screen in LAN modes ---------------------------------------------------------------

    private fun showDice(state: DiceUiState) = compose.setContent {
        DiceFamiliarTheme {
            DiceContent(
                state, {}, {}, {}, {}, {}, {}, onRoll = { calls += "roll" },
                lan = DiceLanActions(onOpenRequest = { calls += "open" }, onDeclineRequest = { calls += "decline" }, onBack = { calls += "back" }),
            )
        }
    }

    @Test
    fun hostRequestScreenSendsInsteadOfRolling() {
        showDice(DiceUiState(notation = "2d6", requestTarget = bob, settings = AppSettings(animationsEnabled = false)))
        compose.onNodeWithText("Dice for Bob").assertIsDisplayed()
        compose.onNodeWithTag(LAN_SEND_TAG).assertIsEnabled().performClick()
        compose.onNodeWithContentDescription("Back to the table").performClick()
        assertEquals(listOf("roll", "back"), calls)
    }

    @Test
    fun hostWaitsForThePlayer() {
        showDice(DiceUiState(notation = "2d6", requestTarget = bob, awaitingReply = true, settings = AppSettings(animationsEnabled = false)))
        compose.onNodeWithText("Waiting for Bob to roll…").assertIsDisplayed()
        compose.onNodeWithTag(LAN_SEND_TAG).assertIsNotEnabled()
    }

    @Test
    fun playerBannerAndLockedPanel() {
        val incoming = IncomingRoll("r1", alex, "3d6+2", com.skrypko.dicefamiliar.domain.dice.RollMode.NORMAL, pool = false)
        showDice(DiceUiState(notation = "1d20", incoming = incoming, settings = AppSettings(animationsEnabled = false)))
        compose.onNodeWithTag(LAN_BANNER_TAG).assertIsDisplayed()
        compose.onNodeWithText("Alex asks you to roll 3d6+2").assertIsDisplayed()
        compose.onNodeWithText("Roll").performClick()
        compose.onNodeWithText("Decline").performClick()
        assertEquals(listOf("open", "decline"), calls)
    }

    @Test
    fun lockedPanelShowsOnlyTheHostsDice() {
        val incoming = IncomingRoll("r1", alex, "10d6", com.skrypko.dicefamiliar.domain.dice.RollMode.NORMAL, pool = true)
        showDice(DiceUiState(notation = "10d6", poolMode = true, incoming = incoming, activeRequest = incoming, settings = AppSettings(animationsEnabled = false)))
        compose.onNodeWithTag(LAN_LOCKED_TAG).assertIsDisplayed()
        compose.onNodeWithText("10d6 (pool)").assertIsDisplayed()
        compose.onNodeWithText("d6").assertDoesNotExist()
        compose.onNodeWithTag(LAN_BANNER_TAG).assertDoesNotExist()
    }
}
