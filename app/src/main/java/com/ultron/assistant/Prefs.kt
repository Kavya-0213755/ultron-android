package com.ultron.assistant

import android.content.Context

class Prefs(ctx: Context) {
    private val sp = ctx.getSharedPreferences("ultron", Context.MODE_PRIVATE)

    var homeUrl: String
        get() = sp.getString("home", "http://192.168.29.5:8765")!!
        set(v) = sp.edit().putString("home", clean(v)).apply()
    var awayUrl: String
        get() = sp.getString("away", "https://kavya-0213755.tailc396ca.ts.net")!!
        set(v) = sp.edit().putString("away", clean(v)).apply()
    var token: String
        get() = sp.getString("token", "")!!
        set(v) = sp.edit().putString("token", v.trim()).apply()
    var wake: Boolean
        get() = sp.getBoolean("wake", false)
        set(v) = sp.edit().putBoolean("wake", v).apply()
    var speak: Boolean
        get() = sp.getBoolean("speak", true)
        set(v) = sp.edit().putBoolean("speak", v).apply()
    var followUp: Boolean
        get() = sp.getBoolean("followup", true)
        set(v) = sp.edit().putBoolean("followup", v).apply()
    var dnd: Boolean
        get() = sp.getBoolean("dnd", false)
        set(v) = sp.edit().putBoolean("dnd", v).apply()
    var dndReply: String
        get() = sp.getString("dndReply", Dnd.DEFAULT_REPLY)!!
        set(v) = sp.edit().putString("dndReply", v).apply()
    var dndStarredRing: Boolean
        get() = sp.getBoolean("dndStarred", true)
        set(v) = sp.edit().putBoolean("dndStarred", v).apply()
    var dndSilence: Boolean
        get() = sp.getBoolean("dndSilence", true)
        set(v) = sp.edit().putBoolean("dndSilence", v).apply()
    var announceCalls: Boolean
        get() = sp.getBoolean("announceCalls", true)
        set(v) = sp.edit().putBoolean("announceCalls", v).apply()
    var announceMessages: Boolean
        get() = sp.getBoolean("announceMessages", false)
        set(v) = sp.edit().putBoolean("announceMessages", v).apply()
    var speakerOnAnswer: Boolean
        get() = sp.getBoolean("speakerOnAnswer", true)
        set(v) = sp.edit().putBoolean("speakerOnAnswer", v).apply()
    var conversationId: String?
        get() = sp.getString("conv", null)
        set(v) = sp.edit().putString("conv", v).apply()

    companion object {
        fun clean(url: String): String {
            var u = url.trim().trimEnd('/')
            if (u.isNotEmpty() && !u.startsWith("http://") && !u.startsWith("https://")) {
                u = if (u.contains(".ts.net")) "https://$u" else "http://$u"
            }
            return u
        }
    }
}
