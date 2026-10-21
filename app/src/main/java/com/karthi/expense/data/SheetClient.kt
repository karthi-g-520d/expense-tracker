package com.karthi.expense.data

import com.karthi.expense.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** Talks to the Apps Script web app in sheet/Code.gs. */
class SheetClient(
    private val url: String = BuildConfig.SHEET_URL,
    private val token: String = BuildConfig.SHEET_TOKEN,
) {

    /** Appends [txns] to the sheet. Returns the ids the sheet confirmed. */
    suspend fun push(txns: List<PendingTxn>): List<String> = withContext(Dispatchers.IO) {
        check(url.isNotEmpty() && token.isNotEmpty()) { "Add sheet.url and sheet.token to local.properties" }

        val rows = JSONArray()
        txns.forEach { t ->
            rows.put(
                JSONObject()
                    .put("id", t.id).put("date", t.date).put("type", t.type)
                    .put("amount", t.amount).put("bank", t.bank).put("account", t.account)
                    .put("counterparty", t.counterparty).put("category", t.category)
                    .put("reason", t.reason).put("reference", t.reference).put("balance", t.balance)
            )
        }
        val body = JSONObject().put("token", token).put("rows", rows).toString()

        val res = JSONObject(post(url, body))
        if (res.has("error")) throw IOException("Sheet error: ${res.getString("error")}")
        val ids = res.getJSONArray("ids")
        List(ids.length()) { ids.getString(it) }
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
