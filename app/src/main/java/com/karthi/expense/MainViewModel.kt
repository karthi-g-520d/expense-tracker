package com.karthi.expense

import android.app.Application
import android.content.Intent
import android.content.IntentSender
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.android.gms.common.api.ApiException
import com.google.android.gms.common.api.CommonStatusCodes
import com.karthi.expense.core.PayerMatch
import com.karthi.expense.core.Shares
import com.karthi.expense.core.TxnType
import com.karthi.expense.data.AppDatabase
import com.karthi.expense.data.Categories
import com.karthi.expense.data.GoogleAuth
import com.karthi.expense.data.GoogleSheetsClient
import com.karthi.expense.data.PendingTxn
import com.karthi.expense.data.SheetBackend
import com.karthi.expense.data.SheetClient
import com.karthi.expense.data.SheetSettings
import com.karthi.expense.data.SheetTxn
import com.karthi.expense.data.SmsImporter
import java.math.BigDecimal
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val db = AppDatabase.get(app)
    private val dao = db.pendingTxnDao()
    private val sheetDao = db.sheetTxnDao()
    private val importer = SmsImporter(app, dao)
    private val settings = SheetSettings(app)
    private val appsScript = SheetClient(settings)
    private val googleAuth = GoogleAuth(app)
    private val googleSheets = GoogleSheetsClient(googleAuth, settings) { dao.synced() }

    /** Google sign-in wins when it's fully set up; otherwise the Apps Script link. */
    private val sheet: SheetBackend get() = if (settings.usesGoogleSignIn) googleSheets else appsScript

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
    var sheetConfigured by mutableStateOf(settings.isConnected)
        private set
    /** Link to open the spreadsheet itself; known after the first successful fetch. */
    var spreadsheetLink by mutableStateOf(settings.sheetUrl)
        private set
    var googleEmail by mutableStateOf(settings.googleEmail)
        private set
    var hasSpreadsheet by mutableStateOf(settings.spreadsheetId != null)
        private set
    var googleBusy by mutableStateOf(false)
        private set
    var googleMessage by mutableStateOf<String?>(null)
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

    var importing by mutableStateOf(false)
        private set

    var syncing by mutableStateOf(false)
        private set

    /** Result of the last sync, shown above the Sync button. */
    var syncMessage by mutableStateOf<String?>(null)
        private set

    /** Bumped after each sync attempt that reached the sheet, so the UI can vibrate once. */
    var syncAttempts by mutableIntStateOf(0)
        private set
    var lastSyncOk by mutableStateOf(false)
        private set

    fun importSms() {
        if (importing) return
        importing = true
        viewModelScope.launch {
            try {
                importer.importNew()
            } finally {
                importing = false
            }
        }
    }

    /** Renames in the list and on cards not yet synced; rows already in the sheet keep the old name. */
    fun updateCategory(type: String, index: Int, name: String, icon: String): Boolean {
        val old = Categories.forType(type)[index].name
        if (!Categories.update(type, index, name, icon)) return false
        val new = Shares.cleanName(name)
        if (new != old) viewModelScope.launch { dao.renameUnsynced(type, old, new) }
        return true
    }

    fun tag(id: String, category: String?, reason: String?, shares: String?, parts: String?) {
        viewModelScope.launch { dao.updateTag(id, category, reason?.ifBlank { null }, shares, parts) }
    }

    /** Names used in earlier splits, most frequent first, for the name suggestions on cards. */
    val knownPeople: StateFlow<List<String>> =
        combine(sheetRows, unsynced) { rows, pending ->
            (rows.map { it.shares } + pending.map { it.shares })
                .flatMap { Shares.parse(it).keys }
                .groupBy { it.lowercase() }.values
                .sortedByDescending { it.size }
                .map { it.first() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /**
     * Every person's ledger, signed so positive means they owe you, from the sheet plus cards not
     * yet synced. Cards use it to guess who a UPI payment is from and which item it pays back.
     */
    val peopleLedger: StateFlow<Map<String, List<PayerMatch.Entry>>> =
        combine(sheetRows, unsynced) { rows, pending ->
            val lines = rows.map { Line(it.id, it.date, it.type, it.counterparty, it.shares) } +
                pending.map { Line(it.id, it.date, it.type, it.counterparty, it.shares) }
            val display = LinkedHashMap<String, String>()
            val byPerson = LinkedHashMap<String, MutableList<PayerMatch.Entry>>()
            for (l in lines) {
                val type = if (l.type == "CREDIT") TxnType.CREDIT else TxnType.DEBIT
                for ((name, share) in Shares.parse(l.shares)) {
                    val key = name.lowercase()
                    display.putIfAbsent(key, name)
                    byPerson.getOrPut(key) { mutableListOf() } +=
                        PayerMatch.Entry(l.id, l.date, l.counterparty ?: "Unknown", Shares.balanceChange(type, share))
                }
            }
            byPerson.entries.associate { display.getValue(it.key) to it.value.toList() }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    private data class Line(val id: String, val date: String, val type: String, val counterparty: String?, val shares: String?)

    /** Result of the last Settle, shown on the People card. */
    var peopleMessage by mutableStateOf<String?>(null)
        private set

    /**
     * Closes a person's balance without money moving: what they owe becomes your expense,
     * what you hold of theirs becomes your income. Written to the sheet as its own row.
     */
    fun settle(person: String, balance: BigDecimal) {
        if (balance.signum() == 0) return
        viewModelScope.launch {
            peopleMessage = try {
                val now = System.currentTimeMillis()
                val owedToMe = balance.signum() > 0
                val row = PendingTxn(
                    id = "SETTLE-${Shares.cleanName(person).replace(' ', '_')}-$now",
                    bank = "",
                    type = if (owedToMe) "DEBIT" else "CREDIT",
                    amount = "0",
                    account = "",
                    counterparty = person,
                    date = java.time.LocalDate.now().toString(),
                    reference = null,
                    balance = null,
                    smsTime = now,
                    category = Categories.SETTLED,
                    reason = if (owedToMe) "Let go what $person owed" else "Kept what was left of $person's money",
                    // A negative share brings the balance to zero and leaves the difference as your own.
                    shares = Shares.format(mapOf(person to balance.abs().negate())),
                )
                sheet.push(listOf(row))
                refreshDashboard()
                "Settled with $person"
            } catch (e: Exception) {
                "Couldn't settle: ${e.message}"
            }
        }
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
                    lastSyncOk = true
                    syncAttempts++
                    "Synced ${ids.size} transaction(s)"
                }
            } catch (e: Exception) {
                lastSyncOk = false
                syncAttempts++
                "Sync failed: ${e.message}"
            } finally {
                syncing = false
            }
        }
    }

    /** Re-downloads the sheet into the on-phone cache. */
    fun refreshDashboard() {
        if (refreshing || !settings.isConnected) return
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
        sheetConfigured = settings.isConnected
        if (!settings.usesGoogleSignIn) spreadsheetLink = null
        testResult = null
    }

    /** Fetches the sheet once to prove the link and token work. */
    fun testConnection() {
        if (testing) return
        testing = true
        viewModelScope.launch {
            testResult = try {
                val data = appsScript.fetchAll()
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

    /** Starts Google sign-in; [launch] shows Google's consent screen when one is needed. */
    fun startGoogleSignIn(launch: (IntentSender) -> Unit) = google {
        val result = googleAuth.authorize()
        val pending = result.pendingIntent
        if (result.hasResolution() && pending != null) {
            launch(pending.intentSender)
        } else {
            onAuthorized(result.accessToken)
        }
    }

    /** Called with what Google's consent screen returned. */
    fun onGoogleSignInResult(data: Intent?) = google {
        onAuthorized(googleAuth.resultFromIntent(data).accessToken)
    }

    fun onGoogleSignInCancelled() {
        googleMessage = "Sign-in cancelled before Google returned a result. Try again; if it keeps happening, " +
            "check the Google Cloud setup (Sheets API, consent screen, Android client SHA-1)."
    }

    fun createSpreadsheet() = google {
        useSpreadsheetId(googleSheets.createSpreadsheet())
        googleMessage = "Created \"Expense Tracker\" in your Google Drive"
    }

    fun useExistingSpreadsheet(link: String) = google {
        useSpreadsheetId(googleSheets.useSpreadsheet(link))
        googleMessage = "Using that spreadsheet"
    }

    /** Keeps the Google account but lets the user pick a different spreadsheet. */
    fun changeSpreadsheet() {
        settings.spreadsheetId = null
        syncGoogleState()
        spreadsheetLink = null
    }

    fun signOutGoogle() {
        settings.signOutGoogle()
        syncGoogleState()
        spreadsheetLink = null
        googleMessage = null
    }

    private suspend fun onAuthorized(accessToken: String?) {
        val token = accessToken ?: error("Google didn't return an access token")
        settings.googleEmail = googleAuth.email(token)
        syncGoogleState()
        googleMessage = if (hasSpreadsheet) null else "Signed in. Now create a spreadsheet or paste a link to one."
    }

    private fun useSpreadsheetId(id: String) {
        settings.spreadsheetId = id
        syncGoogleState()
        rememberSpreadsheetLink(googleSheets.linkFor(id))
        refreshDashboard()
    }

    private fun syncGoogleState() {
        googleEmail = settings.googleEmail
        hasSpreadsheet = settings.spreadsheetId != null
        sheetConfigured = settings.isConnected
    }

    // Runs one Google step with a busy flag and a readable error.
    private fun google(block: suspend () -> Unit) {
        if (googleBusy) return
        googleBusy = true
        viewModelScope.launch {
            try {
                block()
            } catch (e: ApiException) {
                googleMessage = when (e.statusCode) {
                    CommonStatusCodes.DEVELOPER_ERROR ->
                        "Google sign-in isn't set up for this app yet. Check the Android OAuth client " +
                            "(package com.karthi.expense and the SHA-1) in Google Cloud."
                    CommonStatusCodes.CANCELED ->
                        "Sign-in was cancelled. If you didn't cancel it, Google may have rejected the app: in Google " +
                            "Cloud check that the Sheets API is enabled, the consent screen is In production (or your " +
                            "email is a test user), and the Android client's package and SHA-1 match."
                    else -> "Google sign-in failed (code ${e.statusCode}): ${e.status.statusMessage ?: e.message}"
                }
            } catch (e: Exception) {
                googleMessage = e.message ?: "Something went wrong"
            } finally {
                googleBusy = false
            }
        }
    }
}
