package com.karthi.expense.data

import android.content.Context
import com.karthi.expense.BuildConfig

/**
 * Where the Apps Script web app lives, entered on the Settings screen.
 * Falls back to the values baked in at build time (local.properties / CI secrets).
 */
class SheetSettings(context: Context) {

    private val prefs = context.getSharedPreferences("sheet_settings", Context.MODE_PRIVATE)

    val url: String get() = prefs.getString(KEY_URL, null) ?: BuildConfig.SHEET_URL
    val token: String get() = prefs.getString(KEY_TOKEN, null) ?: BuildConfig.SHEET_TOKEN

    /** Link to the spreadsheet itself, learned from the web app on the last successful fetch. */
    var sheetUrl: String?
        get() = prefs.getString(KEY_SHEET_URL, null)
        set(value) = prefs.edit().putString(KEY_SHEET_URL, value).apply()

    val isConfigured: Boolean get() = url.isNotBlank() && token.isNotBlank()

    fun save(url: String, token: String) {
        prefs.edit()
            .putString(KEY_URL, url.trim())
            .putString(KEY_TOKEN, token.trim())
            .remove(KEY_SHEET_URL)
            .apply()
    }

    private companion object {
        const val KEY_URL = "url"
        const val KEY_TOKEN = "token"
        const val KEY_SHEET_URL = "sheet_url"
    }
}
