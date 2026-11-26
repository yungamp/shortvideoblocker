package io.github.yungamp.shortvideoblocker

import android.app.Application
import io.github.yungamp.shortvideoblocker.inspector.DumpRepository
import io.github.yungamp.shortvideoblocker.inspector.EventLog
import io.github.yungamp.shortvideoblocker.settings.InspectorSettings
import java.io.File

/** Holds the app-wide singletons (manual DI; the app is too small for a DI framework). */
class App : Application() {
    lateinit var settings: InspectorSettings
        private set
    lateinit var dumps: DumpRepository
        private set
    val eventLog = EventLog()

    override fun onCreate() {
        super.onCreate()
        settings = InspectorSettings(this)
        dumps = DumpRepository(File(filesDir, "dumps"), File(cacheDir, "share"))
    }
}
