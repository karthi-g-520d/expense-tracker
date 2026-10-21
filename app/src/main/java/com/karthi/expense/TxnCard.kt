package com.karthi.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.karthi.expense.data.Categories
import com.karthi.expense.data.PendingTxn

private val DebitRed = Color(0xFFC62828)
private val CreditGreen = Color(0xFF2E7D32)

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TxnCard(t: PendingTxn, onTag: (category: String?, reason: String?) -> Unit) {
    // Local state so typing isn't interrupted when the database re-emits the list.
    var category by remember(t.id) { mutableStateOf(t.category) }
    var reason by remember(t.id) { mutableStateOf(t.reason.orEmpty()) }
    val isDebit = t.type == "DEBIT"

    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(t.counterparty ?: "Unknown", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                Text(
                    (if (isDebit) "− ₹" else "+ ₹") + t.amount,
                    style = MaterialTheme.typography.titleMedium,
                    color = if (isDebit) DebitRed else CreditGreen,
                )
            }
            Text("${t.date} · ${t.bank} ${t.account}", style = MaterialTheme.typography.bodySmall)

            FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Categories.forType(t.type).forEach { c ->
                    FilterChip(
                        selected = category == c,
                        onClick = {
                            category = if (category == c) null else c
                            onTag(category, reason)
                        },
                        label = { Text(c) },
                    )
                }
            }

            OutlinedTextField(
                value = reason,
                onValueChange = {
                    reason = it
                    onTag(category, it)
                },
                label = { Text("Reason (optional)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}
