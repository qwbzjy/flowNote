package com.wenbo.flownote

import android.app.Application
import com.wenbo.flownote.di.AppContainer

class FlowNoteApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.purgeImageOrphansOnStartup()
    }
}
