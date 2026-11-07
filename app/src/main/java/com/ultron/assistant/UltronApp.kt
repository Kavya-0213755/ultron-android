package com.ultron.assistant

import android.app.Activity
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Bundle

class UltronApp : Application() {
    lateinit var prefs: Prefs
        private set
    lateinit var client: UltronClient
        private set
    lateinit var speaker: Speaker
        private set

    /** Is an Ultron screen open right now? (if not, approvals are shown as notifications) */
    @Volatile var visible = 0

    override fun onCreate() {
        super.onCreate()
        instance = this
        prefs = Prefs(this)
        client = UltronClient(this, prefs)
        speaker = Speaker(this)
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CH_LISTEN, "Listening for “Hey Ultron”", NotificationManager.IMPORTANCE_LOW)
        )
        nm.createNotificationChannel(
            NotificationChannel(CH_ASK, "Approvals and answers", NotificationManager.IMPORTANCE_HIGH)
        )
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) { visible++ }
            override fun onActivityPaused(a: Activity) { visible = (visible - 1).coerceAtLeast(0) }
            override fun onActivityCreated(a: Activity, b: Bundle?) {}
            override fun onActivityStarted(a: Activity) {}
            override fun onActivityStopped(a: Activity) {}
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
            override fun onActivityDestroyed(a: Activity) {}
        })
        Dnd.on.value = prefs.dnd
        if (prefs.token.isNotBlank()) client.start()
    }

    companion object {
        const val CH_LISTEN = "listen"
        const val CH_ASK = "ask"
        lateinit var instance: UltronApp
            private set
    }
}
