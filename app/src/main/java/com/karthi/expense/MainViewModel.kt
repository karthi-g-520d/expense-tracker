package com.karthi.expense

import android.app.Application
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.karthi.expense.data.AppDatabase
import com.karthi.expense.data.PendingTxn
import com.karthi.expense.data.SmsImporter
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(app: Application) : AndroidViewModel(app) {

    private val dao = AppDatabase.get(app).pendingTxnDao()
    private val importer = SmsImporter(app, dao)

    val unsynced: StateFlow<List<PendingTxn>> =
        dao.observeUnsynced().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    /** How many new transactions the last import found; null until the first import finishes. */
    var lastImportCount by mutableStateOf<Int?>(null)
        private set

    var importing by mutableStateOf(false)
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
}
