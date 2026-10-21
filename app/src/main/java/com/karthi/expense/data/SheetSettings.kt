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

    /** The Apps Script link and token are both set. */
    val isConfigured: Boolean get() = url.isNotBlank() && token.isNotBlank()

    /** Google account the app signed in with; null when not signed in. */
    var googleEmail: String?
        get() = prefs.getString(KEY_GOOGLE_EMAIL, null)
        set(value) = prefs.edit().putString(KEY_GOOGLE_EMAIL, value).apply()

    /** Spreadsheet used through the Sheets API after Google sign-in. */
    var spreadsheetId: String?
        get() = prefs.getString(KEY_SPREADSHEET_ID, null)
        set(value) = prefs.edit().putString(KEY_SPREADSHEET_ID, value).apply()

    /** Signed in with Google and a spreadsheet chosen; this wins over the Apps Script link. */
    val usesGoogleSignIn: Boolean get() = googleEmail != null && spreadsheetId != null

    val isConnected: Boolean get() = usesGoogleSignIn || isConfigured

    fun signOutGoogle() {
        prefs.edit().remove(KEY_GOOGLE_EMAIL).remove(KEY_SPREADSHEET_ID).apply()
    }

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
        const val KEY_GOOGLE_EMAIL = "google_email"
        const val KEY_SPREADSHEET_ID = "spreadsheet_id"
    }
}
