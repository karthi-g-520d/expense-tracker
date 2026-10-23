package com.karthi.expense

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import kotlinx.coroutines.launch

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

        BackgroundCard()

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

/** Plain grey, the phone's wallpaper, or a chosen photo, blurred behind the cards. */
@Composable
private fun BackgroundCard() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    // Set while the user is away on Android's "All files access" screen.
    var askedForAccess by rememberSaveable { mutableStateOf(false) }
    var needsAccess by rememberSaveable { mutableStateOf(false) }

    fun useWallpaper() {
        needsAccess = false
        scope.launch {
            message = if (Backdrop.useWallpaper(context)) null
            else "Couldn't read the wallpaper. Live wallpapers can't be shared; choose a photo instead."
        }
    }
    val askStorage = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) useWallpaper() else message = "Without storage access Android won't share the wallpaper. You can choose a photo instead."
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { message = if (Backdrop.usePhoto(context, uri)) null else "Couldn't open that photo." }
    }
    LifecycleResumeEffect(askedForAccess) {
        if (askedForAccess) {
            askedForAccess = false
            if (Backdrop.canReadWallpaper(context)) useWallpaper()
        }
        onPauseOrDispose {}
    }

    Box(Modifier.fillMaxWidth().softCard()) {
        Column(Modifier.padding(vertical = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Column(Modifier.padding(horizontal = 16.dp)) {
                Text("Background", style = MaterialTheme.typography.titleMedium)
                Text(
                    "Shows a soft, blurred picture behind the cards.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Row(
                Modifier.fillMaxWidth().chipRow(rememberScrollState()).padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                CategoryChip("Plain", selected = Backdrop.mode == Backdrop.Mode.PLAIN) {
                    needsAccess = false
                    message = null
                    Backdrop.usePlain()
                }
                CategoryChip("🖼️ Wallpaper", selected = Backdrop.mode == Backdrop.Mode.WALLPAPER) {
                    message = null
                    when {
                        Backdrop.canReadWallpaper(context) -> useWallpaper()
                        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> needsAccess = true
                        else -> askStorage.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                }
                CategoryChip("📷 Photo", selected = Backdrop.mode == Backdrop.Mode.PHOTO) {
                    needsAccess = false
                    message = null
                    pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                }
            }
            if (needsAccess && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Android only shares the wallpaper with apps that have \"All files access\". " +
                            "The app uses it just to read the wallpaper. Or pick the same picture with Photo.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    OutlinedButton(onClick = {
                        askedForAccess = true
                        val app = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION, Uri.parse("package:${context.packageName}"))
                        runCatching { context.startActivity(app) }
                            .recoverCatching { context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)) }
                            .onFailure { askedForAccess = false }
                    }) { Text("Allow all files access") }
                }
            }
            message?.let {
                Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(horizontal = 16.dp))
            }
        }
    }
}
