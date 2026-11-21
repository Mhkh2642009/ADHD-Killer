package com.adhdkiller.app

import android.app.Application

class ADHDKillerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        container.bootstrap()
    }
}
