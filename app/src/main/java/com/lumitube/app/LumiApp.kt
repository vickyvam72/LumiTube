package com.lumitube.app

import android.app.Application
import com.lumitube.app.data.Session
import com.lumitube.app.player.PlaybackManager

class LumiApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Session.init(this)
        PlaybackManager.init(this)
    }
}
