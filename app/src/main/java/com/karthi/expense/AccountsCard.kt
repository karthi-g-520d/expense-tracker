package com.karthi.expense

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.karthi.expense.core.Bank
import com.karthi.expense.data.AccountSettings

/** Your own bank accounts and cards: only SMS about these become cards. */
@Composable
fun AccountsCard() {
    var adding by remember { mutableStateOf(false) }
    val accounts = AccountSettings.accounts

    Box(Modifier.fillMaxWidth().softCard()) {
        Column(Modifier.padding(16.dp).animateContentSize(fluidSpring()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("My bank accounts", style = MaterialTheme.typography.titleMedium)
            Text(
                if (accounts.isEmpty()) {
                    "Every bank SMS shows up for now. Add your banks, with the digits their SMS show " +
                        "(like 1234 in \"A/c XX1234\"), so only your own accounts and cards do."
                } else {
                    "Only SMS about these show up. Add credit and debit cards too: their SMS show the card's digits."
                },
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            accounts.forEachIndexed { i, a ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
                        Text(a.label, style = MaterialTheme.typography.bodyLarge)
                        Text(
                            when {
                                a.digits.isEmpty() -> "Any account in this bank"
                                a.fromStart -> "Number starts with ${a.digits}"
                                else -> "Number ends with ${a.digits}"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { AccountSettings.remove(a) }) {
                        Icon(Icons.Filled.Close, contentDescription = "Remove")
                    }
                }
            }

            OutlinedButton(onClick = { adding = true }) { Text("+ Add account") }
        }
    }

    if (adding) AddAccountDialog(onDismiss = { adding = false })
}

@Composable
private fun AddAccountDialog(onDismiss: () -> Unit) {
    var bank by remember { mutableStateOf<Bank?>(null) }
    var digits by remember { mutableStateOf("") }
    var fromStart by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }
    var duplicate by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add account") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box {
                    OutlinedButton(onClick = { picking = true }, modifier = Modifier.fillMaxWidth()) {
                        Text(bank?.label ?: "Choose bank", Modifier.weight(1f))
                        Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
                    }
                    DropdownMenu(expanded = picking, onDismissRequest = { picking = false }, modifier = Modifier.heightIn(max = 360.dp)) {
                        Bank.selectable.forEach { b ->
                            DropdownMenuItem(text = { Text(b.label) }, onClick = {
                                bank = b
                                picking = false
                            })
                        }
                    }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CategoryChip("Last digits", selected = !fromStart) { fromStart = false }
                    CategoryChip("First digits", selected = fromStart) { fromStart = true }
                }
                OutlinedTextField(
                    value = digits,
                    onValueChange = { v ->
                        digits = v.filter { it.isDigit() }.take(18)
                        duplicate = false
                    },
                    label = { Text(if (fromStart) "First digits of the number" else "Last digits, e.g. 1234") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    isError = duplicate,
                    supportingText = {
                        Text(if (duplicate) "Already added" else "Leave empty to include every account in this bank")
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val b = bank ?: return@TextButton
                    if (AccountSettings.add(b, digits, fromStart)) onDismiss() else duplicate = true
                },
                enabled = bank != null,
            ) { Text("Add") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
