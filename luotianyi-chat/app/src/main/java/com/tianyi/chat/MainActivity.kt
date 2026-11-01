package com.tianyi.chat

import android.annotation.SuppressLint
import android.app.Activity
import android.app.Dialog
import android.content.ContentValues
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.webkit.CookieManager
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

class MainActivity : Activity() {

    private lateinit var web: WebView
    private val pool = Executors.newCachedThreadPool()
    private var loginDialog: Dialog? = null

    companion object {
        const val CHAT_URL = "https://api.deepseek.com/chat/completions"
        const val SEARCH_URL = "https://api.deepseek.com/v1/tools/search"
        const val UA = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
    }

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        window.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        CookieManager.getInstance().setAcceptCookie(true)

        web = WebView(this)
        web.setBackgroundColor(Color.TRANSPARENT)
        web.settings.apply {
            javaScriptEnabled = true
            domStorageEnabled = true
            allowFileAccess = true
            allowFileAccessFromFileURLs = true
            allowContentAccess = false
            mediaPlaybackRequiresUserGesture = false
            setSupportZoom(false)
            builtInZoomControls = false
            textZoom = 100
            mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
        }
        web.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val u = request.url.toString()
                if (u.startsWith("http://") || u.startsWith("https://")) {
                    try {
                        startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, request.url))
                    } catch (e: Exception) {
                    }
                }
                return true
            }
        }
        web.webChromeClient = WebChromeClient()
        web.addJavascriptInterface(Bridge(), "Android")
        setContentView(web)
        web.loadUrl("file:///android_asset/index.html")
    }

    private fun js(code: String) = runOnUiThread { web.evaluateJavascript(code, null) }

    private fun onReply(cb: String, ok: Boolean, text: String) {
        js("window.__onReply&&window.__onReply(" + JSONObject.quote(cb) + "," + ok + "," + JSONObject.quote(text) + ")")
    }
    private fun onStream(cb: String, kind: String, chunk: String) {
        js("window.__onStream&&window.__onStream(" + JSONObject.quote(cb) + "," + JSONObject.quote(kind) + "," + JSONObject.quote(chunk) + ")")
    }
    private fun onUsage(cb: String, usage: String) {
        js("window.__onUsage&&window.__onUsage(" + JSONObject.quote(cb) + "," + JSONObject.quote(usage) + ")")
    }

    private fun onDone(cb: String, ok: Boolean, err: String, text: String) {
        js("window.__onDone&&window.__onDone(" + JSONObject.quote(cb) + "," + ok + "," + JSONObject.quote(err) + "," + JSONObject.quote(text) + ")")
    }

    private fun key(): String =
        getSharedPreferences("cfg", MODE_PRIVATE).getString("key", "") ?: ""
    private fun cookie(): String =
        getSharedPreferences("cfg", MODE_PRIVATE).getString("cookie", "") ?: ""

    private fun saveCookie() {
        try {
            val cm = CookieManager.getInstance()
            cm.flush()
            val c = cm.getCookie("https://music.163.com") ?: ""
            getSharedPreferences("cfg", MODE_PRIVATE).edit().putString("cookie", c).apply()
        } catch (e: Exception) {
        }
    }

    private fun randomStr(len: Int): String {
        val chars = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789"
        val sb = StringBuilder()
        val r = java.security.SecureRandom()
        repeat(len) { sb.append(chars[r.nextInt(chars.length)]) }
        return sb.toString()
    }

    private fun aesEncrypt(text: String, key: String): String {
        val iv = "0102030405060708"
        val cipher = javax.crypto.Cipher.getInstance("AES/CBC/PKCS5Padding")
        val sk = javax.crypto.spec.SecretKeySpec(key.toByteArray(Charsets.UTF_8), "AES")
        cipher.init(javax.crypto.Cipher.ENCRYPT_MODE, sk, javax.crypto.spec.IvParameterSpec(iv.toByteArray(Charsets.UTF_8)))
        val out = cipher.doFinal(text.toByteArray(Charsets.UTF_8))
        return android.util.Base64.encodeToString(out, android.util.Base64.NO_WRAP)
    }

    private fun rsaEncrypt(text: String): String {
        val reversed = StringBuilder(text).reverse().toString()
        val big = java.math.BigInteger(1, reversed.toByteArray(Charsets.UTF_8))
        val exp = java.math.BigInteger("010001", 16)
        val mod = java.math.BigInteger(
            "00e0b509f6259df8642dbc35662901477df22677ec152b5ff68ace615bb7b725152b3ab17a876aea8a5aa76d2e417629ec4ee341f56135fccf695280104e0312ecbda92557c93870114af6c9d05c4f7f0c3685b7a46bee255932575cce10b424d813cfe4875d3e82047b97ddef52741d546b8e289dc6935b3ece0462db0a22b8e7",
            16
        )
        val enc = big.modPow(exp, mod)
        val hex = enc.toString(16)
        return "0".repeat(maxOf(0, 256 - hex.length)) + hex
    }

    private fun weapi(url: String, params: JSONObject): String {
        val text = params.toString()
        val secret = randomStr(16)
        val first = aesEncrypt(text, "0CoJUm6Qyw8W8jud")
        val second = aesEncrypt(first, secret)
        val encSecKey = rsaEncrypt(secret)
        val form = "params=" + java.net.URLEncoder.encode(second, "UTF-8") + "&encSecKey=" + encSecKey
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.requestMethod = "POST"
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.doOutput = true
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Referer", "https://music.163.com/")
        conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
        conn.setRequestProperty("Cookie", cookie())
        conn.outputStream.use { it.write(form.toByteArray(Charsets.UTF_8)) }
        val code = conn.responseCode
        val raw = (if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream))
            ?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
        return raw
    }

    private fun resolveUrl(id: String): String {
        return try {
            val body = weapi(
                "https://music.163.com/weapi/song/enhance/player/url?csrf_token=",
                JSONObject().put("ids", "[" + id + "]").put("br", 320000).put("csrf_token", "")
            )
            val arr = JSONObject(body).optJSONArray("data")
            if (arr != null && arr.length() > 0) {
                val u = arr.getJSONObject(0).optString("url", "")
                if (u.startsWith("http")) u else ""
            } else ""
        } catch (e: Exception) {
            ""
        }
    }



    private fun cleanHtml(s: String): String {
        var t = s.replace(Regex("<[^>]+>"), "")
        t = t.replace("&amp;", "&").replace("&quot;", "\"").replace("&#39;", "'")
        t = t.replace("&lt;", "<").replace("&gt;", ">").replace("&ensp;", " ").replace("&nbsp;", " ")
        t = t.replace("&#183;", "·").replace("&#0183;", "·")
        t = t.replace(Regex("&#\\d+;"), " ")
        return t.replace(Regex("\\s+"), " ").trim()
    }

    private fun bingSearch(query: String): String {
        val q = java.net.URLEncoder.encode(query, "UTF-8")
        val conn = URL("https://cn.bing.com/search?q=" + q + "&count=12").openConnection() as HttpURLConnection
        conn.connectTimeout = 10000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Accept-Language", "zh-CN,zh;q=0.9")
        val html = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        val arr = org.json.JSONArray()
        val blocks = Regex("<li class=\"b_algo\"[\\s\\S]*?</li>", RegexOption.IGNORE_CASE).findAll(html).take(8)
        for (b in blocks) {
            val v = b.value
            val tm = Regex("<h2[^>]*>\\s*<a[^>]*href=\"([^\"]+)\"[^>]*>([\\s\\S]*?)</a>", RegexOption.IGNORE_CASE).find(v) ?: continue
            val url = tm.groupValues[1]
            val title = cleanHtml(tm.groupValues[2])
            if (title.isEmpty()) continue
            val sm = Regex("<p[^>]*>([\\s\\S]*?)</p>", RegexOption.IGNORE_CASE).find(v)
            val snip = if (sm != null) cleanHtml(sm.groupValues[1]) else ""
            val o = JSONObject()
            o.put("title", title)
            o.put("url", url)
            o.put("snippet", snip)
            o.put("summary", snip)
            o.put("site_name", "Bing")
            arr.put(o)
        }
        return arr.toString()
    }


    private fun openUrl(url: String, withCookie: Boolean): String {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15000
        conn.readTimeout = 30000
        conn.setRequestProperty("User-Agent", UA)
        conn.setRequestProperty("Referer", "https://music.163.com/")
        conn.setRequestProperty("Accept", "application/json, text/plain, */*")
        if (withCookie) {
            val c = cookie()
            if (c.isNotEmpty()) conn.setRequestProperty("Cookie", c)
        }
        return try {
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream)
            val raw = stream?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
            if (code in 200..299) raw else "!" + code + " " + raw.take(200)
        } finally {
            try { conn.disconnect() } catch (e: Exception) {}
        }
    }

    private inner class Bridge {

        @JavascriptInterface
        fun settings(): String {
            val sp = getSharedPreferences("cfg", MODE_PRIVATE)
            val o = JSONObject()
            o.put("key", sp.getString("key", "") ?: "")
            o.put("model", sp.getString("model", "deepseek-flash") ?: "deepseek-flash")
            o.put("temperature", sp.getFloat("temperature", 1.0f).toDouble())
            o.put("persona", sp.getString("persona", "") ?: "")
            o.put("skin", sp.getString("skin", "2") ?: "2")
            o.put("thinking", sp.getString("thinking", "high") ?: "high")
            o.put("web", sp.getString("web", "always") ?: "always")
            o.put("cookie", sp.getString("cookie", "") ?: "")
            o.put("base", sp.getString("base", "") ?: "")
            o.put("provider", sp.getString("provider", "deepseek") ?: "deepseek")
            o.put("relayKey", sp.getString("relayKey", "") ?: "")
            o.put("keysJson", sp.getString("keysJson", "") ?: "")
            o.put("relayOn", sp.getBoolean("relayOn", false))
            return o.toString()
        }

        @JavascriptInterface
        fun save(json: String) {
            try {
                val o = JSONObject(json)
                val sp = getSharedPreferences("cfg", MODE_PRIVATE).edit()
                if (o.has("key")) sp.putString("key", o.getString("key"))
                if (o.has("model")) sp.putString("model", o.getString("model"))
                if (o.has("temperature")) sp.putFloat("temperature", o.getDouble("temperature").toFloat())
                if (o.has("persona")) sp.putString("persona", o.getString("persona"))
                if (o.has("skin")) sp.putString("skin", o.getString("skin"))
                if (o.has("thinking")) sp.putString("thinking", o.getString("thinking"))
                if (o.has("web")) sp.putString("web", o.getString("web"))
                if (o.has("base")) sp.putString("base", o.getString("base"))
                if (o.has("provider")) sp.putString("provider", o.getString("provider"))
                if (o.has("relayKey")) sp.putString("relayKey", o.getString("relayKey"))
                if (o.has("keysJson")) sp.putString("keysJson", o.getString("keysJson"))
                if (o.has("relayOn")) sp.putBoolean("relayOn", o.getBoolean("relayOn"))
                sp.apply()
            } catch (e: Exception) {
            }
        }

        @JavascriptInterface
        fun chat(cb: String, body: String) {
            pool.execute {
                var conn: HttpURLConnection? = null
                try {
                    val k = key()
                    if (k.isBlank()) { onDone(cb, false, "NO_KEY", ""); return@execute }
                    val base = getSharedPreferences("cfg", MODE_PRIVATE).getString("base", "") ?: ""
                    val chatUrl = if (base.isNotBlank()) (if (base.endsWith("/")) base + "chat/completions" else base + "/chat/completions") else CHAT_URL
                    conn = URL(chatUrl).openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 20000
                    conn.readTimeout = 200000
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.setRequestProperty("Authorization", "Bearer " + k)
                    conn.setRequestProperty("Accept", "text/event-stream")
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

                    val code = conn.responseCode
                    if (code !in 200..299) {
                        val raw = (conn.errorStream ?: conn.inputStream)?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
                        onDone(cb, false, "HTTP " + code + ": " + raw.take(400), "")
                        return@execute
                    }
                    val reader = conn.inputStream.bufferedReader(Charsets.UTF_8)
                    var usageJson = ""
                    val full = StringBuilder()
                    val cBuf = StringBuilder()
                    val rBuf = StringBuilder()
                    var lastFlush = System.currentTimeMillis()
                    while (true) {
                        val line = reader.readLine() ?: break
                        if (!line.startsWith("data:")) continue
                        val data = line.substring(5).trim()
                        if (data.isEmpty()) continue
                        if (data == "[DONE]") break
                        val o = try { JSONObject(data) } catch (e: Exception) { continue }
                        val uu = o.optJSONObject("usage")
                        if (uu != null) usageJson = uu.toString()
                        val choices = o.optJSONArray("choices") ?: continue
                        if (choices.length() == 0) continue
                        val d = choices.getJSONObject(0).optJSONObject("delta") ?: continue
                        val cObj = d.opt("content")
                        val rObj = d.opt("reasoning_content")
                        val c = if (cObj is String) cObj else ""
                        val r = if (rObj is String) rObj else ""
                        if (r.isNotEmpty()) rBuf.append(r)
                        if (c.isNotEmpty()) { full.append(c); cBuf.append(c) }
                        if (rBuf.length + cBuf.length > 48 || System.currentTimeMillis() - lastFlush > 160) {
                            if (rBuf.isNotEmpty()) { onStream(cb, "r", rBuf.toString()); rBuf.setLength(0) }
                            if (cBuf.isNotEmpty()) { onStream(cb, "c", cBuf.toString()); cBuf.setLength(0) }
                            lastFlush = System.currentTimeMillis()
                        }
                    }
                    if (rBuf.isNotEmpty()) onStream(cb, "r", rBuf.toString())
                    if (cBuf.isNotEmpty()) onStream(cb, "c", cBuf.toString())
                    if (usageJson.isNotEmpty()) onUsage(cb, usageJson)
                    onDone(cb, true, "", full.toString())
                } catch (e: Exception) {
                    onDone(cb, false, (e.message ?: e.javaClass.simpleName), "")
                } finally {
                    try { conn?.disconnect() } catch (e: Exception) {}
                }
            }
        }

        @JavascriptInterface
        fun search(cb: String, query: String) {
            pool.execute {
                try {
                    val k = key()
                    if (k.isBlank()) { onReply(cb, false, "NO_KEY"); return@execute }
                    val req = JSONObject()
                    req.put("query", query.take(120))
                    req.put("count", 10)
                    req.put("need_content", false)
                    val conn = URL(SEARCH_URL).openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 15000
                    conn.readTimeout = 25000
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.setRequestProperty("Authorization", "Bearer " + k)
                    conn.outputStream.use { it.write(req.toString().toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    val raw = (if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream))
                        ?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
                    if (code in 200..299) onReply(cb, true, raw) else onReply(cb, false, "HTTP " + code + ": " + raw.take(300))
                } catch (e: Exception) {
                    onReply(cb, false, (e.message ?: e.javaClass.simpleName))
                }
            }
        }

        @JavascriptInterface
        fun netGet(cb: String, url: String, withCookie: Boolean) {
            pool.execute {
                try {
                    if (!url.startsWith("https://music.163.com/")) { onReply(cb, false, "BAD_URL"); return@execute }
                    val raw = openUrl(url, withCookie)
                    if (raw.startsWith("!")) onReply(cb, false, raw) else onReply(cb, true, raw)
                } catch (e: Exception) {
                    onReply(cb, false, (e.message ?: "net error"))
                }
            }
        }

        @JavascriptInterface
        fun netUrl(cb: String, id: String) {
            pool.execute {
                var conn: HttpURLConnection? = null
                try {
                    if (id.isBlank()) { onReply(cb, false, "NO_ID"); return@execute }
                    conn = URL("https://music.163.com/song/media/outer/url?id=" + id + ".mp3").openConnection() as HttpURLConnection
                    conn.instanceFollowRedirects = true
                    conn.connectTimeout = 15000
                    conn.readTimeout = 20000
                    conn.setRequestProperty("User-Agent", UA)
                    conn.setRequestProperty("Referer", "https://music.163.com/")
                    val c = cookie()
                    if (c.isNotEmpty()) conn.setRequestProperty("Cookie", c)
                    conn.requestMethod = "GET"
                    val code = conn.responseCode
                    val finalUrl = conn.url.toString()
                    if (code in 200..299 && finalUrl.contains(".mp3")) onReply(cb, true, finalUrl)
                    else onReply(cb, false, "HTTP " + code)
                } catch (e: Exception) {
                    onReply(cb, false, (e.message ?: "url error"))
                } finally {
                    try { conn?.disconnect() } catch (e: Exception) {}
                }
            }
        }

        /** 下载音频到本地缓存，返回 file:// 路径（绕开 WebView 混合内容/防盗链） */
        @JavascriptInterface
        fun netCache(cb: String, id: String) {
            pool.execute {
                if (id.isBlank()) { onReply(cb, false, "NO_ID"); return@execute }
                val f = File(cacheDir, "pl_" + id + ".mp3")
                if (f.exists() && f.length() > 150000L) {
                    onReply(cb, true, "file://" + f.absolutePath)
                    return@execute
                }

                fun plainDownload(startUrl: String, useCookie: Boolean): String {
                    var conn: HttpURLConnection? = null
                    try {
                        var url = startUrl
                        var hops = 0
                        var code = 0
                        while (true) {
                            conn = URL(url).openConnection() as HttpURLConnection
                            conn.instanceFollowRedirects = false
                            conn.connectTimeout = 15000
                            conn.readTimeout = 60000
                            conn.setRequestProperty("User-Agent", UA)
                            conn.setRequestProperty("Referer", "https://music.163.com/")
                            conn.setRequestProperty("Accept", "*/*")
                            if (useCookie && url.contains("music.163.com")) {
                                val ck = cookie()
                                if (ck.isNotEmpty()) conn.setRequestProperty("Cookie", ck)
                            }
                            code = conn.responseCode
                            if (code in 300..399) {
                                val loc = conn.getHeaderField("Location")
                                conn.disconnect()
                                if (loc.isNullOrBlank() || hops >= 6) return "ERR redirect"
                                hops++
                                url = if (loc.startsWith("http")) loc else URL(URL(url), loc).toString()
                                continue
                            }
                            break
                        }
                        if (code !in 200..299) return "ERR HTTP " + code
                        val http = conn ?: return "ERR no conn"
                        val ct = http.contentType ?: ""
                        if (ct.contains("html") || ct.contains("json") || ct.startsWith("text/")) return "RETRY"
                        val len = http.contentLengthLong
                        if (len in 1..120000L) return "ERR 只有试听(" + len + "B)"
                        val tmp = File(cacheDir, "pl_" + id + "_" + hops + ".part")
                        http.inputStream.use { input -> FileOutputStream(tmp).use { out -> input.copyTo(out, 65536) } }
                        if (tmp.length() < 120000L) {
                            val sz = tmp.length()
                            tmp.delete()
                            return "ERR 内容太小(" + sz + "B)"
                        }
                        if (f.exists()) f.delete()
                        tmp.renameTo(f)
                        return "OK"
                    } catch (e: Exception) {
                        return "ERR " + (e.message ?: "download error")
                    } finally {
                        try { conn?.disconnect() } catch (e: Exception) {}
                    }
                }

                var result = "ERR 无可用地址"
                val vipUrl = resolveUrl(id)
                if (vipUrl.isNotEmpty()) {
                    result = plainDownload(vipUrl, false)
                    if (result != "OK" && result != "RETRY") {
                    }
                }
                if (result != "OK") {
                    val fallback = plainDownload("https://music.163.com/song/media/outer/url?id=" + id + ".mp3", true)
                    result = if (fallback == "RETRY") {
                        val f2 = plainDownload("https://music.163.com/song/media/outer/url?id=" + id + ".mp3", false)
                        f2
                    } else fallback
                }
                if (result == "OK") onReply(cb, true, "file://" + f.absolutePath)
                else onReply(cb, false, if (vipUrl.isEmpty()) "这首歌没有授权地址（可能要单独购买或已下架）" else result)
            }
        }


        @JavascriptInterface
        fun writeDiag(text: String) {
            try {
                val name = "洛天依诊断-" + java.text.SimpleDateFormat("yyyyMMdd-HHmmss", java.util.Locale.US).format(java.util.Date()) + ".txt"
                val values = ContentValues()
                values.put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                values.put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                if (Build.VERSION.SDK_INT >= 29) values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: return
                contentResolver.openOutputStream(uri)?.use { os -> os.write(text.toByteArray(Charsets.UTF_8)) }
            } catch (e: Exception) {
            }
        }


        @JavascriptInterface
        fun balance(cb: String) {
            pool.execute {
                try {
                    val k = key()
                    if (k.isBlank()) { onReply(cb, false, "NO_KEY"); return@execute }
                    val conn = URL("https://api.deepseek.com/user/balance").openConnection() as HttpURLConnection
                    conn.connectTimeout = 10000
                    conn.readTimeout = 15000
                    conn.setRequestProperty("Authorization", "Bearer " + k)
                    val code = conn.responseCode
                    val raw = (if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream))?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
                    if (code in 200..299) onReply(cb, true, raw) else onReply(cb, false, "HTTP " + code + " " + raw.take(120))
                } catch (e: Exception) {
                    onReply(cb, false, (e.message ?: "balance error"))
                }
            }
        }


        @JavascriptInterface
        fun listModels(cb: String, base: String, key: String) {
            pool.execute {
                try {
                    var b = if (base.isBlank()) "https://api.deepseek.com" else base
                    if (b.endsWith("/")) b = b.dropLast(1)
                    val conn = URL(b + "/models").openConnection() as HttpURLConnection
                    conn.connectTimeout = 10000
                    conn.readTimeout = 15000
                    conn.setRequestProperty("Authorization", "Bearer " + key)
                    conn.setRequestProperty("Accept", "application/json")
                    val code = conn.responseCode
                    val raw = (if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream))
                        ?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
                    if (code in 200..299) onReply(cb, true, raw) else onReply(cb, false, "HTTP " + code)
                } catch (e: Exception) {
                    onReply(cb, false, (e.message ?: "models error"))
                }
            }
        }



        @JavascriptInterface
        fun searchRaw(cb: String, query: String, apiKey: String) {
            pool.execute {
                try {
                    if (apiKey.isBlank()) { onReply(cb, false, "NO_KEY"); return@execute }
                    val req = JSONObject()
                    req.put("query", query.take(120))
                    req.put("count", 10)
                    req.put("need_content", false)
                    val conn = URL(SEARCH_URL).openConnection() as HttpURLConnection
                    conn.requestMethod = "POST"
                    conn.connectTimeout = 15000
                    conn.readTimeout = 40000
                    conn.doOutput = true
                    conn.setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    conn.setRequestProperty("Authorization", "Bearer " + apiKey)
                    conn.outputStream.use { it.write(req.toString().toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    val raw = (if (code in 200..299) conn.inputStream else (conn.errorStream ?: conn.inputStream))
                        ?.bufferedReader(Charsets.UTF_8)?.readText() ?: ""
                    if (code in 200..299) onReply(cb, true, raw) else onReply(cb, false, "HTTP " + code + ": " + raw.take(200))
                } catch (e: Exception) {
                    onReply(cb, false, (e.message ?: "search error"))
                }
            }
        }


        @JavascriptInterface
        fun login() {
            runOnUiThread {
                try {
                    val dlg = Dialog(this@MainActivity, android.R.style.Theme_DeviceDefault_Light_NoActionBar)
                    val root = LinearLayout(this@MainActivity)
                    root.orientation = LinearLayout.VERTICAL
                    root.setBackgroundColor(Color.WHITE)

                    val bar = LinearLayout(this@MainActivity)
                    bar.orientation = LinearLayout.HORIZONTAL
                    bar.gravity = Gravity.CENTER_VERTICAL
                    bar.setPadding(28, 26, 20, 26)
                    val tip = TextView(this@MainActivity)
                    tip.text = "登录网易云（登录完点右边完成）"
                    tip.textSize = 15f
                    bar.addView(tip, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
                    val done = Button(this@MainActivity)
                    done.text = "完成"
                    done.setOnClickListener {
                        saveCookie()
                        dlg.dismiss()
                        js("window.__onLogin&&window.__onLogin()")
                    }
                    bar.addView(done)
                    root.addView(bar)

                    val wv = WebView(this@MainActivity)
                    wv.settings.javaScriptEnabled = true
                    wv.settings.domStorageEnabled = true
                    wv.settings.userAgentString = UA
                    wv.webViewClient = WebViewClient()
                    wv.loadUrl("https://music.163.com/")
                    root.addView(wv, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

                    dlg.setContentView(root)
                    dlg.setOnDismissListener { saveCookie() }
                    loginDialog = dlg
                    dlg.show()
                } catch (e: Exception) {
                }
            }
        }

        @JavascriptInterface
        fun logout() {
            try {
                getSharedPreferences("cfg", MODE_PRIVATE).edit().remove("cookie").apply()
                CookieManager.getInstance().removeAllCookies(null)
                CookieManager.getInstance().flush()
            } catch (e: Exception) {
            }
        }

        @JavascriptInterface
        fun cookieInfo(): String {
            val c = cookie()
            val o = JSONObject()
            o.put("login", c.isNotEmpty())
            o.put("len", c.length)
            return o.toString()
        }

        @JavascriptInterface
        fun exportFile(name: String, content: String): String {
            return try {
                val safe = name.replace(Regex("[\\\\/:*?\"<>|]"), "_")
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    val values = ContentValues()
                    values.put(MediaStore.MediaColumns.DISPLAY_NAME, safe)
                    values.put(
                        MediaStore.MediaColumns.MIME_TYPE,
                        if (safe.endsWith(".json")) "application/json" else "text/markdown"
                    )
                    values.put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                    val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        ?: return "ERR 无法创建文件"
                    contentResolver.openOutputStream(uri)?.use { it.write(content.toByteArray(Charsets.UTF_8)) }
                    "/sdcard/Download/" + safe
                } else {
                    val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    val f = File(dir, safe)
                    f.writeText(content)
                    f.absolutePath
                }
            } catch (e: Exception) {
                "ERR " + (e.message ?: "unknown")
            }
        }

        @JavascriptInterface
        fun writeInternal(name: String, content: String): String {
            return try {
                val f = File(filesDir, name.replace(Regex("[\\\\/:*?\"<>|]"), "_"))
                f.writeText(content)
                f.absolutePath
            } catch (e: Exception) {
                ""
            }
        }

        @JavascriptInterface
        fun readInternal(name: String): String {
            return try {
                val f = File(filesDir, name.replace(Regex("[\\\\/:*?\"<>|]"), "_"))
                if (f.exists()) f.readText() else ""
            } catch (e: Exception) {
                ""
            }
        }
    }
}

