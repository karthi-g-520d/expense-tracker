package com.karthi.expense

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karthi.expense.data.Categories
import com.karthi.expense.data.SheetTxn
import java.text.NumberFormat
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale

private val rupees: NumberFormat = NumberFormat.getCurrencyInstance(Locale("en", "IN"))
private val monthTitle = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)

private data class CategoryTotal(val category: String, val total: Double, val txns: List<SheetTxn>)

private fun totalsByCategory(rows: List<SheetTxn>) = rows
    .groupBy { it.category ?: "Uncategorised" }
    .map { (c, txns) -> CategoryTotal(c, txns.sumOf { it.amount }, txns) }
    .sortedByDescending { it.total }

/** Monthly statement from the sheet: spent, received, and where the money went. */
@Composable
fun DashboardScreen(vm: MainViewModel) {
    LaunchedEffect(Unit) { vm.refreshDashboard() }

    val all by vm.sheetRows.collectAsState()
    var month by remember { mutableStateOf(YearMonth.now()) }
    val rows = all.filter { it.date.startsWith(month.toString()) }
    val debits = rows.filter { it.type == "DEBIT" }
    val credits = rows.filter { it.type == "CREDIT" }
    val spent = debits.sumOf { it.amount }
    val received = credits.sumOf { it.amount }

    LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = { month = month.minusMonths(1) }) { Text("‹") }
                Text(month.format(monthTitle), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                TextButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.now()) { Text("›") }
                TextButton(onClick = vm::refreshDashboard, enabled = !vm.refreshing) {
                    Text(if (vm.refreshing) "Loading…" else "Refresh")
                }
            }
            vm.refreshError?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
        }

        item {
            Card(
                Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
            ) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Spent", style = MaterialTheme.typography.labelMedium)
                    Text(rupees.format(spent), style = MaterialTheme.typography.headlineMedium)
                    Text("Received ${rupees.format(received)} · Net ${rupees.format(received - spent)}")
                }
            }
        }

        if (rows.isEmpty()) {
            item { Text(if (vm.refreshing) "Loading…" else "Nothing synced for this month yet.") }
        }

        if (debits.isNotEmpty()) {
            item { Text("Where it went", style = MaterialTheme.typography.titleMedium) }
            items(totalsByCategory(debits), key = { "d-" + it.category }) { CategoryRow(it, spent) }
        }
        if (credits.isNotEmpty()) {
            item { Text("Money in", style = MaterialTheme.typography.titleMedium) }
            items(totalsByCategory(credits), key = { "c-" + it.category }) { CategoryRow(it, received) }
        }
    }
}

/** One category with its share of the month as a bar, like a data-usage breakdown. Tap to list its transactions. */
@Composable
private fun CategoryRow(c: CategoryTotal, monthTotal: Double) {
    var expanded by remember(c.category) { mutableStateOf(false) }
    val share = if (monthTotal > 0) (c.total / monthTotal).toFloat() else 0f

    Column(Modifier.fillMaxWidth().clickable { expanded = !expanded }.padding(vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth()) {
            Text(Categories.label(c.category), modifier = Modifier.weight(1f))
            Text("${rupees.format(c.total)}  ${(share * 100).toInt()}%")
        }
        LinearProgressIndicator(progress = { share }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
        if (expanded) {
            c.txns.forEach { t ->
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 6.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(t.counterparty ?: "Unknown", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            listOfNotNull(t.date, t.reason).joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Text(rupees.format(t.amount), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}
