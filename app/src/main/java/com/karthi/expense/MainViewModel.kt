package com.karthi.expense

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.karthi.expense.data.AppDatabase
import com.karthi.expense.data.PendingTxn
import com.karthi.expense.data.SheetClient
import com.karthi.expense.data.SheetSettings
import com.karthi.expense.data.SheetTxn
import com.karthi.expense.data.SmsImporter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val dao = db.pendingTxnDao()
    private val sheetDao = db.sheetTxnDao()
    private val importer = SmsImporter(app, dao)
    private val settings = SheetSettings(app)
    private val sheet = SheetClient(settings)

    val unsynced: StateFlow<List<PendingTxn>> =
        dao.observeUnsynced().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** Cached copy of the sheet, shown on the dashboard even when offline. */
    val sheetRows: StateFlow<List<SheetTxn>> =
        sheetDao.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    // Sheet connection, as shown on the Settings screen.
    var sheetUrlSetting by mutableStateOf(settings.url)
        private set
    var sheetTokenSetting by mutableStateOf(settings.token)
        private set
    var sheetConfigured by mutableStateOf(settings.isConfigured)
        private set
    /** Link to open the spreadsheet itself; known after the first successful fetch. */
    var spreadsheetLink by mutableStateOf(settings.sheetUrl)
        private set
    var testing by mutableStateOf(false)
        private set
    var testResult by mutableStateOf<String?>(null)
        private set

    var refreshing by mutableStateOf(false)
        private set

    /** Error from the last dashboard refresh, if it failed. */
    var refreshError by mutableStateOf<String?>(null)
        private set

    /** How many new transactions the last import found; null until the first import finishes. */
    var lastImportCount by mutableStateOf<Int?>(null)
        private set

    var importing by mutableStateOf(false)
        private set

    var syncing by mutableStateOf(false)
        private set

    /** Result of the last sync, shown above the Sync button. */
    var syncMessage by mutableStateOf<String?>(null)
        private set

    fun importSms() {
        if (importing) return
        importing = true
        viewModelScope.launch {
            try {
                lastImportCount = importer.importNew()
            } finally {
                importing = false
            }
        }
    }

    /** Forgets the read checkpoint and re-reads this month's SMS (duplicates are skipped). */
    fun rescanThisMonth() {
        importer.rescanThisMonth()
        importSms()
    }

    fun tag(id: String, category: String?, reason: String?) {
        viewModelScope.launch { dao.updateTag(id, category, reason?.ifBlank { null }) }
    }

    /** Sends every transaction that has a category; untagged ones stay on the phone. */
    fun sync() {
        if (syncing) return
        syncing = true
        viewModelScope.launch {
            syncMessage = try {
                val tagged = dao.taggedUnsynced()
                if (tagged.isEmpty()) {
                    "Pick a category on at least one transaction first"
                } else {
                    val ids = sheet.push(tagged)
                    dao.markSynced(ids, System.currentTimeMillis())
                    refreshDashboard()
                    "Synced ${ids.size} transaction(s)"
                }
            } catch (e: Exception) {
                "Sync failed: ${e.message}"
            } finally {
                syncing = false
            }
        }
    }

    /** Re-downloads the sheet into the on-phone cache. */
    fun refreshDashboard() {
        if (refreshing || !settings.isConfigured) return
        refreshing = true
        viewModelScope.launch {
            refreshError = try {
                val data = sheet.fetchAll()
                sheetDao.replaceAll(data.rows)
                rememberSpreadsheetLink(data.sheetUrl)
                null
            } catch (e: Exception) {
                "Couldn't refresh from the sheet: ${e.message}"
            } finally {
                refreshing = false
            }
        }
    }

    fun saveSheetSettings(url: String, token: String) {
        settings.save(url, token)
        sheetUrlSetting = settings.url
        sheetTokenSetting = settings.token
        sheetConfigured = settings.isConfigured
        spreadsheetLink = null
        testResult = null
    }

    /** Fetches the sheet once to prove the link and token work. */
    fun testConnection() {
        if (testing) return
        testing = true
        viewModelScope.launch {
            testResult = try {
                val data = sheet.fetchAll()
                rememberSpreadsheetLink(data.sheetUrl)
                "✅ Connected · ${data.rows.size} rows in the sheet"
            } catch (e: Exception) {
                "❌ ${e.message}"
            } finally {
                testing = false
            }
        }
    }

    private fun rememberSpreadsheetLink(link: String?) {
        if (link != null) {
            settings.sheetUrl = link
            spreadsheetLink = link
        }
    }
}
