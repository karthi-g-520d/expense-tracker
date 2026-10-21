package com.karthi.expense

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

private const val SETUP_GUIDE = "https://github.com/karthi-g-520d/expense-tracker/blob/main/sheet/README.md"

@Composable
fun SettingsScreen(vm: MainViewModel) {
    val context = LocalContext.current
    fun open(link: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))

    var url by rememberSaveable { mutableStateOf(vm.sheetUrlSetting) }
    var token by rememberSaveable { mutableStateOf(vm.sheetTokenSetting) }
    var showToken by remember { mutableStateOf(false) }
    val changed = url.trim() != vm.sheetUrlSetting || token.trim() != vm.sheetTokenSetting

    Column(
        Modifier.verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Google Sheet", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (vm.sheetConfigured) "Connected to your sheet's web app." else "Not connected yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Web app link (…/exec)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = token,
                    onValueChange = { token = it },
                    label = { Text("Token") },
                    singleLine = true,
                    visualTransformation = if (showToken) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        TextButton(onClick = { showToken = !showToken }) { Text(if (showToken) "Hide" else "Show") }
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { vm.saveSheetSettings(url, token) }, enabled = changed) { Text("Save") }
                    OutlinedButton(onClick = vm::testConnection, enabled = vm.sheetConfigured && !changed && !vm.testing) {
                        Text(if (vm.testing) "Testing…" else "Test connection")
                    }
                }
                vm.testResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                vm.spreadsheetLink?.let { link ->
                    OutlinedButton(onClick = { open(link) }, modifier = Modifier.fillMaxWidth()) { Text("Open the spreadsheet") }
                }
                TextButton(onClick = { open(SETUP_GUIDE) }) { Text("How to set up the sheet") }
            }
        }

        Card(Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SMS", style = MaterialTheme.typography.titleMedium)
                Text(
                    "The app reads new ICICI and IOB messages each time it opens. " +
                        "Re-scan if you think some were missed; ones already added are skipped.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = vm::rescanThisMonth, enabled = !vm.importing) {
                    Text(if (vm.importing) "Scanning…" else "Re-scan this month's SMS")
                }
                vm.lastImportCount?.let { Text("Last scan found $it new", style = MaterialTheme.typography.bodySmall) }
            }
        }

        Text(
            "Expense Tracker ${BuildConfig.VERSION_NAME}",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
