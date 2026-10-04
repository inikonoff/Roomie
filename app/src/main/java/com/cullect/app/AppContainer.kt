package com.cullect.app

import android.content.Context
import com.cullect.app.data.db.CullectDatabase
import com.cullect.app.data.media.MediaRepository
import com.cullect.app.data.monetization.MonetizationGateway
import com.cullect.app.data.monetization.NoOpMonetizationGateway
import com.cullect.app.data.settings.SettingsRepository
import com.cullect.app.data.trash.TrashRepository

/**
 * Hand-rolled DI container. The app is small enough (a handful of repositories, no multi-module
 * setup) that a DI framework would add build complexity without buying anything here.
 */
class AppContainer(context: Context) {
    private val appContext = context.applicationContext

    init {
        CrashReporter.mark(appContext, "AppContainer:before CullectDatabase.getInstance()")
    }

    private val database = CullectDatabase.getInstance(appContext).also {
        CrashReporter.mark(appContext, "AppContainer:after CullectDatabase.getInstance()")
    }

    val mediaRepository = MediaRepository(appContext).also {
        CrashReporter.mark(appContext, "AppContainer:after MediaRepository")
    }
    val settingsRepository = SettingsRepository(appContext).also {
        CrashReporter.mark(appContext, "AppContainer:after SettingsRepository")
    }
    val trashRepository = TrashRepository(appContext, database.trashDao()).also {
        CrashReporter.mark(appContext, "AppContainer:after TrashRepository")
    }
    val monetizationGateway: MonetizationGateway = NoOpMonetizationGateway()

    init {
        CrashReporter.mark(appContext, "AppContainer:done")
    }
}
