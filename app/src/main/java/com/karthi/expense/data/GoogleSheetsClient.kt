package com.karthi.expense.data

import com.karthi.expense.core.Shares
import com.karthi.expense.core.TxnType
import java.io.IOException
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Writes and reads the spreadsheet directly through the Google Sheets API, using the
 * signed-in account. Uses the same layout as sheet/Code.gs so both backends are interchangeable.
 */
class GoogleSheetsClient(
    private val auth: GoogleAuth,
    private val settings: SheetSettings,
    /** Transactions this phone already synced; used to restore rows when the tab's layout is damaged. */
    private val localSynced: suspend () -> List<PendingTxn>,
) : SheetBackend {

    /** Spreadsheet whose formats and summary formula were already repaired in this app run. */
    @Volatile private var repairedId: String? = null

    override suspend fun push(txns: List<PendingTxn>): List<String> = withContext(Dispatchers.IO) {
        val id = spreadsheetId()
        val token = auth.accessToken()
        repair(token, id)

        val existing = values(token, id, "$TAB!A2:A").map { it.optString(0) }.toSet()
        val rows = JSONArray()
        txns.filter { it.id !in existing }.forEach { t ->
            rows.put(rowFor(t, java.time.LocalDateTime.now().toString()))
        }
        if (rows.length() > 0) {
            request(
                "POST", token,
                // OVERWRITE fills the existing empty rows, which carry the column's date format;
                // INSERT_ROWS would copy the header row's plain format and dates would show as numbers.
                "$API/$id/values/${enc("$TAB!A1")}:append?valueInputOption=USER_ENTERED&insertDataOption=OVERWRITE",
                JSONObject().put("values", rows),
            )
        }
        // Ones that were already in the sheet count as synced too.
        txns.map { it.id }
    }

    override suspend fun fetchAll(): SheetData = withContext(Dispatchers.IO) {
        val id = spreadsheetId()
        val token = auth.accessToken()
        if (repairedId != id) repair(token, id)
        val rows = values(token, id, "$TAB!A2:N", unformatted = true).mapNotNull { r ->
            val txnId = r.optString(0)
            if (txnId.isEmpty()) return@mapNotNull null
            SheetTxn(
                id = txnId,
                date = isoDate(r.opt(1)),
                type = r.optString(2),
                amount = (r.opt(3) as? Number)?.toDouble() ?: r.optString(3).replace(",", "").toDoubleOrNull() ?: 0.0,
                bank = r.optString(4),
                counterparty = r.optString(6).ifEmpty { null },
                category = r.optString(7).ifEmpty { null },
                reason = r.optString(8).ifEmpty { null },
                shares = r.optString(12).ifEmpty { null },
            )
        }
        writePeople(token, id, rows)
        SheetData(rows, linkFor(id))
    }

    /** Creates "Expense Tracker" in the user's Drive with both tabs set up. Returns its id. */
    suspend fun createSpreadsheet(): String = withContext(Dispatchers.IO) {
        val token = auth.accessToken()
        val created = request(
            "POST", token, API,
            JSONObject()
                .put("properties", JSONObject().put("title", "Expense Tracker"))
                .put(
                    "sheets", JSONArray()
                        .put(JSONObject().put("properties", JSONObject().put("title", TAB)))
                        .put(JSONObject().put("properties", JSONObject().put("title", SUMMARY)))
                ),
        )
        val id = created.getString("spreadsheetId")
        setUpTabs(token, id, created)
        id
    }

    /** Uses an existing spreadsheet the user pasted, adding the tabs if they're missing. */
    suspend fun useSpreadsheet(linkOrId: String): String = withContext(Dispatchers.IO) {
        val id = Regex("/d/([a-zA-Z0-9_-]+)").find(linkOrId)?.groupValues?.get(1) ?: linkOrId.trim()
        val token = auth.accessToken()
        var meta = request("GET", token, "$API/$id?fields=sheets.properties", null)
        val titles = meta.getJSONArray("sheets").let { s -> List(s.length()) { s.getJSONObject(it).getJSONObject("properties").getString("title") } }
        val missing = listOf(TAB, SUMMARY).filter { it !in titles }
        if (missing.isNotEmpty()) {
            val adds = JSONArray()
            missing.forEach { adds.put(JSONObject().put("addSheet", JSONObject().put("properties", JSONObject().put("title", it)))) }
            request("POST", token, "$API/$id:batchUpdate", JSONObject().put("requests", adds))
            meta = request("GET", token, "$API/$id?fields=sheets.properties", null)
        }
        if (TAB in missing) setUpTabs(token, id, meta) else if (SUMMARY in missing) writeSummaryFormula(token, id)
        id
    }

    /** Sheets returns a date cell either as text or as a serial day number (days since 1899-12-30). */
    private fun isoDate(value: Any?): String {
        val serial = (value as? Number)?.toLong() ?: value?.toString()?.toDoubleOrNull()?.toLong()
        return serial?.let { SHEETS_EPOCH.plusDays(it).toString() } ?: value?.toString().orEmpty()
    }

    // A leading apostrophe keeps numbers like "041330335754" as text, so leading zeros survive.
    private fun asText(value: String?) = value?.let { "'$it" }.orEmpty()

    fun linkFor(id: String) = "https://docs.google.com/spreadsheets/d/$id/edit"

    private fun spreadsheetId() =
        settings.spreadsheetId ?: throw IOException("Choose a spreadsheet in Settings")

    // Header row, date/amount formats, frozen header, and the monthly summary formula.
    private fun setUpTabs(token: String, id: String, meta: JSONObject) {
        val tabId = tabIdFrom(meta)

        request(
            "PUT", token, "$API/$id/values/${enc("$TAB!A1:L1")}?valueInputOption=RAW",
            JSONObject().put("values", JSONArray().put(JSONArray(HEADERS))),
        )
        val requests = JSONArray()
            .put(JSONObject().put("updateSheetProperties", JSONObject()
                .put("properties", JSONObject().put("sheetId", tabId).put("gridProperties", JSONObject().put("frozenRowCount", 1)))
                .put("fields", "gridProperties.frozenRowCount")))
        request("POST", token, "$API/$id:batchUpdate", JSONObject().put("requests", requests))
        applyColumnFormats(token, id, tabId)
        writeSummaryFormula(token, id)
    }

    private fun rowFor(t: PendingTxn, syncedAt: String): JSONArray = JSONArray()
        .put(t.id).put(t.date).put(t.type).put(t.amount.toDouble()).put(t.bank).put(asText(t.account))
        .put(t.counterparty.orEmpty()).put(t.category.orEmpty()).put(t.reason.orEmpty())
        .put(asText(t.reference)).put(t.balance?.toDouble() ?: "").put(syncedAt)
        .put(t.shares.orEmpty())
        .put(Categories.myAmount(BigDecimal(t.amount), t.shares, t.category).toDouble())

    // Fixes sheets written by older versions (dates shown as numbers, summary formula
    // broken by inserted rows) and tabs whose rows got moved. Safe to repeat.
    private suspend fun repair(token: String, id: String) {
        fixLayout(token, id)
        applyColumnFormats(token, id, tabId(token, id))
        writeSummaryFormula(token, id)
        repairedId = id
    }

    /**
     * If the header isn't in A1:L1 (rows were moved or pasted over), collects every row that
     * starts with a transaction id, wherever it sits, and rewrites the tab from A1. Rows whose
     * id cell was lost are restored from what this phone synced.
     */
    private suspend fun fixLayout(token: String, id: String) {
        val cells = values(token, id, TAB, unformatted = true)
        val header = cells.firstOrNull()?.let { r -> List(r.length()) { r.optString(it) } }.orEmpty()
        if (header.take(LEGACY_COLUMNS) == HEADERS.take(LEGACY_COLUMNS)) {
            // Sheets made before shares existed just need the two new column titles.
            if (header.size < HEADERS.size) {
                request(
                    "PUT", token, "$API/$id/values/${enc("$TAB!A1")}?valueInputOption=RAW",
                    JSONObject().put("values", JSONArray().put(JSONArray(HEADERS))),
                )
            }
            return
        }

        val found = LinkedHashMap<String, JSONArray>()
        for (r in cells) {
            for (start in 0 until r.length()) {
                val txnId = r.optString(start)
                if (!ID_PATTERN.matches(txnId) || txnId in found) continue
                found[txnId] = JSONArray().apply {
                    put(txnId)
                    put(isoDate(r.opt(start + 1)))
                    put(r.optString(start + 2))
                    put(r.opt(start + 3) ?: "")
                    put(r.optString(start + 4))
                    put(asText(plain(r.opt(start + 5))))
                    put(r.optString(start + 6))
                    put(r.optString(start + 7))
                    put(r.optString(start + 8))
                    put(asText(plain(r.opt(start + 9))))
                    put(r.opt(start + 10) ?: "")
                    put(dateTime(r.opt(start + 11)))
                    put(r.optString(start + 12))
                    put(r.opt(start + 13) ?: "")
                }
            }
        }
        localSynced().filter { it.id !in found }.forEach { found[it.id] = rowFor(it, "") }

        val rows = JSONArray().put(JSONArray(HEADERS))
        found.values.sortedBy { it.optString(1) }.forEach { rows.put(it) }
        request("POST", token, "$API/$id/values/${enc(TAB)}:clear", JSONObject())
        request(
            "PUT", token, "$API/$id/values/${enc("$TAB!A1")}?valueInputOption=USER_ENTERED",
            JSONObject().put("values", rows),
        )
    }

    // Unformatted reads return numbers for cells like account "597"; turn 597.0 back into "597".
    private fun plain(value: Any?): String? = when (value) {
        null, "" -> null
        is Number -> java.math.BigDecimal(value.toString()).stripTrailingZeros().toPlainString()
        else -> value.toString()
    }

    private fun dateTime(value: Any?): String {
        val serial = (value as? Number)?.toDouble() ?: return value?.toString().orEmpty()
        val seconds = Math.round(serial * 86_400)
        return SHEETS_EPOCH.atStartOfDay().plusSeconds(seconds).toString()
    }

    /** Rewrites the People tab with everyone whose balance isn't settled yet. */
    private fun writePeople(token: String, id: String, rows: List<SheetTxn>) {
        val meta = request("GET", token, "$API/$id?fields=sheets.properties", null)
        val sheets = meta.getJSONArray("sheets")
        val titles = List(sheets.length()) { sheets.getJSONObject(it).getJSONObject("properties").getString("title") }
        if (PEOPLE !in titles) {
            val add = JSONObject().put("addSheet", JSONObject().put("properties", JSONObject().put("title", PEOPLE)))
            request("POST", token, "$API/$id:batchUpdate", JSONObject().put("requests", JSONArray().put(add)))
        }

        val open = Shares.balances(rows.map { (if (it.type == "CREDIT") TxnType.CREDIT else TxnType.DEBIT) to it.shares })
            .filter { it.value.abs() >= BigDecimal("0.01") }
            .entries.sortedByDescending { it.value }
        val values = JSONArray().put(JSONArray(listOf("Person", "Balance", "Status")))
        open.forEach { (name, balance) ->
            values.put(
                JSONArray().put(name).put(balance.toDouble())
                    .put(if (balance.signum() > 0) "Owes you" else "You hold their money")
            )
        }
        request("POST", token, "$API/$id/values/${enc(PEOPLE)}:clear", JSONObject())
        request(
            "PUT", token, "$API/$id/values/${enc("$PEOPLE!A1")}?valueInputOption=RAW",
            JSONObject().put("values", values),
        )
    }

    private fun applyColumnFormats(token: String, id: String, tabId: Int) {
        val requests = JSONArray()
            .put(numberFormat(tabId, 1, "DATE", "yyyy-mm-dd"))
            .put(numberFormat(tabId, 3, "NUMBER", "#,##0.00"))
            .put(numberFormat(tabId, 13, "NUMBER", "#,##0.00"))
        request("POST", token, "$API/$id:batchUpdate", JSONObject().put("requests", requests))
    }

    private fun tabId(token: String, id: String) =
        tabIdFrom(request("GET", token, "$API/$id?fields=sheets.properties", null))

    private fun tabIdFrom(meta: JSONObject): Int {
        val sheets = meta.getJSONArray("sheets")
        return List(sheets.length()) { sheets.getJSONObject(it).getJSONObject("properties") }
            .first { it.getString("title") == TAB }.getInt("sheetId")
    }

    private fun writeSummaryFormula(token: String, id: String) {
        request(
            "PUT", token, "$API/$id/values/${enc("'$SUMMARY'!A1")}?valueInputOption=USER_ENTERED",
            JSONObject().put("values", JSONArray().put(JSONArray().put(SUMMARY_FORMULA))),
        )
    }

    private fun numberFormat(sheetId: Int, column: Int, type: String, pattern: String) =
        JSONObject().put("repeatCell", JSONObject()
            .put("range", JSONObject().put("sheetId", sheetId).put("startRowIndex", 1)
                .put("startColumnIndex", column).put("endColumnIndex", column + 1))
            .put("cell", JSONObject().put("userEnteredFormat", JSONObject()
                .put("numberFormat", JSONObject().put("type", type).put("pattern", pattern))))
            .put("fields", "userEnteredFormat.numberFormat"))

    private fun values(token: String, id: String, range: String, unformatted: Boolean = false): List<JSONArray> {
        val render = if (unformatted) "?valueRenderOption=UNFORMATTED_VALUE" else ""
        val res = request("GET", token, "$API/$id/values/${enc(range)}$render", null)
        val rows = res.optJSONArray("values") ?: return emptyList()
        return List(rows.length()) { rows.getJSONArray(it) }
    }

    private fun request(method: String, token: String, url: String, body: JSONObject?): JSONObject {
        val conn = URL(url).openConnection() as HttpURLConnection
        try {
            conn.requestMethod = method
            conn.connectTimeout = 15_000
            conn.readTimeout = 30_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            if (body != null) {
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/json")
                conn.outputStream.use { it.write(body.toString().toByteArray()) }
            }
            val code = conn.responseCode
            if (code !in 200..299) {
                val err = conn.errorStream?.bufferedReader()?.use { it.readText() }.orEmpty()
                val msg = runCatching { JSONObject(err).getJSONObject("error").getString("message") }.getOrNull()
                throw IOException("Google Sheets error ($code)${msg?.let { ": $it" }.orEmpty()}")
            }
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            return if (text.isBlank()) JSONObject() else JSONObject(text)
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(range: String) = URLEncoder.encode(range, "UTF-8").replace("+", "%20")

    private companion object {
        const val API = "https://sheets.googleapis.com/v4/spreadsheets"
        const val TAB = "Transactions"
        const val SUMMARY = "Monthly Summary"
        // Whole-column ranges never shift when rows are inserted, and ISNUMBER skips the header
        // row and blanks (a real date is a number underneath). Totals use your own part (my_amount),
        // falling back to the full amount for rows synced before shares existed.
        val SUMMARY_FORMULA = "=QUERY({ARRAYFORMULA(IF(ISNUMBER($TAB!B:B),TEXT($TAB!B:B,\"yyyy-mm\"),)), " +
            "$TAB!C:C, ARRAYFORMULA(IF($TAB!N:N=\"\",$TAB!D:D,$TAB!N:N)), $TAB!H:H}, " +
            "\"select Col1, Col2, Col4, sum(Col3) where Col1 is not null and Col3 <> 0 " +
            "group by Col1, Col2, Col4 order by Col1 desc, Col2 desc, sum(Col3) desc " +
            "label Col1 'Month', Col2 'Type', Col4 'Category', sum(Col3) 'Total'\", 0)"
        val ID_PATTERN = Regex("((ICICI|IOB)-(DEBIT|CREDIT)|SETTLE)-.+")
        const val PEOPLE = "People"
        /** Columns A–L, the layout before shares were added. */
        const val LEGACY_COLUMNS = 12
        val SHEETS_EPOCH: java.time.LocalDate = java.time.LocalDate.of(1899, 12, 30)
        val HEADERS = listOf(
            "id", "date", "type", "amount", "bank", "account", "counterparty",
            "category", "reason", "reference", "balance", "synced_at", "shares", "my_amount",
        )
    }
}
