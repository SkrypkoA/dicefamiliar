package com.skrypko.dicefamiliar.ui.cards

import android.content.Intent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.splineBasedDecay
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.RestartAlt
import androidx.compose.material.icons.outlined.TouchApp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.zIndex
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import com.skrypko.dicefamiliar.R
import com.skrypko.dicefamiliar.domain.cards.CardFace
import com.skrypko.dicefamiliar.domain.cards.DeckDefinition
import com.skrypko.dicefamiliar.domain.cards.DeckKind
import com.skrypko.dicefamiliar.ui.common.CaptureAsImage
import com.skrypko.dicefamiliar.ui.common.HelpSheet
import com.skrypko.dicefamiliar.ui.common.HelpTopic
import com.skrypko.dicefamiliar.ui.common.FeatureHandleBar
import com.skrypko.dicefamiliar.ui.common.FeaturePanel
import com.skrypko.dicefamiliar.ui.common.FeatureShare
import com.skrypko.dicefamiliar.ui.common.copyText
import com.skrypko.dicefamiliar.ui.common.shareImage
import com.skrypko.dicefamiliar.ui.theme.LocalThemeStyle
import com.skrypko.dicefamiliar.ui.theme.tray.themedTray
import com.skrypko.dicefamiliar.ui.theme.tray.trayColors
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlinx.coroutines.launch

@Composable
fun CardTableScreen(onOpenDecks: () -> Unit, viewModel: CardTableViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val haptics = LocalHapticFeedback.current
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboard.current
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(viewModel) {
        viewModel.events.collect { event ->
            if (!viewModel.uiState.value.settings.hapticsEnabled) return@collect
            haptics.performHapticFeedback(
                when (event) {
                    CardEvent.SHUFFLED -> HapticFeedbackType.Confirm
                    CardEvent.DREW, CardEvent.DISCARDED -> HapticFeedbackType.ContextClick
                },
            )
        }
    }
    Box(Modifier.fillMaxSize()) {
    CardTableContent(
        state = state,
        onOpenDecks = onOpenDecks,
        onCopy = { text ->
            scope.launch {
                if (copyText(clipboard, resources.getString(R.string.cards_share), text)) snackbar.showSnackbar(resources.getString(R.string.copied))
            }
        },
        onShare = { text ->
            val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, text)
            context.startActivity(Intent.createChooser(send, resources.getString(R.string.cards_share)))
        },
        onShareImage = { image ->
            scope.launch { shareImage(context, image, "hand.png", resources.getString(R.string.cards_share)) }
        },
        actions = CardTableActions(
            draw = viewModel::draw,
            takeFromDiscard = viewModel::takeFromDiscard,
            discard = viewModel::discard,
            putOnDraw = viewModel::putOnDraw,
            discardHand = viewModel::discardHand,
            handToDraw = viewModel::handToDraw,
            shuffleDraw = viewModel::shuffleDraw,
            shuffleDiscard = viewModel::shuffleDiscard,
            discardOntoDraw = viewModel::discardOntoDraw,
            reset = viewModel::reset,
            toggleButtons = viewModel::toggleButtons,
            togglePanel = viewModel::togglePanel,
        ),
    )
    SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter))
    }
}

/** Everything the table can do; gestures and the optional buttons call the same actions. */
data class CardTableActions(
    val draw: () -> Unit = {},
    val takeFromDiscard: () -> Unit = {},
    /** A hand card onto the top of the discard pile. */
    val discard: (Int) -> Unit = {},
    /** A hand card onto the top of the draw pile. */
    val putOnDraw: (Int) -> Unit = {},
    /** The whole hand onto the top of the discard pile. */
    val discardHand: () -> Unit = {},
    /** The whole hand onto the top of the draw pile. */
    val handToDraw: () -> Unit = {},
    val shuffleDraw: () -> Unit = {},
    val shuffleDiscard: () -> Unit = {},
    val discardOntoDraw: () -> Unit = {},
    val reset: () -> Unit = {},
    val toggleButtons: () -> Unit = {},
    val togglePanel: () -> Unit = {},
)

/**
 * The card table: a collapsible top bar (deck, buttons toggle, reset), the handle bar (share, chevron, "?"),
 * then the play area (draw and discard piles above the hand) on the theme's tray, then the optional
 * action buttons.
 */
@Composable
fun CardTableContent(
    state: CardTableUiState,
    onOpenDecks: () -> Unit,
    actions: CardTableActions,
    modifier: Modifier = Modifier,
    onShare: (String) -> Unit = {},
    onShareImage: (ImageBitmap) -> Unit = {},
    onCopy: (String) -> Unit = {},
) {
    var capturing by remember { mutableStateOf(false) }
    var viewing by rememberSaveable { mutableStateOf<Int?>(null) }
    var confirmReset by rememberSaveable { mutableStateOf(false) }
    var showHelp by rememberSaveable { mutableStateOf(false) }
    val deck = state.deck
    val shareText = deck?.let { d ->
        stringResource(R.string.cards_share_text, deckTitle(d), state.hand.mapNotNull { state.face(it)?.name }.joinToString(", "))
    }

    Box(modifier.fillMaxSize()) {
    // "Share as image": the hand is rendered off screen, captured once, then shared.
    if (capturing) {
        CaptureAsImage(onCaptured = { image ->
            capturing = false
            onShareImage(image)
        }) { HandSnapshot(state) }
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val bar: @Composable () -> Unit = {
            TopBar(
                state,
                deck,
                onOpenDecks,
                onReset = { confirmReset = true },
                actions,
            )
        }
        FeaturePanel(visible = state.panelVisible, animate = state.settings.animationsEnabled) { bar() }
        FeatureHandleBar(
            panelVisible = state.panelVisible,
            onTogglePanel = actions.togglePanel,
            hideDescription = stringResource(R.string.cards_panel_hide),
            showDescription = stringResource(R.string.cards_panel_show),
            helpDescription = stringResource(R.string.help_cards_button),
            onHelp = { showHelp = true },
            share = FeatureShare(
                description = stringResource(R.string.cards_share),
                onCopy = { shareText?.let(onCopy) },
                onShareText = { shareText?.let(onShare) },
                onShareImage = { capturing = true },
                enabled = state.hand.isNotEmpty(),
            ),
        )

        PlayArea(state, actions, onView = { viewing = it }, modifier = Modifier.weight(1f).fillMaxWidth())

        if (state.showButtons) ActionButtons(state, actions)
    }
    }

    viewing?.let { card ->
        val face = state.face(card)
        if (face == null || card !in state.hand) {
            viewing = null
        } else {
            CardViewer(face, showButtons = state.showButtons, onDiscard = {
                actions.discard(card)
                viewing = null
            }, onPutOnDraw = {
                actions.putOnDraw(card)
                viewing = null
            }, onClose = { viewing = null })
        }
    }
    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text(stringResource(R.string.cards_reset_title)) },
            text = { Text(stringResource(R.string.cards_reset_text)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmReset = false
                    actions.reset()
                }) { Text(stringResource(R.string.cards_reset)) }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text(stringResource(R.string.action_cancel)) } },
        )
    }
    if (showHelp) HelpSheet(R.string.help_cards_title, CARDS_HELP, onDismiss = { showHelp = false })
}

@Composable
private fun TopBar(
    state: CardTableUiState,
    deck: DeckDefinition?,
    onOpenDecks: () -> Unit,
    onReset: () -> Unit,
    actions: CardTableActions,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onOpenDecks) {
            Icon(LocalThemeStyle.current.icons.cards, contentDescription = stringResource(R.string.cards_back_to_decks))
        }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                deck?.let { deckTitle(it) }.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                stringResource(R.string.cards_left, state.drawCount, state.discardCount),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        IconToggleButton(checked = state.showButtons, onCheckedChange = { actions.toggleButtons() }) {
            Icon(
                Icons.Outlined.TouchApp,
                contentDescription = stringResource(if (state.showButtons) R.string.cards_hide_buttons else R.string.cards_show_buttons),
            )
        }
        IconButton(onClick = onReset) {
            Icon(Icons.Outlined.RestartAlt, contentDescription = stringResource(R.string.cards_reset))
        }
    }
}

@Composable
private fun PlayArea(state: CardTableUiState, actions: CardTableActions, onView: (Int) -> Unit, modifier: Modifier) {
    val shape = MaterialTheme.shapes.extraLarge
    val inset = LocalThemeStyle.current.tray.contentInset
    // Overflow is hidden at the table's edge (inside a rim, if the tray has one): nothing spills over it.
    val inner = if (inset > 0.dp) RoundedCornerShape(18.dp) else shape
    var area by remember { mutableStateOf(Rect.Zero) }
    BoxWithConstraints(
        modifier.themedTray(shape).padding(inset).clip(inner).padding(12.dp).onGloballyPositioned { area = it.boundsInRoot() },
    ) {
        val pileWidth = (maxWidth * 0.36f).coerceAtMost(150.dp)
        val handHeight = maxHeight * 0.45f
        val piles = remember { PileTargets() }
        Column(Modifier.fillMaxSize()) {
            Piles(state, actions, piles, pileWidth, Modifier.weight(1f).fillMaxWidth())
            Hand(state, actions, piles, onView, Modifier.fillMaxWidth().height(handHeight))
        }
        // Several cards being moved (the hand, or the discard pile) follow the finger as a small pile.
        if (piles.carried.isNotEmpty()) {
            val width = with(LocalDensity.current) { pileWidth.toPx() }
            val height = width / CARD_ASPECT
            CarriedPile(
                piles.carried,
                Modifier
                    .testTag(CARRIED_TAG)
                    .offset { IntOffset((piles.carriedAt.x - area.left - width / 2).roundToInt(), (piles.carriedAt.y - area.top - height / 2).roundToInt()) }
                    .width(pileWidth),
            )
        }
    }
}

enum class PileTarget { DRAW, DISCARD }

/**
 * The two piles as drop targets, shared by everything that can be dragged onto them: the discard pile
 * (onto the deck), a hand card, and the whole hand. [carried] are the cards shown under the finger while
 * several cards are moved (at most 3, top card last), at [carriedAt] in root coordinates.
 */
private class PileTargets {
    var carried by mutableStateOf<List<CardFace>>(emptyList())
    var carriedAt by mutableStateOf(Offset.Zero)

    var drawBounds by mutableStateOf(Rect.Zero)
    var discardBounds by mutableStateOf(Rect.Zero)
    var hover by mutableStateOf<PileTarget?>(null)

    fun targetAt(point: Offset, allowDiscard: Boolean = true): PileTarget? = when {
        drawBounds.inflate(24f).contains(point) -> PileTarget.DRAW
        allowDiscard && discardBounds.inflate(24f).contains(point) -> PileTarget.DISCARD
        else -> null
    }
}

@Composable
private fun Piles(state: CardTableUiState, actions: CardTableActions, piles: PileTargets, pileWidth: Dp, modifier: Modifier) {
    val colors = trayColors()
    val animate = state.settings.animationsEnabled
    val scope = rememberCoroutineScope()
    var carryingDiscard by remember { mutableStateOf(false) }
    val current by rememberUpdatedState(actions)
    val discardFaces by rememberUpdatedState(state.table?.discard.orEmpty().takeLast(CARRIED_CARDS).mapNotNull(state::face))
    // Each pile shakes on its own when it is shuffled.
    val drawShake = remember { Animatable(0f) }
    val discardShake = remember { Animatable(0f) }
    val shuffleLabel = stringResource(R.string.cards_action_shuffle)
    val drawLabel = stringResource(R.string.cards_action_draw)
    val takeLabel = stringResource(R.string.cards_action_take_discard)
    val shuffleDiscardLabel = stringResource(R.string.cards_action_shuffle_discard)
    val intoDeckLabel = stringResource(R.string.cards_action_discard_into_deck)
    val highlight = MaterialTheme.colorScheme.tertiary

    fun jiggle(shake: Animatable<Float, *>) {
        if (animate) scope.launch {
            shake.snapTo(0f)
            shake.animateTo(0f, keyframes {
                durationMillis = 320
                8f at 60
                -8f at 130
                5f at 200
                -3f at 260
            })
        }
    }

    Row(modifier, horizontalArrangement = Arrangement.spacedBy(28.dp, Alignment.CenterHorizontally), verticalAlignment = Alignment.CenterVertically) {
        // Draw pile: tap = draw, long-press = shuffle; cards, the hand and the discard can be dropped on it.
        val drawDescription = stringResource(R.string.cards_draw_pile, state.drawCount)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                Modifier
                    .width(pileWidth)
                    .testTag(DRAW_PILE_TAG)
                    .onGloballyPositioned { piles.drawBounds = it.boundsInRoot() }
                    .graphicsLayer { translationX = drawShake.value.dp.toPx() }
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = { current.draw() }, onLongPress = {
                            current.shuffleDraw()
                            jiggle(drawShake)
                        })
                    }
                    .semantics {
                        contentDescription = drawDescription
                        onClick(drawLabel) { current.draw(); true }
                        onLongClick(shuffleLabel) { current.shuffleDraw(); true }
                    },
            ) {
                val hovered = piles.hover == PileTarget.DRAW
                if (state.drawCount == 0) {
                    EmptySlot(stringResource(R.string.cards_deck_empty), highlighted = hovered)
                } else {
                    if (state.drawCount > 2) CardBack(state.deck?.backImage, Modifier.offset(4.dp, 4.dp), elevation = 0.dp)
                    if (state.drawCount > 1) CardBack(state.deck?.backImage, Modifier.offset(2.dp, 2.dp), elevation = 0.dp)
                    CardBack(state.deck?.backImage)
                    if (hovered) Box(Modifier.matchParentSize().border(3.dp, highlight, RoundedCornerShape(8.dp)))
                }
            }
            Text(state.drawCount.toString(), color = colors.content, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
        }

        // Discard pile: tap = take back, long-press = shuffle, drag onto the deck = shuffle back in.
        val discardDescription = stringResource(R.string.cards_discard_pile, state.discardCount)
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.width(pileWidth).onGloballyPositioned { piles.discardBounds = it.boundsInRoot() }) {
                val discard = state.table?.discard.orEmpty()
                // While the pile is carried, show the card under the carried ones (or the empty slot).
                val under = discard.getOrNull(discard.size - if (carryingDiscard) CARRIED_CARDS + 1 else 2)?.let(state::face)
                val hovered = piles.hover == PileTarget.DISCARD
                if (under != null) PlayingCard(under, elevation = 0.dp) else EmptySlot(stringResource(R.string.cards_discard_empty), highlighted = hovered && state.topDiscard == null)
                state.topDiscard?.let { top ->
                    PlayingCard(
                        top,
                        Modifier
                            .testTag(DISCARD_PILE_TAG)
                            .graphicsLayer {
                                translationX = discardShake.value.dp.toPx()
                                alpha = if (carryingDiscard) 0f else 1f
                            }
                            .pointerInput(Unit) {
                                detectTapGestures(onTap = { current.takeFromDiscard() }, onLongPress = {
                                    current.shuffleDiscard()
                                    jiggle(discardShake)
                                })
                            }
                            .pointerInput(Unit) {
                                detectDragGestures(
                                    onDragStart = {
                                        carryingDiscard = true
                                        piles.carried = discardFaces
                                        piles.carriedAt = piles.discardBounds.center
                                    },
                                    onDrag = { change, amount ->
                                        change.consume()
                                        piles.carriedAt += amount
                                        piles.hover = piles.targetAt(piles.carriedAt, allowDiscard = false)
                                    },
                                    onDragEnd = {
                                        val drop = piles.hover == PileTarget.DRAW
                                        piles.hover = null
                                        piles.carried = emptyList()
                                        carryingDiscard = false
                                        // A move, not a shuffle: the pile is turned over onto the deck.
                                        if (drop) current.discardOntoDraw()
                                    },
                                    onDragCancel = {
                                        piles.hover = null
                                        piles.carried = emptyList()
                                        carryingDiscard = false
                                    },
                                )
                            }
                            .semantics {
                                contentDescription = discardDescription
                                onClick(takeLabel) { current.takeFromDiscard(); true }
                                onLongClick(shuffleDiscardLabel) { current.shuffleDiscard(); true }
                                customActions = listOf(CustomAccessibilityAction(intoDeckLabel) { current.discardOntoDraw(); true })
                            },
                    )
                    if (hovered) Box(Modifier.matchParentSize().border(3.dp, highlight, RoundedCornerShape(8.dp)))
                }
            }
            Text(state.discardCount.toString(), color = colors.content, style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

/** An outlined card-sized slot for an empty pile; highlighted while something is dragged over it. */
@Composable
private fun EmptySlot(label: String, highlighted: Boolean) {
    val colors = trayColors()
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(CARD_ASPECT)
            .border(
                if (highlighted) 3.dp else 1.5.dp,
                if (highlighted) MaterialTheme.colorScheme.tertiary else colors.muted,
                RoundedCornerShape(8.dp),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = colors.muted, style = MaterialTheme.typography.labelMedium)
    }
}

/**
 * The hand, always inside the table: a fan that fits the width (room is kept for its tilt and arc), or,
 * when the cards would hide their values, a flat row that scrolls sideways (with fling) and keeps every
 * value corner visible; a new card scrolls into view.
 *
 * **Long-press the hand** to pick up all of it and drop it on the draw or discard pile (on top);
 * released elsewhere it goes back.
 */
@Composable
private fun Hand(state: CardTableUiState, actions: CardTableActions, piles: PileTargets, onView: (Int) -> Unit, modifier: Modifier) {
    val colors = trayColors()
    val animate = state.settings.animationsEnabled
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val scroll = remember { Animatable(0f) }
    var maxScroll by remember { mutableFloatStateOf(0f) }
    var handPicked by remember { mutableStateOf(false) }
    var handBounds by remember { mutableStateOf(Rect.Zero) }
    val current by rememberUpdatedState(actions)
    val haptic by rememberUpdatedState(state.settings.hapticsEnabled)
    val hand = state.hand
    val handEmpty by rememberUpdatedState(hand.isEmpty())
    // The hand's rightmost (newest) cards, shown as a small pile while the whole hand is carried.
    val handTop by rememberUpdatedState(hand.takeLast(CARRIED_CARDS).mapNotNull(state::face))
    val handLabel = stringResource(R.string.cards_hand, hand.size)
    val discardHandLabel = stringResource(R.string.cards_action_discard_hand)
    val handToDeckLabel = stringResource(R.string.cards_action_hand_to_deck)
    BoxWithConstraints(
        modifier
            .testTag(HAND_TAG)
            .onGloballyPositioned { handBounds = it.boundsInRoot() }
            .pointerInput(Unit) {
                // Long-press anywhere on the hand: pick up the whole hand (wins over the cards' own gestures).
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val slop = viewConfiguration.touchSlop
                    // Non-null when the finger lifts or moves before the long-press timeout: not a long press.
                    val ended = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                        var done = false
                        while (!done) {
                            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id }
                            done = change == null || !change.pressed || (change.position - down.position).getDistance() > slop
                        }
                        true
                    }
                    if (ended != null || handEmpty) return@awaitEachGesture
                    handPicked = true
                    piles.carried = handTop
                    piles.carriedAt = handBounds.topLeft + down.position
                    if (haptic) haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                    var offset = Offset.Zero
                    var position = down.position
                    while (true) {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        change.consume()
                        if (!change.pressed) break
                        offset += change.positionChange()
                        position = change.position
                        piles.carriedAt = handBounds.topLeft + position
                        piles.hover = piles.targetAt(piles.carriedAt)
                    }
                    val target = piles.hover
                    piles.hover = null
                    handPicked = false
                    piles.carried = emptyList()
                    when (target) {
                        PileTarget.DRAW -> current.handToDraw()
                        PileTarget.DISCARD -> current.discardHand()
                        null -> Unit
                    }
                }
            }
            .pointerInput(Unit) {
                // Sideways drags scroll a hand that is wider than the table.
                val tracker = VelocityTracker()
                detectHorizontalDragGestures(
                    onDragStart = {
                        tracker.resetTracking()
                        scope.launch { scroll.stop() }
                    },
                    onHorizontalDrag = { change, amount ->
                        if (maxScroll > 0f) {
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            scope.launch { scroll.snapTo((scroll.value - amount).coerceIn(0f, maxScroll)) }
                        }
                    },
                    onDragEnd = {
                        val velocity = tracker.calculateVelocity().x
                        if (maxScroll > 0f) scope.launch { scroll.animateDecay(-velocity, splineBasedDecay(density)) }
                    },
                )
            }
            .semantics {
                contentDescription = handLabel
                if (hand.isNotEmpty()) {
                    customActions = listOf(
                        CustomAccessibilityAction(discardHandLabel) { current.discardHand(); true },
                        CustomAccessibilityAction(handToDeckLabel) { current.handToDraw(); true },
                    )
                }
            },
    ) {
        if (hand.isEmpty()) {
            Text(
                stringResource(R.string.cards_hand_empty),
                color = colors.muted,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        val count = hand.size
        // Card size: the hand's height (minus room for the arc and tilt), capped by the width. Bigger hands
        // first shrink the cards (down to MIN_CARD of the width) so they still fit with every value visible;
        // only then does the hand scroll.
        val fullWidth = (maxHeight * 0.84f * CARD_ASPECT).coerceAtMost(maxWidth * 0.42f)
        val fitWidth = maxWidth * 0.86f / (1 + MIN_VISIBLE * (count - 1).coerceAtLeast(0))
        val cardWidth = fitWidth.coerceIn(minOf(maxWidth * MIN_CARD, fullWidth), fullWidth)
        val cardHeight = cardWidth / CARD_ASPECT
        val tiltRoom = cardWidth * 0.07f
        val fitStep = if (count <= 1) 0.dp else (maxWidth - tiltRoom * 2 - cardWidth) / (count - 1)
        val minStep = cardWidth * MIN_VISIBLE
        val scrolls = count > 1 && fitStep < minStep
        val step = if (scrolls) minStep else fitStep.coerceAtMost(cardWidth * 1.04f)
        val total = cardWidth + step * (count - 1)
        val start = if (scrolls) 0.dp else (maxWidth - total) / 2
        val arcMax = if (scrolls) 0.dp else cardHeight * 0.06f
        val top = maxHeight - cardHeight - arcMax - (if (scrolls) 0.dp else tiltRoom)
        val overflow = with(density) { (total - maxWidth).coerceAtLeast(0.dp).toPx() }
        // Keep the scroll range in step with the hand, and bring a newly drawn card into view.
        var lastCount by remember { mutableIntStateOf(count) }
        LaunchedEffect(overflow, count) {
            maxScroll = overflow
            scroll.updateBounds(0f, overflow)
            if (count > lastCount && overflow > 0f) {
                if (animate) scroll.animateTo(overflow, tween(250)) else scroll.snapTo(overflow)
            } else if (scroll.value > overflow) {
                scroll.snapTo(overflow)
            }
            lastCount = count
        }
        val fan = if (scrolls) 0f else (24f / count.coerceAtLeast(1)).coerceAtMost(4f)
        val half = (count - 1) / 2f
        hand.forEachIndexed { index, card ->
            val face = state.face(card)
            if (face != null) key(card) {
                val centre = index - half
                val arc = if (half == 0f) 0.dp else arcMax * (centre * centre) / (half * half)
                HandCard(
                    card = card,
                    face = face,
                    animate = animate,
                    onDiscard = { current.discard(it) },
                    onPutOnDraw = { current.putOnDraw(it) },
                    piles = piles,
                    onView = onView,
                    modifier = Modifier
                        .offset(x = start + step * index, y = top + arc)
                        .width(cardWidth)
                        .graphicsLayer {
                            translationX = -scroll.value
                            rotationZ = centre * fan
                            // While the hand is carried, it is shown as the pile under the finger instead.
                            alpha = if (handPicked) 0f else 1f
                        },
                )
            }
        }
    }
}

/**
 * A card in the hand. Tap: full-screen view. A drag that starts upward or downward belongs to the card:
 * dropped on the draw pile it goes on top of the deck, on the discard pile on top of the discard;
 * released after moving up ≥ 64 dp elsewhere it is discarded; otherwise it springs back. A drag that
 * starts sideways scrolls the hand.
 */
@Composable
private fun HandCard(
    card: Int,
    face: CardFace,
    animate: Boolean,
    onDiscard: (Int) -> Unit,
    onPutOnDraw: (Int) -> Unit,
    piles: PileTargets,
    onView: (Int) -> Unit,
    modifier: Modifier,
) {
    val scope = rememberCoroutineScope()
    val drag = remember { Animatable(Offset.Zero, Offset.VectorConverter) }
    var dragging by remember { mutableStateOf(false) }
    var bounds by remember { mutableStateOf(Rect.Zero) }
    val enter = remember { Animatable(if (animate) 0f else 1f) }
    LaunchedEffect(Unit) { if (animate) enter.animateTo(1f, tween(220)) }
    val discard by rememberUpdatedState(onDiscard)
    val putOnDraw by rememberUpdatedState(onPutOnDraw)
    val view by rememberUpdatedState(onView)
    val discardLabel = stringResource(R.string.cards_action_discard)
    val returnLabel = stringResource(R.string.cards_action_return)
    val viewLabel = stringResource(R.string.cards_action_view)
    PlayingCard(
        face,
        modifier
            .zIndex(if (dragging) 1f else 0f)
            .testTag("$HAND_CARD_TAG$card")
            .onGloballyPositioned { if (!dragging) bounds = it.boundsInRoot() }
            .graphicsLayer {
                translationX = drag.value.x
                translationY = drag.value.y - (1f - enter.value) * 120.dp.toPx()
                alpha = enter.value
                if (dragging) {
                    scaleX = 1.06f
                    scaleY = 1.06f
                }
            }
            .pointerInput(card) { detectTapGestures(onTap = { view(card) }) }
            .pointerInput(card) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    var start = Offset.Zero
                    // Claim only mostly-vertical drags; sideways ones scroll the hand.
                    val first = awaitTouchSlopOrCancellation(down.id) { change, over ->
                        if (abs(over.y) > abs(over.x)) {
                            change.consume()
                            start = over
                        }
                    }
                    if (first != null) {
                        val origin = bounds.center
                        dragging = true
                        var offset = start
                        scope.launch { drag.snapTo(offset) }
                        val finished = drag(first.id) { change ->
                            offset += change.positionChange()
                            change.consume()
                            scope.launch { drag.snapTo(offset) }
                            piles.hover = piles.targetAt(origin + offset)
                        }
                        val target = piles.hover
                        piles.hover = null
                        dragging = false
                        when {
                            finished && target == PileTarget.DRAW -> putOnDraw(card)
                            finished && target == PileTarget.DISCARD -> discard(card)
                            finished && offset.y <= -SWIPE_UP.toPx() -> discard(card)
                            else -> scope.launch { if (animate) drag.animateTo(Offset.Zero, spring()) else drag.snapTo(Offset.Zero) }
                        }
                    }
                }
            }
            .semantics {
                onClick(viewLabel) { view(card); true }
                customActions = listOf(
                    CustomAccessibilityAction(discardLabel) { discard(card); true },
                    CustomAccessibilityAction(returnLabel) { putOnDraw(card); true },
                )
            },
    )
}

/** The picture shared by "Share as image": the deck name and the hand, laid out on the theme's table. */
@Composable
private fun HandSnapshot(state: CardTableUiState) {
    val shape = MaterialTheme.shapes.extraLarge
    val inset = LocalThemeStyle.current.tray.contentInset
    Column(
        Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.background).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(state.deck?.let { deckTitle(it) }.orEmpty(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onBackground)
        FlowRow(
            Modifier.fillMaxWidth().themedTray(shape).padding(16.dp + inset),
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            state.hand.mapNotNull(state::face).forEach { face -> PlayingCard(face, Modifier.width(SNAPSHOT_CARD)) }
        }
    }
}

/** Cards being moved together, shown as a small fanned pile: the top card last and in front. */
@Composable
private fun CarriedPile(faces: List<CardFace>, modifier: Modifier) {
    Box(modifier) {
        val half = (faces.size - 1) / 2f
        faces.forEachIndexed { index, face ->
            PlayingCard(
                face,
                Modifier
                    .fillMaxWidth(0.86f)
                    .offset(x = (index * 8).dp, y = (index * 5).dp)
                    .graphicsLayer { rotationZ = (index - half) * 5f },
                elevation = 6.dp,
            )
        }
    }
}

@Composable
private fun ActionButtons(state: CardTableUiState, actions: CardTableActions) {
    FlowRow(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        FilledTonalButton(onClick = actions.draw, enabled = state.drawCount > 0) { Text(stringResource(R.string.cards_action_draw)) }
        OutlinedButton(onClick = actions.shuffleDraw, enabled = state.drawCount > 1) { Text(stringResource(R.string.cards_action_shuffle)) }
        OutlinedButton(onClick = actions.takeFromDiscard, enabled = state.discardCount > 0) { Text(stringResource(R.string.cards_action_take_discard)) }
        OutlinedButton(onClick = actions.shuffleDiscard, enabled = state.discardCount > 1) { Text(stringResource(R.string.cards_action_shuffle_discard)) }
        OutlinedButton(onClick = actions.discardOntoDraw, enabled = state.discardCount > 0) { Text(stringResource(R.string.cards_action_discard_into_deck)) }
        OutlinedButton(onClick = actions.discardHand, enabled = state.hand.isNotEmpty()) { Text(stringResource(R.string.cards_action_discard_hand)) }
        OutlinedButton(onClick = actions.handToDraw, enabled = state.hand.isNotEmpty()) { Text(stringResource(R.string.cards_action_hand_to_deck)) }
    }
}

/** A card filling the screen; tap anywhere (or back) to close. */
@Composable
private fun CardViewer(face: CardFace, showButtons: Boolean, onDiscard: () -> Unit, onPutOnDraw: () -> Unit, onClose: () -> Unit) {
    Dialog(onDismissRequest = onClose, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.85f))
                .clickable(onClickLabel = stringResource(R.string.cards_close), onClick = onClose)
                .padding(24.dp),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                PlayingCard(face, Modifier.widthIn(max = 420.dp).fillMaxWidth().testTag(VIEWER_TAG), elevation = 8.dp)
                if (showButtons) {
                    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        FilledTonalButton(onClick = onDiscard) { Text(stringResource(R.string.cards_action_discard)) }
                        FilledTonalButton(onClick = onPutOnDraw) { Text(stringResource(R.string.cards_action_return)) }
                    }
                }
            }
        }
    }
}

/** The deck's display name: presets are named by size ("52 cards"), others by their own name. */
@Composable
fun deckTitle(deck: DeckDefinition): String =
    if (deck.kind == DeckKind.PRESET) androidx.compose.ui.res.pluralStringResource(R.plurals.cards_preset_name, deck.cards.size, deck.cards.size) else deck.name

internal val CARDS_HELP = listOf(
    HelpTopic(R.string.help_cards_draw_title, R.string.help_cards_draw_text),
    HelpTopic(R.string.help_cards_shuffle_title, R.string.help_cards_shuffle_text),
    HelpTopic(R.string.help_cards_discard_title, R.string.help_cards_discard_text),
    HelpTopic(R.string.help_cards_hand_title, R.string.help_cards_hand_text),
    HelpTopic(R.string.help_cards_scroll_title, R.string.help_cards_scroll_text),
    HelpTopic(R.string.help_cards_take_title, R.string.help_cards_take_text),
    HelpTopic(R.string.help_cards_return_title, R.string.help_cards_return_text),
    HelpTopic(R.string.help_cards_view_title, R.string.help_cards_view_text),
    HelpTopic(R.string.help_cards_buttons_title, R.string.help_cards_buttons_text),
    HelpTopic(R.string.help_cards_reset_title, R.string.help_cards_reset_text),
)

private val SWIPE_UP = 64.dp

/** At least this share of each card (its value corner) stays visible in a crowded hand. */
private const val MIN_VISIBLE = 0.3f

/** Cards in a big hand never get narrower than this share of the table width. */
private const val MIN_CARD = 0.26f
internal const val DRAW_PILE_TAG = "drawPile"
internal const val DISCARD_PILE_TAG = "discardPile"
internal const val HAND_TAG = "hand"
internal const val HAND_CARD_TAG = "handCard-"
internal const val VIEWER_TAG = "cardViewer"
internal const val CARRIED_TAG = "carriedCards"

private val SNAPSHOT_CARD = 96.dp

/** How many cards are shown when several are carried (the hand or the discard pile). */
private const val CARRIED_CARDS = 3
