package com.cullect.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import com.cullect.app.data.settings.LanguageMode
import com.cullect.app.data.settings.CullectSettings
import com.cullect.app.data.settings.ThemeMode
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

        setContent {
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
                    CullectNavHost(viewModelFactory = viewModelFactory)
                }
            }
        }
    }
}
