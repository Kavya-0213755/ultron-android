package com.ultron.assistant

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.ContactsContract
import androidx.core.content.ContextCompat

/** Finds people in the phone's contacts by how the owner says their name ("Papa", "Rahul office", "mummy"). */
object Contacts {
    data class Person(val name: String, val number: String, val starred: Boolean = false)

    private val RELATIONS = listOf(
        setOf("papa", "dad", "daddy", "father", "pappa", "pitaji", "pita ji", "baba", "abba", "pa"),
        setOf("mummy", "mom", "mother", "mumma", "mum", "maa", "ma", "mama ji", "mamma", "ammi", "amma", "mataji"),
        setOf("bhai", "brother", "bro", "bhaiya", "bhaiyya"),
        setOf("didi", "sister", "sis", "behen"),
        setOf("wife", "biwi", "patni"),
        setOf("husband", "pati"),
        setOf("dadaji", "dada", "grandfather", "grandpa"),
        setOf("dadiji", "dadi", "grandmother", "grandma"),
        setOf("nanaji", "nana"),
        setOf("naniji", "nani"),
        setOf("son", "beta"),
        setOf("daughter", "beti"),
    )

    fun allowed(ctx: Context) =
        ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

    fun all(ctx: Context): List<Person> {
        if (!allowed(ctx)) return emptyList()
        val out = LinkedHashMap<String, Person>()
        ctx.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            arrayOf(
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.STARRED,
                ContactsContract.CommonDataKinds.Phone.TYPE,
            ), null, null, null
        )?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(0) ?: continue
                val number = c.getString(1) ?: continue
                val starred = c.getInt(2) == 1
                val mobile = c.getInt(3) == ContactsContract.CommonDataKinds.Phone.TYPE_MOBILE
                val key = name.lowercase()
                if (!out.containsKey(key) || mobile) out[key] = Person(name, number, starred)
            }
        }
        return out.values.toList()
    }

    private fun clean(s: String) = s.lowercase()
        .replace(Regex("\\b(my|mere|meri|mera|the|ko|se|ji|jee|sir|madam|uncle|aunty)\\b"), " ")
        .replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun similarity(a: String, b: String): Double {
        if (a == b) return 1.0
        val d = levenshtein(a, b)
        return 1.0 - d.toDouble() / maxOf(a.length, b.length).coerceAtLeast(1)
    }

    private fun levenshtein(a: String, b: String): Int {
        val dp = IntArray(b.length + 1) { it }
        for (i in 1..a.length) {
            var prev = dp[0]
            dp[0] = i
            for (j in 1..b.length) {
                val tmp = dp[j]
                dp[j] = minOf(dp[j] + 1, dp[j - 1] + 1, prev + if (a[i - 1] == b[j - 1]) 0 else 1)
                prev = tmp
            }
        }
        return dp[b.length]
    }

    private fun relationOf(word: String) = RELATIONS.firstOrNull { word in it }

    /** Best match for a spoken name, or a plain phone number. */
    fun find(ctx: Context, query: String): Person? {
        val digits = query.filter { it.isDigit() || it == '+' }
        if (digits.count { it.isDigit() } >= 6 && digits.length >= query.replace(" ", "").length - 2) {
            return Person(digits, digits)
        }
        val q = clean(query)
        if (q.isBlank()) return null
        var best: Person? = null
        var bestScore = 0.0
        val qRel = relationOf(q)
        for (p in all(ctx)) {
            val n = clean(p.name)
            if (n.isBlank()) continue
            val words = n.split(" ")
            var score = when {
                n == q -> 1.0
                words.firstOrNull() == q -> 0.93                       // "Rahul" → "Rahul Sharma"
                n.startsWith("$q ") || n.endsWith(" $q") || n.contains(" $q ") -> 0.88
                q.split(" ").all { qw -> words.any { similarity(it, qw) > 0.8 } } -> 0.85
                else -> maxOf(similarity(n, q), words.maxOf { similarity(it, q) } * 0.9)
            }
            if (qRel != null && words.any { it in qRel }) score = maxOf(score, 0.95)   // "papa" ↔ "Dad"
            if (p.starred) score += 0.02
            if (score > bestScore) { bestScore = score; best = p }
        }
        return if (bestScore >= 0.72) best else null
    }

    fun nameFor(ctx: Context, number: String?): String? {
        if (number.isNullOrBlank() || !allowed(ctx)) return null
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return try {
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.DISPLAY_NAME), null, null, null)
                ?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
        } catch (_: Exception) { null }
    }

    fun isStarred(ctx: Context, number: String?): Boolean {
        if (number.isNullOrBlank() || !allowed(ctx)) return false
        val uri = Uri.withAppendedPath(ContactsContract.PhoneLookup.CONTENT_FILTER_URI, Uri.encode(number))
        return try {
            ctx.contentResolver.query(uri, arrayOf(ContactsContract.PhoneLookup.STARRED), null, null, null)
                ?.use { c -> c.moveToFirst() && c.getInt(0) == 1 } ?: false
        } catch (_: Exception) { false }
    }

    /** For WhatsApp links: digits with the country code (India +91 if none). */
    fun international(number: String): String {
        var d = number.filter { it.isDigit() }
        if (number.trim().startsWith("+")) return d
        d = d.trimStart('0')
        return if (d.length == 10) "91$d" else d
    }
}
