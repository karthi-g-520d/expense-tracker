package com.karthi.expense

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karthi.expense.data.Categories
import com.karthi.expense.data.PendingTxn

/** One transaction: payee and amount, a single scrolling row of categories, and an optional note. */
@Composable
fun TxnCard(t: PendingTxn, modifier: Modifier = Modifier, onTag: (category: String?, reason: String?) -> Unit) {
    // Local state so typing isn't interrupted when the database re-emits the list.
    var category by remember(t.id) { mutableStateOf(t.category) }
    var reason by remember(t.id) { mutableStateOf(t.reason.orEmpty()) }
    val focus = remember { FocusRequester() }
    val isDebit = t.type == "DEBIT"
    val tagged = category != null

    val highlight by animateColorAsState(
        if (tagged) MaterialTheme.colorScheme.primary.copy(alpha = 0.55f) else Color.Transparent,
        fluidSpring(),
        label = "tagged",
    )

    Box(modifier.fillMaxWidth().softCard(highlight = highlight)) {
        Column(Modifier.padding(vertical = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        t.counterparty ?: "Unknown",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${t.bank} ••${t.account}",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    (if (isDebit) "−₹" else "+₹") + t.amount,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (isDebit) DebitRed else CreditGreen,
                )
            }

            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Categories.forType(t.type).forEach { c ->
                    CategoryChip(Categories.label(c), selected = category == c) {
                        category = if (category == c) null else c
                        onTag(category, reason)
                    }
                }
            }

            // A text field per card is the heaviest thing in the list, so it's only created
            // once the user asks for a note (or the card already has one).
            var editing by remember(t.id) { mutableStateOf(false) }
            if (editing || reason.isNotEmpty()) {
                OutlinedTextField(
                    value = reason,
                    onValueChange = {
                        reason = it
                        onTag(category, it)
                    },
                    placeholder = { Text("Add a note (optional)") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.Sentences,
                        imeAction = ImeAction.Done,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).focusRequester(focus),
                )
                LaunchedEffect(editing) { if (editing && reason.isEmpty()) focus.requestFocus() }
            } else {
                TextButton(
                    onClick = { editing = true },
                    modifier = Modifier.padding(horizontal = 4.dp),
                ) { Text("+ Add a note") }
            }
        }
    }
}

/** Capsule that fills with the accent colour when picked; shrinks a touch while pressed. */
@Composable
private fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,
        fluidSpring(),
        label = "chip-bg",
    )
    val fg by animateColorAsState(
        if (selected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurface,
        fluidSpring(),
        label = "chip-fg",
    )
    val interaction = remember { MutableInteractionSource() }
    Text(
        label,
        style = MaterialTheme.typography.labelLarge,
        color = fg,
        modifier = Modifier
            .pressScale(interaction, 0.94f)
            .clip(CircleShape)
            .background(bg)
            .clickable(interaction, indication = null, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}
