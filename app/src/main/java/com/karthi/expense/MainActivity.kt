package com.karthi.expense

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            ExpenseTheme {
                Surface(Modifier.fillMaxSize()) {
                    MainScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainScreen(vm: MainViewModel = viewModel()) {
    var tab by rememberSaveable { mutableIntStateOf(0) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    val pending by vm.unsynced.collectAsState()

    BackHandler(enabled = showSettings) { showSettings = false }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (showSettings) "Settings" else "Expense Tracker") },
                navigationIcon = {
                    if (showSettings) {
                        IconButton(onClick = { showSettings = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                },
                actions = {
                    if (!showSettings) {
                        IconButton(onClick = { showSettings = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Settings")
                        }
                    }
                },
            )
        },
        bottomBar = { if (!showSettings && tab == 0) SyncBar(vm, pending.size, pending.count { it.category != null }) },
    ) { padding ->
        Column(Modifier.padding(padding)) {
            if (showSettings) {
                SettingsScreen(vm)
                return@Column
            }
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("To tag (${pending.size})") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("Dashboard") })
            }
            if (!vm.sheetConfigured) {
                ConnectBanner(onOpenSettings = { showSettings = true })
            }
            when (tab) {
                0 -> TagScreen(vm)
                else -> Column(Modifier.padding(horizontal = 16.dp)) { DashboardScreen(vm) }
            }
        }
    }
}

@Composable
private fun ConnectBanner(onOpenSettings: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
    ) {
        Row(Modifier.padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Connect your Google Sheet to sync", modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = onOpenSettings) { Text("Set up") }
        }
    }
}

/** Progress of tagging plus the Sync button, pinned to the bottom of the To tag tab. */
@Composable
private fun SyncBar(vm: MainViewModel, total: Int, tagged: Int) {
    if (total == 0 && vm.syncMessage == null) return
    Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
        Column(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            vm.syncMessage?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
            if (total > 0) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("$tagged of $total tagged", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f))
                }
                LinearProgressIndicator(progress = { tagged.toFloat() / total }, modifier = Modifier.fillMaxWidth())
            }
            Button(
                onClick = vm::sync,
                enabled = tagged > 0 && vm.sheetConfigured && !vm.syncing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(if (vm.syncing) "Syncing…" else "Sync $tagged to Google Sheet")
            }
        }
    }
}

private val dayHeader = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)

@OptIn(ExperimentalFoundationApi::class)
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

    if (!hasSmsPermission) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text("SMS permission is needed to find your bank transactions.")
            Button(onClick = { askPermission.launch(Manifest.permission.READ_SMS) }) { Text("Allow SMS access") }
        }
        return
    }

    val txns by vm.unsynced.collectAsState()
    if (vm.importing) LinearProgressIndicator(modifier = Modifier.fillMaxWidth())

    if (txns.isEmpty()) {
        Column(Modifier.fillMaxWidth().padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎉", style = MaterialTheme.typography.displaySmall)
            Text("All caught up", style = MaterialTheme.typography.titleMedium)
            Text(
                "New bank SMS will show up here.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }

    val days = remember(txns) { txns.groupBy { it.date } }
    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        days.forEach { (date, dayTxns) ->
            stickyHeader(key = "h-$date", contentType = "day") {
                Surface(Modifier.fillMaxWidth()) {
                    Column {
                        Text(
                            runCatching { LocalDate.parse(date).format(dayHeader) }.getOrDefault(date),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(top = 12.dp, bottom = 6.dp),
                        )
                        HorizontalDivider()
                    }
                }
            }
            items(dayTxns, key = { it.id }, contentType = { "txn" }) { t ->
                TxnCard(t) { category, reason -> vm.tag(t.id, category, reason) }
            }
        }
    }
}
