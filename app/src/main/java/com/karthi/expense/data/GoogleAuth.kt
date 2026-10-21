package com.karthi.expense.data

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Google sign-in for the Sheets API, via Play services' AuthorizationClient.
 * Needs an Android OAuth client in Google Cloud for this package and signing key's SHA-1.
 */
class GoogleAuth(private val context: Context) {

    private val request = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(SCOPE_SHEETS), Scope(SCOPE_EMAIL)))
        .build()

    /** First step of sign-in. If the result has a resolution, the UI must launch its pending intent. */
    suspend fun authorize(): AuthorizationResult =
        Identity.getAuthorizationClient(context).authorize(request).await()

    /** Reads the result the consent screen returned. */
    fun resultFromIntent(data: Intent?): AuthorizationResult =
        Identity.getAuthorizationClient(context).getAuthorizationResultFromIntent(data)

    /** A fresh access token without any UI; fails if the user has to sign in again. */
    suspend fun accessToken(): String {
        val result = authorize()
        if (result.hasResolution()) throw IOException("Google sign-in expired. Sign in again in Settings")
        return result.accessToken ?: throw IOException("Google didn't return an access token")
    }

    /** The signed-in account's email, for display in Settings. */
    suspend fun email(accessToken: String): String = withContext(Dispatchers.IO) {
        val conn = URL("https://www.googleapis.com/oauth2/v3/userinfo").openConnection() as HttpURLConnection
        try {
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            if (conn.responseCode !in 200..299) throw IOException("Couldn't read your Google account (HTTP ${conn.responseCode})")
            JSONObject(conn.inputStream.bufferedReader().use { it.readText() }).getString("email")
        } finally {
            conn.disconnect()
        }
    }

    private companion object {
        const val SCOPE_SHEETS = "https://www.googleapis.com/auth/spreadsheets"
        const val SCOPE_EMAIL = "email"
    }
}
