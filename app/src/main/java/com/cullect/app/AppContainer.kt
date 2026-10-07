package com.cullect.app

import android.content.Context
import com.cullect.app.data.db.CullectDatabase
import com.cullect.app.data.media.MediaRepository
import com.cullect.app.data.media.PendingMoveStore
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

    private val database = CullectDatabase.getInstance(appContext)

    val mediaRepository = MediaRepository(appContext)
    val settingsRepository = SettingsRepository(appContext)
    val pendingMoveStore = PendingMoveStore(appContext)
    val trashRepository = TrashRepository(appContext, database.trashDao(), pendingMoveStore)
    val monetizationGateway: MonetizationGateway = NoOpMonetizationGateway()
}
