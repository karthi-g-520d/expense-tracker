package com.karthi.expense

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.karthi.expense.data.Categories
import com.karthi.expense.data.PendingTxn

/** One transaction: payee and amount, a single scrolling row of categories, and a reason box. */
@Composable
fun TxnCard(t: PendingTxn, onTag: (category: String?, reason: String?) -> Unit) {
    // Local state so typing isn't interrupted when the database re-emits the list.
    var category by remember(t.id) { mutableStateOf(t.category) }
    var reason by remember(t.id) { mutableStateOf(t.reason.orEmpty()) }
    val isDebit = t.type == "DEBIT"
    val tagged = category != null

    OutlinedCard(
        modifier = Modifier.fillMaxWidth(),
        border = BorderStroke(
            if (tagged) 2.dp else 1.dp,
            if (tagged) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
        ),
        colors = CardDefaults.outlinedCardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(Modifier.padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp), verticalAlignment = Alignment.CenterVertically) {
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
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Categories.forType(t.type).forEach { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = {
                            category = if (category == c) null else c
                            onTag(category, reason)
                        },
                        label = { Text(Categories.label(c)) },
                    )
                }
            }

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
                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp),
            )
        }
    }
}
