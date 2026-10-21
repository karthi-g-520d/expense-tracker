package com.karthi.expense.data

import com.karthi.expense.core.Shares
import java.io.IOException
import java.math.BigDecimal
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/** Rows in the sheet, plus the spreadsheet's own link when known. */
class SheetData(val rows: List<SheetTxn>, val sheetUrl: String?)

/** Where synced transactions go: the Apps Script web app or the Sheets API. */
interface SheetBackend {
    /** Appends [txns] to the sheet, skipping ids already there. Returns the ids the sheet now holds. */
    suspend fun push(txns: List<PendingTxn>): List<String>

    /** Downloads every row in the sheet, for the dashboard. */
    suspend fun fetchAll(): SheetData
}

/** Talks to the Apps Script web app in sheet/Code.gs. Reads the link and token at call time. */
class SheetClient(private val settings: SheetSettings) : SheetBackend {

    private val url get() = settings.url
    private val token get() = settings.token

    override suspend fun push(txns: List<PendingTxn>): List<String> = withContext(Dispatchers.IO) {
        check(settings.isConfigured) { "Connect your Google Sheet in Settings" }

        val rows = JSONArray()
        txns.forEach { t ->
            rows.put(
                JSONObject()
                    .put("id", t.id).put("date", t.date).put("type", t.type)
                    .put("amount", t.amount).put("bank", t.bank).put("account", t.account)
                    .put("counterparty", t.counterparty).put("category", t.category)
                    .put("reason", t.reason).put("reference", t.reference).put("balance", t.balance)
                    .put("shares", t.shares)
                    .put("my_amount", Shares.myPart(BigDecimal(t.amount), Shares.parse(t.shares)).toDouble())
            )
        }
        val body = JSONObject().put("token", token).put("rows", rows).toString()

        val res = JSONObject(post(url, body))
        if (res.has("error")) throw IOException("Sheet error: ${res.getString("error")}")
        val ids = res.getJSONArray("ids")
        List(ids.length()) { ids.getString(it) }
    }

    override suspend fun fetchAll(): SheetData = withContext(Dispatchers.IO) {
        check(settings.isConfigured) { "Connect your Google Sheet in Settings" }

        val res = JSONObject(get("$url?token=${URLEncoder.encode(token, "UTF-8")}"))
        if (res.has("error")) throw IOException("Sheet error: ${res.getString("error")}")
        val rows = res.getJSONArray("rows")
        val txns = List(rows.length()) { i ->
            val r = rows.getJSONObject(i)
            SheetTxn(
                id = r.getString("id"),
                date = r.getString("date"),
                type = r.getString("type"),
                amount = r.optDouble("amount", 0.0),
                bank = r.optString("bank"),
                counterparty = r.optString("counterparty").ifEmpty { null },
                category = r.optString("category").ifEmpty { null },
                reason = r.optString("reason").ifEmpty { null },
                shares = r.optString("shares").ifEmpty { null },
            )
        }
        SheetData(txns, res.optString("sheetUrl").ifEmpty { null })
    }

    // Apps Script answers a POST with a 302 to a URL that must be fetched with GET.
    private fun post(target: String, body: String): String {
        val conn = (URL(target).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            instanceFollowRedirects = false
            doOutput = true
            connectTimeout = 15_000
            readTimeout = 30_000
            setRequestProperty("Content-Type", "application/json")
        }
        try {
            conn.outputStream.use { it.write(body.toByteArray()) }
            return when (val code = conn.responseCode) {
                in 300..399 -> get(conn.getHeaderField("Location"))
                in 200..299 -> conn.inputStream.bufferedReader().use { it.readText() }
                else -> throw IOException("Sheet returned HTTP $code")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun get(target: String): String {
        val conn = URL(target).openConnection() as HttpURLConnection
        try {
            if (conn.responseCode !in 200..299) throw IOException("Sheet returned HTTP ${conn.responseCode}")
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }
}
