package com.karthi.expense

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(Modifier.fillMaxSize()) {
                    // Placeholder until the SMS cards (step 2-3) and dashboard (step 4) land.
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(if (BuildConfig.SHEET_URL.isEmpty()) "Add sheet.url to local.properties" else "Expense Tracker")
                    }
                }
            }
        }
    }
}
