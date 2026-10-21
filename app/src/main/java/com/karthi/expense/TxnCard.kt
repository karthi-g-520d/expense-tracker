package com.karthi.expense

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karthi.expense.core.Shares
import com.karthi.expense.data.Categories
import com.karthi.expense.data.PendingTxn
import java.math.BigDecimal

/** One person's part of a transaction while it's being edited; amount stays text so typing isn't fought. */
private data class PersonShare(val name: String, val amount: String)

/**
 * One transaction: payee and amount, a scrolling row of categories, an optional note, and an
 * optional split saying which part belongs to other people.
 */
@Composable
fun TxnCard(
    t: PendingTxn,
    knownPeople: List<String>,
    modifier: Modifier = Modifier,
    onTag: (category: String?, reason: String?, shares: String?) -> Unit,
) {
    // Local state so typing isn't interrupted when the database re-emits the list.
    var category by remember(t.id) { mutableStateOf(t.category) }
    var reason by remember(t.id) { mutableStateOf(t.reason.orEmpty()) }
    val people = remember(t.id) {
        mutableStateListOf<PersonShare>().apply {
            Shares.parse(t.shares).forEach { (name, amount) -> add(PersonShare(name, amount.stripTrailingZeros().toPlainString())) }
        }
    }
    var splitOpen by remember(t.id) { mutableStateOf(people.isNotEmpty()) }
    // While true, adding a person re-divides the bill equally; typing an amount turns it off.
    var autoEqual by remember(t.id) { mutableStateOf(people.isEmpty()) }
    val focus = remember { FocusRequester() }
    val isDebit = t.type == "DEBIT"
    val total = remember(t.amount) { BigDecimal(t.amount) }

    // Read from the live list each time, so a save right after an edit sees that edit.
    fun shareMap() = people
        .filter { it.name.isNotBlank() }
        .associate { Shares.cleanName(it.name) to (it.amount.toBigDecimalOrNull() ?: BigDecimal.ZERO) }
    fun isAllTheirs(map: Map<String, BigDecimal>) = map.isNotEmpty() && Shares.myPart(total, map).signum() == 0

    val shareMap = shareMap()
    val mine = Shares.myPart(total, shareMap)
    val allTheirs = isAllTheirs(shareMap)

    fun save() {
        val current = shareMap()
        val sharesText = Shares.format(current)
        // The whole amount is someone else's: no category of yours to pick.
        category = when {
            isAllTheirs(current) -> Categories.NOT_MINE
            category == Categories.NOT_MINE -> null
            else -> category
        }
        onTag(category, reason, sharesText)
    }

    fun splitEqually() {
        val each = Shares.splitEqually(total, people.size).stripTrailingZeros().toPlainString()
        people.indices.forEach { people[it] = people[it].copy(amount = each) }
    }

    fun addPerson(name: String) {
        val firstOnCredit = !isDebit && people.isEmpty()
        people.add(PersonShare(name, if (firstOnCredit) t.amount else ""))
        if (isDebit && autoEqual) splitEqually()
        save()
    }

    // The system picker hands back just the chosen contact, so no contacts permission is needed.
    val context = LocalContext.current
    val pickContact = rememberLauncherForActivityResult(ActivityResultContracts.PickContact()) { uri ->
        val name = uri?.let { contactName(context, it) } ?: return@rememberLauncherForActivityResult
        addPerson(Shares.cleanName(name))
    }

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
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        (if (isDebit) "−₹" else "+₹") + t.amount,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDebit) DebitRed else CreditGreen,
                    )
                    if (shareMap.isNotEmpty()) {
                        Text(
                            "yours ₹${mine.stripTrailingZeros().toPlainString()}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (!allTheirs) {
                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Categories.forType(t.type).forEach { c ->
                        CategoryChip(Categories.label(c.name), selected = category == c.name) {
                            category = if (category == c.name) null else c.name
                            save()
                        }
                    }
                }
            }

            AnimatedVisibility(
                visible = splitOpen,
                enter = expandVertically(fluidSpring()) + fadeIn(fluidSpring()),
                exit = shrinkVertically(fluidSpring()) + fadeOut(fluidSpring()),
            ) {
                SplitEditor(
                    isDebit = isDebit,
                    total = total,
                    mine = mine,
                    people = people,
                    suggestions = (listOfNotNull(t.counterparty.takeIf { !isDebit }?.let(Shares::cleanName)) + knownPeople)
                        .distinctBy { it.lowercase() }
                        .filter { s -> people.none { it.name.equals(s, ignoreCase = true) } }
                        .take(6),
                    onAdd = ::addPerson,
                    onPickContact = { pickContact.launch(null) },
                    onChange = { i, p ->
                        if (p.amount != people[i].amount) autoEqual = false
                        people[i] = p
                        save()
                    },
                    onRemove = { i ->
                        people.removeAt(i)
                        if (isDebit && autoEqual && people.isNotEmpty()) splitEqually()
                        if (people.isEmpty()) splitOpen = false
                        save()
                    },
                    onSplitEqually = {
                        autoEqual = true
                        splitEqually()
                        save()
                    },
                    onAllTheirs = {
                        people[0] = people[0].copy(amount = t.amount)
                        autoEqual = false
                        save()
                    },
                )
            }

            // A text field per card is the heaviest thing in the list, so it's only created
            // once the user asks for a note (or the card already has one).
            var editing by remember(t.id) { mutableStateOf(false) }
            if (editing || reason.isNotEmpty()) {
                OutlinedTextField(
                    value = reason,
                    onValueChange = {
                        reason = it
                        save()
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
            }
            if (!(editing || reason.isNotEmpty()) || !splitOpen) {
                Row(Modifier.padding(horizontal = 4.dp)) {
                    if (!(editing || reason.isNotEmpty())) {
                        TextButton(onClick = { editing = true }) { Text("+ Add a note") }
                    }
                    if (!splitOpen) {
                        TextButton(onClick = { splitOpen = true }) {
                            Text(if (isDebit) "👥 Split / paid for others" else "👥 From / for someone")
                        }
                    }
                }
            }
        }
    }
}

/** People and their parts of this transaction, with name suggestions and your remaining share. */
@Composable
private fun SplitEditor(
    isDebit: Boolean,
    total: BigDecimal,
    mine: BigDecimal,
    people: List<PersonShare>,
    suggestions: List<String>,
    onAdd: (String) -> Unit,
    onPickContact: () -> Unit,
    onChange: (Int, PersonShare) -> Unit,
    onRemove: (Int) -> Unit,
    onSplitEqually: () -> Unit,
    onAllTheirs: () -> Unit,
) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            if (isDebit) "Who else is this for?" else "Who is this money from or for?",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        people.forEachIndexed { i, p ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = p.name,
                    onValueChange = { onChange(i, p.copy(name = it)) },
                    placeholder = { Text("Name") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Next),
                    modifier = Modifier.weight(1f),
                )
                OutlinedTextField(
                    value = p.amount,
                    onValueChange = { v -> if (v.isEmpty() || v.matches(Regex("\\d*\\.?\\d{0,2}"))) onChange(i, p.copy(amount = v)) },
                    prefix = { Text("₹") },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                    modifier = Modifier.width(118.dp),
                )
                IconButton(onClick = { onRemove(i) }) { Icon(Icons.Filled.Close, contentDescription = "Remove") }
            }
        }

        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            CategoryChip("📇 Contacts", selected = false, onClick = onPickContact)
            suggestions.forEach { name -> CategoryChip("+ $name", selected = false) { onAdd(name) } }
            CategoryChip("+ Type a name", selected = false) { onAdd("") }
        }

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                when {
                    mine.signum() < 0 -> "That's more than ₹${total.stripTrailingZeros().toPlainString()}"
                    people.isEmpty() -> "Pick a person above"
                    mine.signum() == 0 -> "None of it is yours"
                    else -> "Your share ₹${mine.stripTrailingZeros().toPlainString()}"
                },
                style = MaterialTheme.typography.bodyMedium,
                color = if (mine.signum() < 0) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.weight(1f),
            )
            if (isDebit && people.isNotEmpty()) TextButton(onClick = onSplitEqually) { Text("Split equally") }
            if (people.size == 1) TextButton(onClick = onAllTheirs) { Text("All theirs") }
        }
    }
}

private fun contactName(context: Context, uri: Uri): String? =
    runCatching {
        context.contentResolver.query(uri, arrayOf(ContactsContract.Contacts.DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }.getOrNull()?.takeIf { it.isNotBlank() }

/** Capsule that fills with the accent colour when picked; shrinks a touch while pressed. */
@Composable
internal fun CategoryChip(label: String, selected: Boolean, onClick: () -> Unit) {
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
