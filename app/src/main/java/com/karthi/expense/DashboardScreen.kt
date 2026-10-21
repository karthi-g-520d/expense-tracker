package com.karthi.expense

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.style.TextAlign
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
fun DashboardScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    LaunchedEffect(Unit) { vm.refreshDashboard() }

    val all by vm.sheetRows.collectAsState()
    var month by remember { mutableStateOf(YearMonth.now()) }
    val rows = all.filter { it.date.startsWith(month.toString()) }
    val debits = rows.filter { it.type == "DEBIT" }
    val credits = rows.filter { it.type == "CREDIT" }
    val spent = debits.sumOf { it.amount }
    val received = credits.sumOf { it.amount }
    val shownSpent by animateFloatAsState(spent.toFloat(), fluidSpring(), label = "spent")

    LazyColumn(Modifier.fillMaxHeight(), contentPadding = contentPadding, verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { month = month.minusMonths(1) }) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = "Previous month")
                }
                AnimatedContent(
                    targetState = month,
                    transitionSpec = {
                        val dir = if (targetState > initialState) 1 else -1
                        (slideInHorizontally(fluidSpring()) { dir * it / 2 } + fadeIn(fluidSpring())) togetherWith
                            (slideOutHorizontally(fluidSpring()) { -dir * it / 2 } + fadeOut(fluidSpring()))
                    },
                    modifier = Modifier.weight(1f),
                    contentAlignment = Alignment.Center,
                    label = "month",
                ) { m ->
                    Text(m.format(monthTitle), style = MaterialTheme.typography.titleMedium, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
                }
                IconButton(onClick = { month = month.plusMonths(1) }, enabled = month < YearMonth.now()) {
                    Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Next month")
                }
            }
        }

        item {
            Column(Modifier.fillMaxWidth().softCard().padding(20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        "SPENT",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = vm::refreshDashboard, enabled = !vm.refreshing) {
                        Text(if (vm.refreshing) "Updating…" else "Refresh")
                    }
                }
                Text(rupees.format(shownSpent.toDouble()), style = MaterialTheme.typography.headlineMedium)
                Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                    Stat("Received", rupees.format(received), CreditGreen)
                    Stat("Net", rupees.format(received - spent), if (received >= spent) CreditGreen else DebitRed)
                }
                vm.refreshError?.let {
                    Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 6.dp))
                }
            }
        }

        if (rows.isEmpty()) {
            item {
                Text(
                    if (vm.refreshing) "Loading…" else "Nothing synced for this month yet.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 4.dp),
                )
            }
        }

        if (debits.isNotEmpty()) {
            item(key = "debits-$month") { CategoryGroup("WHERE IT WENT", totalsByCategory(debits), spent, Modifier.animateItem()) }
        }
        if (credits.isNotEmpty()) {
            item(key = "credits-$month") { CategoryGroup("MONEY IN", totalsByCategory(credits), received, Modifier.animateItem()) }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, color: Color) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.titleMedium, color = color)
    }
}

/** iOS inset-grouped list: a section title, then one card holding every category row. */
@Composable
private fun CategoryGroup(title: String, totals: List<CategoryTotal>, monthTotal: Double, modifier: Modifier = Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 4.dp),
        )
        Column(Modifier.fillMaxWidth().softCard()) {
            totals.forEachIndexed { i, c ->
                if (i > 0) HorizontalDivider(Modifier.padding(start = 16.dp), thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                CategoryRow(c, monthTotal)
            }
        }
    }
}

/** One category with its share of the month as a bar, like a data-usage breakdown. Tap to list its transactions. */
@Composable
private fun CategoryRow(c: CategoryTotal, monthTotal: Double) {
    var expanded by remember(c.category) { mutableStateOf(false) }
    val share = if (monthTotal > 0) (c.total / monthTotal).toFloat() else 0f
    val bar by animateFloatAsState(share, fluidSpring(), label = "share")
    val chevron by animateFloatAsState(if (expanded) 90f else 0f, fluidSpring(), label = "chevron")

    Column(
        Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(Categories.label(c.category), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
            Text(rupees.format(c.total), style = MaterialTheme.typography.bodyLarge)
            Text(
                "  ${(share * 100).toInt()}%",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.rotate(chevron),
            )
        }
        LinearProgressIndicator(
            progress = { bar },
            modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(4.dp),
            strokeCap = StrokeCap.Round,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            gapSize = 0.dp,
            drawStopIndicator = {},
        )
        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(fluidSpring()) + fadeIn(fluidSpring()),
            exit = shrinkVertically(fluidSpring()) + fadeOut(fluidSpring()),
        ) {
            Column(Modifier.padding(top = 6.dp)) {
                c.txns.forEach { t ->
                    Row(Modifier.fillMaxWidth().padding(top = 8.dp)) {
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
}
