package com.roomie.app.ui.screens.logs

import androidx.lifecycle.ViewModel
import com.roomie.app.AppLogger
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class LogsViewModel : ViewModel() {
    private val _lines = MutableStateFlow(AppLogger.snapshot())
    val lines: StateFlow<List<String>> = _lines.asStateFlow()

    fun refresh() {
        _lines.value = AppLogger.snapshot()
    }

    fun clear() {
        AppLogger.clear()
        refresh()
    }
}
