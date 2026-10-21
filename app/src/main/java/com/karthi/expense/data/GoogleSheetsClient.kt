package com.karthi.expense.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Writes and reads the spreadsheet directly through the Google Sheets API, using the
 * signed-in account. Uses the same layout as sheet/Code.gs so both backends are interchangeable.
 */
class GoogleSheetsClient(private val auth: GoogleAuth, private val settings: SheetSettings) : SheetBackend {

    override suspend fun push(txns: List<PendingTxn>): List<String> = withContext(Dispatchers.IO) {
        val id = spreadsheetId()
        val token = auth.accessToken()

        val existing = values(token, id, "$TAB!A2:A").map { it.optString(0) }.toSet()
        val rows = JSONArray()
        txns.filter { it.id !in existing }.forEach { t ->
            rows.put(
                JSONArray()
                    .put(t.id).put(t.date).put(t.type).put(t.amount.toDouble()).put(t.bank).put(t.account)
                    .put(t.counterparty.orEmpty()).put(t.category.orEmpty()).put(t.reason.orEmpty())
                    .put(t.reference.orEmpty()).put(t.balance?.toDouble() ?: "").put(java.time.LocalDateTime.now().toString())
            )
        }
        if (rows.length() > 0) {
            request(
                "POST", token,
                "$API/$id/values/${enc("$TAB!A1")}:append?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS",
                JSONObject().put("values", rows),
            )
        }
        // Ones that were already in the sheet count as synced too.
        txns.map { it.id }
    }

    override suspend fun fetchAll(): SheetData = withContext(Dispatchers.IO) {
        val id = spreadsheetId()
        val token = auth.accessToken()
        val rows = values(token, id, "$TAB!A2:L").mapNotNull { r ->
            val txnId = r.optString(0)
            if (txnId.isEmpty()) return@mapNotNull null
            SheetTxn(
                id = txnId,
                date = r.optString(1),
                type = r.optString(2),
                amount = r.optString(3).replace(",", "").toDoubleOrNull() ?: 0.0,
                bank = r.optString(4),
                counterparty = r.optString(6).ifEmpty { null },
                category = r.optString(7).ifEmpty { null },
                reason = r.optString(8).ifEmpty { null },
            )
        }
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

    fun linkFor(id: String) = "https://docs.google.com/spreadsheets/d/$id/edit"

    private fun spreadsheetId() =
        settings.spreadsheetId ?: throw IOException("Choose a spreadsheet in Settings")

    // Header row, date/amount formats, frozen header, and the monthly summary formula.
    private fun setUpTabs(token: String, id: String, meta: JSONObject) {
        val sheets = meta.getJSONArray("sheets")
        val tabId = List(sheets.length()) { sheets.getJSONObject(it).getJSONObject("properties") }
            .first { it.getString("title") == TAB }.getInt("sheetId")

        request(
            "PUT", token, "$API/$id/values/${enc("$TAB!A1:L1")}?valueInputOption=RAW",
            JSONObject().put("values", JSONArray().put(JSONArray(HEADERS))),
        )
        val requests = JSONArray()
            .put(JSONObject().put("updateSheetProperties", JSONObject()
                .put("properties", JSONObject().put("sheetId", tabId).put("gridProperties", JSONObject().put("frozenRowCount", 1)))
                .put("fields", "gridProperties.frozenRowCount")))
            .put(numberFormat(tabId, 1, "DATE", "yyyy-mm-dd"))
            .put(numberFormat(tabId, 3, "NUMBER", "#,##0.00"))
        request("POST", token, "$API/$id:batchUpdate", JSONObject().put("requests", requests))
        writeSummaryFormula(token, id)
    }

    private fun writeSummaryFormula(token: String, id: String) {
        val formula = "=QUERY({ARRAYFORMULA(IF($TAB!B2:B=\"\",,TEXT($TAB!B2:B,\"yyyy-mm\")))," +
            "$TAB!C2:C,$TAB!D2:D,$TAB!H2:H}," +
            "\"select Col1, Col2, Col4, sum(Col3) where Col1 is not null " +
            "group by Col1, Col2, Col4 order by Col1 desc, Col2 desc, sum(Col3) desc " +
            "label Col1 'Month', Col2 'Type', Col4 'Category', sum(Col3) 'Total'\",0)"
        request(
            "PUT", token, "$API/$id/values/${enc("'$SUMMARY'!A1")}?valueInputOption=USER_ENTERED",
            JSONObject().put("values", JSONArray().put(JSONArray().put(formula))),
        )
    }

    private fun numberFormat(sheetId: Int, column: Int, type: String, pattern: String) =
        JSONObject().put("repeatCell", JSONObject()
            .put("range", JSONObject().put("sheetId", sheetId).put("startRowIndex", 1)
                .put("startColumnIndex", column).put("endColumnIndex", column + 1))
            .put("cell", JSONObject().put("userEnteredFormat", JSONObject()
                .put("numberFormat", JSONObject().put("type", type).put("pattern", pattern))))
            .put("fields", "userEnteredFormat.numberFormat"))

    private fun values(token: String, id: String, range: String): List<JSONArray> {
        val res = request("GET", token, "$API/$id/values/${enc(range)}", null)
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
        val HEADERS = listOf(
            "id", "date", "type", "amount", "bank", "account", "counterparty",
            "category", "reason", "reference", "balance", "synced_at",
        )
    }
}
