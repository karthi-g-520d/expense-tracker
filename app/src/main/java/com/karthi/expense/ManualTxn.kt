package com.karthi.expense

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.karthi.expense.data.AccountSettings
import com.karthi.expense.data.PendingTxn
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

private val shortDate = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

/**
 * Add a transaction no SMS will ever show (cash, or one the bank never texted about).
 * It lands on To tag as an ordinary card, so category, note and split work the same way.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManualTxnDialog(
    onDismiss: () -> Unit,
    onAdd: (type: String, amount: BigDecimal, counterparty: String?, date: LocalDate, bank: String, account: String) -> Unit,
) {
    var debit by remember { mutableStateOf(true) }
    var amount by remember { mutableStateOf("") }
    var who by remember { mutableStateOf("") }
    val today = remember { LocalDate.now() }
    var date by remember { mutableStateOf(today) }
    var picking by remember { mutableStateOf(false) }
    // Cash first, then your own accounts from Settings.
    val sources = remember {
        listOf(Triple(PendingTxn.MANUAL_BANK, "", "💵 Cash")) +
            AccountSettings.accounts.map { Triple(it.bank.name, it.digits, "${it.bank.label} ••${it.digits}") }
    }
    var source by remember { mutableStateOf(sources.first()) }
    val value = amount.toBigDecimalOrNull()?.takeIf { it.signum() > 0 }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add a transaction") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryChip("Spent", selected = debit) { debit = true }
                    CategoryChip("Received", selected = !debit) { debit = false }
                }
                OutlinedTextField(
                    value = amount,
                    onValueChange = { v -> if (v.isEmpty() || v.matches(Regex("\\d*\\.?\\d{0,2}"))) amount = v },
                    prefix = { Text("₹") },
                    placeholder = { Text("Amount") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = who,
                    onValueChange = { who = it },
                    placeholder = { Text(if (debit) "Paid to (shop or person)" else "Received from") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                Text("When", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().chipRow(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryChip("Today", selected = date == today) { date = today }
                    CategoryChip("Yesterday", selected = date == today.minusDays(1)) { date = today.minusDays(1) }
                    val other = date != today && date != today.minusDays(1)
                    CategoryChip(if (other) "📅 ${date.format(shortDate)}" else "📅 Pick", selected = other) { picking = true }
                }
                if (sources.size > 1) {
                    Text("Paid with", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(Modifier.fillMaxWidth().chipRow(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        sources.forEach { s -> CategoryChip(s.third, selected = s == source) { source = s } }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = value != null,
                onClick = {
                    onAdd(if (debit) "DEBIT" else "CREDIT", value!!, who, date, source.first, source.second)
                    onDismiss()
                },
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )

    if (picking) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli(),
            selectableDates = object : androidx.compose.material3.SelectableDates {
                override fun isSelectableDate(utcTimeMillis: Long) =
                    !Instant.ofEpochMilli(utcTimeMillis).atZone(ZoneOffset.UTC).toLocalDate().isAfter(today)
            },
        )
        DatePickerDialog(
            onDismissRequest = { picking = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                    picking = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { picking = false }) { Text("Cancel") } },
        ) { DatePicker(state) }
    }
}
