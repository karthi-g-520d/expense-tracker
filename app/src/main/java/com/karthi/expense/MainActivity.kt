package com.karthi.expense

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }
}

@Composable
private fun MainScreen(vm: MainViewModel = viewModel()) {
    var tab by remember { mutableIntStateOf(0) }
    val pending by vm.unsynced.collectAsState()

    Column(Modifier.safeDrawingPadding()) {
        TabRow(selectedTabIndex = tab) {
            Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("To tag (${pending.size})") })
            Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Dashboard") })
        }
        Column(Modifier.padding(16.dp)) {
            when (tab) {
                0 -> TagScreen(vm)
                else -> DashboardScreen(vm)
            }
        }
    }
}

@Composable
private fun TagScreen(vm: MainViewModel) {
    val context = LocalContext.current
    var hasSmsPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.READ_SMS) == PackageManager.PERMISSION_GRANTED
        )
    }
    val askPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasSmsPermission = granted
    }

    // Read new SMS every time this tab opens (and right after permission is granted).
    LaunchedEffect(hasSmsPermission) {
        if (hasSmsPermission) vm.importSms() else askPermission.launch(Manifest.permission.READ_SMS)
    }

    val txns by vm.unsynced.collectAsState()
    val taggedCount = txns.count { it.category != null }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        if (!hasSmsPermission) {
            Text("SMS permission is needed to find your bank transactions.")
            Button(onClick = { askPermission.launch(Manifest.permission.READ_SMS) }) { Text("Allow SMS access") }
            return@Column
        }

        Text(
            when {
                vm.importing -> "Reading new SMS…"
                vm.lastImportCount != null -> "${vm.lastImportCount} new · ${txns.size} waiting to sync"
                else -> "${txns.size} waiting to sync"
            },
            style = MaterialTheme.typography.titleMedium,
        )

        Button(
            onClick = vm::sync,
            enabled = taggedCount > 0 && !vm.syncing,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (vm.syncing) "Syncing…" else "Sync $taggedCount of ${txns.size} to Google Sheet")
        }
        vm.syncMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            items(txns, key = { it.id }) { t ->
                TxnCard(t) { category, reason -> vm.tag(t.id, category, reason) }
            }
        }
    }
}
