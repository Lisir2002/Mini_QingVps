package com.vps.qing

import android.app.Application
import com.vps.qing.core.AppContainer

class QingVpsApp : Application() {
    override fun onCreate() {
        super.onCreate()
        AppContainer.init(this)
    }
}
