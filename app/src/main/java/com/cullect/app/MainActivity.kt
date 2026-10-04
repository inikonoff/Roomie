package com.cullect.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.cullect.app.data.settings.LanguageMode
import com.cullect.app.data.settings.CullectSettings
import com.cullect.app.data.settings.ThemeMode
import com.cullect.app.ui.CrashScreen
import com.cullect.app.ui.ViewModelFactory
import com.cullect.app.ui.navigation.CullectNavHost
import com.cullect.app.ui.strings.EnglishStrings
import com.cullect.app.ui.strings.LocalAppStrings
import com.cullect.app.ui.strings.RussianStrings
import com.cullect.app.ui.theme.CullectTheme
import java.util.Locale

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as CullectApplication).container
        val viewModelFactory = ViewModelFactory(container)

        // If we're here at all, this launch's Application.onCreate already finished successfully
        // (Android always completes it before starting an Activity), so a crash log or a
        // checkpoint short of "onCreate:done" can only be left over from a PREVIOUS attempt.
        val crashLog = CrashReporter.readAndClear(this)
        val staleCheckpoint = CrashReporter.readCheckpoint(this)?.takeIf { !it.endsWith("onCreate:done") }
        val diagnosticText = crashLog ?: staleCheckpoint?.let {
            "No exception was caught, but a previous launch didn't finish starting.\n\nLast checkpoint reached:\n$it"
        }

        setContent {
            var showDiagnostic by remember { mutableStateOf(diagnosticText != null) }
            val settings by container.settingsRepository.settings.collectAsState(initial = CullectSettings())
            val darkTheme = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> isSystemInDarkTheme()
            }
            val strings = when (settings.languageMode) {
                LanguageMode.ENGLISH -> EnglishStrings
                LanguageMode.RUSSIAN -> RussianStrings
                LanguageMode.SYSTEM -> if (Locale.getDefault().language == "ru") RussianStrings else EnglishStrings
            }
            CullectTheme(darkTheme = darkTheme) {
                CompositionLocalProvider(LocalAppStrings provides strings) {
                    if (showDiagnostic && diagnosticText != null) {
                        CrashScreen(stackTrace = diagnosticText, onContinue = { showDiagnostic = false })
                    } else {
                        CullectNavHost(viewModelFactory = viewModelFactory)
                    }
                }
            }
        }
    }
}
