package com.example.data

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

object AppSettings {
    var debugMode by mutableStateOf(true)
    var benchmarkMode by mutableStateOf(false)
    var replaySaving by mutableStateOf(true)
    var ocrDiagnostics by mutableStateOf(true)
}
