package com.karthi.expense.data

import android.content.Context
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import com.karthi.expense.core.Bank
import com.karthi.expense.core.MyAccount
import org.json.JSONArray
import org.json.JSONObject

/**
 * Your own bank accounts and cards, set in Settings and saved on the phone. Only SMS about these
 * become cards; with none set, every bank SMS does.
 */
object AccountSettings {

    /** Compose state, so Settings updates as soon as the list changes. */
    val accounts: SnapshotStateList<MyAccount> = mutableStateListOf()

    private var prefs: android.content.SharedPreferences? = null

    /** False until the list has been filled in once (from Settings or from SMS already imported). */
    val isSetUp: Boolean get() = prefs?.contains(KEY) == true

    /** Loads your saved accounts. Call once before showing any screen. */
    fun init(context: Context) {
        if (prefs != null) return
        val p = context.applicationContext.getSharedPreferences("accounts", Context.MODE_PRIVATE)
        prefs = p
        p.getString(KEY, null)?.let { saved ->
            runCatching { accounts.addAll(read(JSONArray(saved))) }
        }
    }

    /**
     * After the update that added this setting, starts the list from the accounts in SMS already on
     * the phone, so nothing you were seeing disappears. A new install starts empty.
     */
    fun seedIfNew(found: List<MyAccount>) {
        if (isSetUp) return
        accounts.addAll(found)
        save()
    }

    /** Digits are kept as typed, minus anything that isn't a digit. Returns false for a duplicate. */
    fun add(bank: Bank, digits: String, fromStart: Boolean): Boolean {
        val account = MyAccount(bank, digits.filter { it.isDigit() }, fromStart && digits.isNotBlank())
        if (accounts.any { it == account }) return false
        accounts.add(account)
        accounts.sortWith(compareBy({ it.bank.label.lowercase() }, { it.digits }))
        save()
        return true
    }

    fun remove(account: MyAccount) {
        accounts.remove(account)
        save()
    }

    private fun save() {
        prefs?.edit()?.putString(KEY, write(accounts).toString())?.apply()
    }

    private fun read(arr: JSONArray) = (0 until arr.length()).mapNotNull {
        val o = arr.getJSONObject(it)
        val bank = Bank.entries.firstOrNull { b -> b.name == o.getString("bank") } ?: return@mapNotNull null
        MyAccount(bank, o.optString("digits"), o.optBoolean("fromStart"))
    }

    private fun write(list: List<MyAccount>) = JSONArray().apply {
        list.forEach { put(JSONObject().put("bank", it.bank.name).put("digits", it.digits).put("fromStart", it.fromStart)) }
    }

    private const val KEY = "list"
}
