package com.lumitube.app.data

import org.json.JSONArray
import org.json.JSONObject

/** Navigasi aman di dalam JSON: path berisi String (key) atau Int (index array). */
fun Any?.at(vararg path: Any): Any? {
    var cur: Any? = this
    for (p in path) {
        cur = when {
            cur is JSONObject && p is String -> cur.opt(p)
            cur is JSONArray && p is Int -> if (p >= 0 && p < cur.length()) cur.opt(p) else null
            else -> return null
        }
        if (cur == null || cur == JSONObject.NULL) return null
    }
    return cur
}

fun Any?.obj(vararg path: Any): JSONObject? = at(*path) as? JSONObject
fun Any?.arr(vararg path: Any): JSONArray? = at(*path) as? JSONArray
fun Any?.str(vararg path: Any): String? = (at(*path) as? String)?.takeIf { it.isNotEmpty() }

fun JSONArray.objects(): List<JSONObject> = (0 until length()).mapNotNull { optJSONObject(it) }

/** Ambil teks dari format YouTube: simpleText, runs[], atau content (view model). */
fun text(node: Any?): String? {
    if (node is String) return node.takeIf { it.isNotBlank() }
    val o = node as? JSONObject ?: return null
    o.str("simpleText")?.let { return it }
    o.arr("runs")?.let { runs ->
        val s = runs.objects().joinToString("") { it.optString("text") }
        if (s.isNotBlank()) return s
    }
    o.str("content")?.let { return it }
    return null
}

/**
 * Menelusuri seluruh pohon JSON. [visit] dipanggil untuk setiap objek yang punya key,
 * kembalikan true bila objek sudah ditangani (tidak perlu ditelusuri lebih dalam).
 */
fun walk(node: Any?, visit: (String, JSONObject) -> Boolean) {
    when (node) {
        is JSONObject -> {
            val keys = node.keys()
            while (keys.hasNext()) {
                val k = keys.next()
                when (val v = node.opt(k)) {
                    is JSONObject -> if (!visit(k, v)) walk(v, visit)
                    is JSONArray -> walk(v, visit)
                }
            }
        }
        is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i), visit)
    }
}

fun findFirst(node: Any?, key: String): JSONObject? {
    var result: JSONObject? = null
    walk(node) { k, v ->
        if (result != null) {
            true
        } else if (k == key) {
            result = v
            true
        } else {
            false
        }
    }
    return result
}

fun findAll(node: Any?, key: String): List<JSONObject> {
    val out = mutableListOf<JSONObject>()
    walk(node) { k, v ->
        if (k == key) {
            out.add(v)
            true
        } else {
            false
        }
    }
    return out
}

fun fixUrl(u: String): String = if (u.startsWith("//")) "https:$u" else u

/** Pilih thumbnail terbesar dari {thumbnails:[...]}, {sources:[...]} atau array langsung. */
fun bestThumb(node: Any?): String? {
    val arr = (node as? JSONArray) ?: node.arr("thumbnails") ?: node.arr("sources") ?: return null
    val list = arr.objects().filter { it.str("url") != null }
    if (list.isEmpty()) return null
    val best = list.maxByOrNull { it.optInt("width", 0) * it.optInt("height", 0) } ?: list.last()
    val chosen = if (best.optInt("width", 0) == 0) list.last() else best
    return chosen.str("url")?.let { fixUrl(it) }
}

private val sizeRegex = Regex("=w\\d+-h\\d+")

/** Thumbnail YT Music biasanya kecil (w60-h60), naikkan resolusinya. */
fun musicThumb(node: Any?): String? {
    val url = bestThumb(node) ?: return null
    return if (sizeRegex.containsMatchIn(url)) url.replace(sizeRegex, "=w544-h544") else url
}
