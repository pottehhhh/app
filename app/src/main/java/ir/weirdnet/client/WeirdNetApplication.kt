package ir.weirdnet.client

import android.app.Application
import ir.weirdnet.client.data.db.AppDatabase
import ir.weirdnet.client.data.repository.ProfileRepository
import ir.weirdnet.client.data.repository.SettingsRepository
import ir.weirdnet.client.util.WeirdLogger

class WeirdNetApplication : Application() {

    lateinit var database: AppDatabase
        private set
    lateinit var profileRepository: ProfileRepository
        private set
    lateinit var settingsRepository: SettingsRepository
        private set

    override fun onCreate() {
        super.onCreate()
        database = AppDatabase.getInstance(this)
        profileRepository = ProfileRepository(database.profileDao())
        settingsRepository = SettingsRepository(this)
        WeirdLogger.init(database)
    }

    companion object {
        fun from(context: android.content.Context): WeirdNetApplication =
            context.applicationContext as WeirdNetApplication
    }
}
