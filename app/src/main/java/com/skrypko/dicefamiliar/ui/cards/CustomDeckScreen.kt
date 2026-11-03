package com.skrypko.dicefamiliar.ui.cards

import androidx.annotation.StringRes
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skrypko.dicefamiliar.R
import com.skrypko.dicefamiliar.domain.cards.StandardCards
import com.skrypko.dicefamiliar.domain.cards.Suit

@Composable
fun CustomDeckScreen(onDone: () -> Unit, viewModel: CustomDeckViewModel = hiltViewModel()) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(viewModel) { viewModel.saved.collect { onDone() } }
    CustomDeckContent(
        state = state,
        onBack = onDone,
        onNameChange = viewModel::onNameChange,
        onToggle = viewModel::toggle,
        onToggleGroup = viewModel::toggleGroup,
        onSave = viewModel::save,
    )
}

/** The 54 cards to pick from, grouped as jokers first, then suit by suit, each with an "All" toggle. */
internal val CARD_GROUPS: List<Pair<Int, List<String>>> =
    listOf(R.string.cards_custom_jokers to listOf(StandardCards.RED_JOKER, StandardCards.BLACK_JOKER)) +
        Suit.entries.map { suit ->
            val title = when (suit) {
                Suit.SPADES -> R.string.cards_suit_spades
                Suit.HEARTS -> R.string.cards_suit_hearts
                Suit.DIAMONDS -> R.string.cards_suit_diamonds
                Suit.CLUBS -> R.string.cards_suit_clubs
            }
            title to StandardCards.all54.filter { id -> id.endsWith(suit.code) && id != StandardCards.RED_JOKER && id != StandardCards.BLACK_JOKER }
        }

@Composable
fun CustomDeckContent(
    state: CustomDeckUiState,
    onBack: () -> Unit,
    onNameChange: (String) -> Unit,
    onToggle: (String) -> Unit,
    onToggleGroup: (List<String>) -> Unit,
    onSave: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxSize()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cards_custom_back))
            }
            Text(stringResource(R.string.cards_custom_title), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
            Button(onClick = onSave, enabled = state.canSave, modifier = Modifier.padding(end = 8.dp)) {
                Text(stringResource(R.string.cards_custom_save))
            }
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = state.name,
                onValueChange = onNameChange,
                label = { Text(stringResource(R.string.cards_custom_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                stringResource(R.string.cards_custom_selected, state.selected.size),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
            CARD_GROUPS.forEach { (title, ids) -> CardGroup(title, ids, state.selected, onToggle, onToggleGroup) }
        }
    }
}

@Composable
private fun CardGroup(
    @StringRes title: Int,
    ids: List<String>,
    selected: Set<String>,
    onToggle: (String) -> Unit,
    onToggleGroup: (List<String>) -> Unit,
) {
    val all = selected.containsAll(ids)
    val name = stringResource(title)
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .toggleable(value = all, role = Role.Checkbox, onValueChange = { onToggleGroup(ids) }),
    ) {
        Text(name, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        Text(stringResource(R.string.cards_custom_all), style = MaterialTheme.typography.labelLarge)
        Checkbox(checked = all, onCheckedChange = null, modifier = Modifier.padding(start = 4.dp))
    }
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        ids.forEach { id ->
            val face = StandardCards.face(id) ?: return@forEach
            val checked = id in selected
            Box(
                Modifier
                    .width(48.dp)
                    .toggleable(value = checked, role = Role.Checkbox, onValueChange = { onToggle(id) }),
            ) {
                PlayingCard(face, Modifier.fillMaxWidth().alpha(if (checked) 1f else 0.35f), elevation = 1.dp)
                if (checked) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .size(16.dp)
                            .border(1.dp, MaterialTheme.colorScheme.surface, CircleShape),
                    )
                } else {
                    Box(Modifier.matchParentSize().border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(4.dp)))
                }
            }
        }
    }
}
