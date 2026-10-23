package com.karthi.expense

import android.app.Activity
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp

private const val SETUP_GUIDE = "https://github.com/karthi-g-520d/expense-tracker/blob/main/sheet/README.md"

@Composable
fun SettingsScreen(vm: MainViewModel, contentPadding: PaddingValues) {
    val context = LocalContext.current
    fun open(link: String) = context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(link)))

    var url by rememberSaveable { mutableStateOf(vm.sheetUrlSetting) }
    var token by rememberSaveable { mutableStateOf(vm.sheetTokenSetting) }
    var showToken by remember { mutableStateOf(false) }
    val changed = url.trim() != vm.sheetUrlSetting || token.trim() != vm.sheetTokenSetting

    Column(
        Modifier.fillMaxHeight().verticalScroll(rememberScrollState()).padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        GoogleAccountCard(vm, open = { open(it) })

        AccountsCard()

        CategoriesCard(vm)

        AppLockCard()

        Box(Modifier.fillMaxWidth().softCard()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Apps Script link (alternative)", style = MaterialTheme.typography.titleMedium)
                Text(
                    when {
                        vm.googleEmail != null && vm.hasSpreadsheet -> "Not used while you're signed in with Google."
                        vm.sheetUrlSetting.isNotBlank() && vm.sheetTokenSetting.isNotBlank() -> "Connected to your sheet's web app."
                        else -> "Use this instead of Google sign-in if you set up the sheet's web app."
                    },
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
                    OutlinedButton(
                        onClick = vm::testConnection,
                        enabled = vm.sheetUrlSetting.isNotBlank() && vm.sheetTokenSetting.isNotBlank() && !changed && !vm.testing,
                    ) {
                        Text(if (vm.testing) "Testing…" else "Test connection")
                    }
                }
                vm.testResult?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
                if (!vm.hasSpreadsheet) {
                    vm.spreadsheetLink?.let { link ->
                        OutlinedButton(onClick = { open(link) }, modifier = Modifier.fillMaxWidth()) { Text("Open the spreadsheet") }
                    }
                }
                TextButton(onClick = { open(SETUP_GUIDE) }) { Text("How to set up the sheet") }
            }
        }

        Box(Modifier.fillMaxWidth().softCard()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("SMS", style = MaterialTheme.typography.titleMedium)
                Text(
                    "The app reads new bank messages each time it opens. " +
                        "Re-scan if you think some were missed, or after adding an account; ones already added are skipped.",
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

/** Turn the fingerprint / pattern / PIN lock on or off. */
@Composable
private fun AppLockCard() {
    val context = LocalContext.current
    val lock = remember { AppLock(context) }
    var enabled by remember { mutableStateOf(lock.enabled) }
    val available = remember { lock.available }

    Box(Modifier.fillMaxWidth().softCard()) {
        Row(Modifier.padding(start = 16.dp, end = 12.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("App lock", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (available) "Ask for fingerprint, pattern or PIN when opening the app."
                    else "Set up a screen lock in your phone's settings to use this.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Switch(
                checked = enabled && available,
                onCheckedChange = {
                    enabled = it
                    lock.enabled = it
                },
                enabled = available,
            )
        }
    }
}

/** Sign in with Google, then create a spreadsheet or point at an existing one. */
@Composable
private fun GoogleAccountCard(vm: MainViewModel, open: (String) -> Unit) {
    val signIn = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { res ->
        // Even a non-OK result can carry Google's real error, so let the ViewModel read it.
        if (res.resultCode == Activity.RESULT_OK || res.data != null) vm.onGoogleSignInResult(res.data) else vm.onGoogleSignInCancelled()
    }
    var pastedLink by rememberSaveable { mutableStateOf("") }

    Box(Modifier.fillMaxWidth().softCard()) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("Google account", style = MaterialTheme.typography.titleMedium)

            val email = vm.googleEmail
            if (email == null) {
                Text(
                    "Sign in so the app can create and update your expense spreadsheet in Google Drive.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Button(
                    onClick = { vm.startGoogleSignIn { sender -> signIn.launch(IntentSenderRequest.Builder(sender).build()) } },
                    enabled = !vm.googleBusy,
                    modifier = Modifier.fillMaxWidth(),
                ) { Text(if (vm.googleBusy) "Signing in…" else "Sign in with Google") }
            } else {
                Text("Signed in as $email", style = MaterialTheme.typography.bodyMedium)

                if (vm.hasSpreadsheet) {
                    vm.spreadsheetLink?.let { link ->
                        Button(onClick = { open(link) }, modifier = Modifier.fillMaxWidth()) { Text("Open the spreadsheet") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = vm::changeSpreadsheet) { Text("Change spreadsheet") }
                        TextButton(onClick = vm::signOutGoogle) { Text("Sign out") }
                    }
                } else {
                    Button(onClick = vm::createSpreadsheet, enabled = !vm.googleBusy, modifier = Modifier.fillMaxWidth()) {
                        Text(if (vm.googleBusy) "Working…" else "Create a new spreadsheet")
                    }
                    HorizontalDivider()
                    OutlinedTextField(
                        value = pastedLink,
                        onValueChange = { pastedLink = it },
                        label = { Text("Or paste a spreadsheet link") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { vm.useExistingSpreadsheet(pastedLink) },
                            enabled = pastedLink.isNotBlank() && !vm.googleBusy,
                        ) { Text("Use this sheet") }
                        TextButton(onClick = vm::signOutGoogle) { Text("Sign out") }
                    }
                }
            }
            vm.googleMessage?.let { Text(it, style = MaterialTheme.typography.bodyMedium) }
        }
    }
}
