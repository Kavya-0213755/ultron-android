package com.ultron.assistant

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.net.URLEncoder
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Optional helper (Accessibility): when Ultron opens a WhatsApp chat with a message typed in, this presses
 * Send — so WhatsApp messages go out hands-free. It only acts for 15 seconds after Ultron asked it to, and
 * only inside WhatsApp.
 */
class AutoSend : AccessibilityService() {
    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean { if (instance === this) instance = null; return super.onUnbind(intent) }
    override fun onInterrupt() {}

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (System.currentTimeMillis() > armedUntil) return
        val pkg = event?.packageName?.toString() ?: return
        if (!pkg.startsWith("com.whatsapp")) return
        val root = rootInActiveWindow ?: return
        val send = root.findAccessibilityNodeInfosByViewId("$pkg:id/send").firstOrNull()
            ?: findByDescription(root, "Send")
            ?: return
        if (send.performAction(AccessibilityNodeInfo.ACTION_CLICK)) {
            armedUntil = 0
            sent?.countDown()
            Thread {
                Thread.sleep(700)
                performGlobalAction(GLOBAL_ACTION_BACK)              // leave the chat
                Thread.sleep(300)
                performGlobalAction(GLOBAL_ACTION_BACK)
            }.start()
        }
    }

    private fun findByDescription(node: AccessibilityNodeInfo, desc: String): AccessibilityNodeInfo? {
        if (node.contentDescription?.toString().equals(desc, ignoreCase = true) && node.isClickable) return node
        for (i in 0 until node.childCount) {
            val c = node.getChild(i) ?: continue
            findByDescription(c, desc)?.let { return it }
        }
        return null
    }

    companion object {
        @Volatile var instance: AutoSend? = null
        @Volatile private var armedUntil = 0L
        @Volatile private var sent: CountDownLatch? = null

        fun enabled(ctx: Context): Boolean {
            val flat = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
                ?: return false
            return flat.contains(ComponentName(ctx, AutoSend::class.java).flattenToString())
        }

        /** Opens the chat with the text and waits (up to 15 s) for Send to be pressed. */
        fun send(ctx: Context, pkg: String, phone: String, text: String): Boolean {
            val latch = CountDownLatch(1)
            sent = latch
            armedUntil = System.currentTimeMillis() + 15_000
            val url = "https://api.whatsapp.com/send?phone=$phone&text=" + URLEncoder.encode(text, "UTF-8")
            return try {
                ctx.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage(pkg)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP))
                latch.await(15, TimeUnit.SECONDS)
            } catch (_: Exception) {
                false
            } finally {
                armedUntil = 0
            }
        }
    }
}
