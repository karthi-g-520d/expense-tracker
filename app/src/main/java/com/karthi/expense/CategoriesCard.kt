package com.karthi.expense

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karthi.expense.data.Categories

/** What the edit dialog is doing: adding a new category, or editing the one at [index]. */
private data class Editing(val index: Int?, val name: String, val icon: String)

/** Add, rename, reorder and remove the categories offered on cards, for spending and income separately. */
@Composable
fun CategoriesCard(vm: MainViewModel) {
    var type by rememberSaveable { mutableStateOf("DEBIT") }
    var editing by remember { mutableStateOf<Editing?>(null) }
    var confirmReset by remember { mutableStateOf(false) }
    val list = Categories.forType(type)

    Box(Modifier.fillMaxWidth().softCard()) {
        Column(Modifier.padding(16.dp).animateContentSize(fluidSpring()), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Categories", style = MaterialTheme.typography.titleMedium)
            Text(
                "Tap one to rename it or change its emoji. Rows already in the sheet keep their old name.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                CategoryChip("Spending", selected = type == "DEBIT") { type = "DEBIT" }
                CategoryChip("Income", selected = type == "CREDIT") { type = "CREDIT" }
            }

            list.forEachIndexed { i, c ->
                if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        Categories.label(c.name),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier
                            .weight(1f)
                            .clickable(enabled = c.name != Categories.SELF_TRANSFER) { editing = Editing(i, c.name, c.icon) }
                            .padding(vertical = 10.dp),
                    )
                    IconButton(onClick = { Categories.move(type, i, -1) }, enabled = i > 0) {
                        Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Move up")
                    }
                    IconButton(onClick = { Categories.move(type, i, 1) }, enabled = i < list.lastIndex) {
                        Icon(Icons.Filled.KeyboardArrowDown, contentDescription = "Move down")
                    }
                    // Self transfer is set automatically on import, so it can't be renamed or removed.
                    if (c.name != Categories.SELF_TRANSFER) {
                        IconButton(onClick = { Categories.remove(type, i) }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove")
                        }
                    } else {
                        Box(Modifier.width(48.dp))
                    }
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { editing = Editing(null, "", "") }) { Text("+ Add category") }
                TextButton(onClick = { confirmReset = true }) { Text("Reset") }
            }
        }
    }

    editing?.let { e ->
        var name by remember(e) { mutableStateOf(e.name) }
        var icon by remember(e) { mutableStateOf(e.icon) }
        val clash = list.withIndex().any { (i, c) -> i != e.index && c.name.equals(name.trim(), ignoreCase = true) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text(if (e.index == null) "New category" else "Edit category") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = icon,
                        onValueChange = { icon = it.take(8) },
                        label = { Text("Emoji (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Name") },
                        singleLine = true,
                        isError = clash,
                        supportingText = { if (clash) Text("There's already one with this name") },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val ok = if (e.index == null) Categories.add(type, name, icon) else vm.updateCategory(type, e.index, name, icon)
                        if (ok) editing = null
                    },
                    enabled = name.isNotBlank() && !clash,
                ) { Text(if (e.index == null) "Add" else "Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Reset categories?") },
            text = { Text("Both lists go back to the built-in ones. Your own categories are removed from the cards.") },
            confirmButton = {
                TextButton(onClick = {
                    Categories.resetToDefaults()
                    confirmReset = false
                }) { Text("Reset") }
            },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
        )
    }
}
