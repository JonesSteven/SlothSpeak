package com.slothspeak

import android.app.Application
import com.slothspeak.data.db.SlothSpeakDatabase

class SlothSpeakApplication : Application() {

    lateinit var database: SlothSpeakDatabase
        private set

    override fun onCreate() {
        super.onCreate()
        database = SlothSpeakDatabase.getInstance(this)
    }
}
