package com.lumen.player.data.nas

import android.util.Xml
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.asRequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.xmlpull.v1.XmlPullParser
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.net.URLDecoder
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

class WebDavNasClient(override val host: String, private val cfg: NasConfig, base: OkHttpClient) : NasClient {

    private val http = base.newBuilder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            val req = chain.request()
            if (cfg.username.isBlank()) chain.proceed(req)
            else chain.proceed(req.newBuilder().header("Authorization", Credentials.basic(cfg.username, cfg.password)).build())
        }
        .build()

    private val scheme = if (cfg.useHttps) "https" else "http"
    private val port = if (cfg.port > 0) cfg.port else if (cfg.useHttps) 5006 else 5005
    private val basePath = joinPath(cfg.share, cfg.rootPath)

    private fun enc(path: String) = path.split('/').filter { it.isNotEmpty() }
        .joinToString("/") { URLEncoder.encode(it, "UTF-8").replace("+", "%20") }

    private fun url(rel: String, dir: Boolean = false): String {
        val p = enc(joinPath(basePath, rel))
        return "$scheme://$host:$port/$p" + if (dir && p.isNotEmpty()) "/" else ""
    }

    private fun Response.check(): Response {
        if (!isSuccessful && code != 207) { close(); throw IOException("WebDAV $code ${message}") }
        return this
    }

    fun ping() {
        http.newCall(Request.Builder().url(url("", true)).method("PROPFIND", PROPFIND_BODY.toRequestBody(XML)).header("Depth", "0").build())
            .execute().check().close()
    }

    override fun list(path: String): List<NasEntry> {
        val req = Request.Builder().url(url(path, true))
            .method("PROPFIND", PROPFIND_BODY.toRequestBody(XML)).header("Depth", "1").build()
        http.newCall(req).execute().check().use { resp ->
            val selfPath = "/" + enc(joinPath(basePath, path))
            return parse(resp.body!!.byteStream())
                .filter { URLDecoder.decode(it.href.trimEnd('/'), "UTF-8") != URLDecoder.decode(selfPath.trimEnd('/'), "UTF-8") &&
                        !it.href.trimEnd('/').endsWith(selfPath.trimEnd('/')) }
                .map {
                    val name = URLDecoder.decode(it.href.trimEnd('/').substringAfterLast('/'), "UTF-8")
                    NasEntry(name, joinPath(path, name), it.dir, it.size, it.mtime)
                }
        }
    }

    override fun open(path: String): NasFile {
        val u = url(path)
        val head = http.newCall(Request.Builder().url(u).head().build()).execute().check()
        val size = head.header("Content-Length")?.toLongOrNull() ?: -1L
        head.close()
        return object : NasFile {
            override val size: Long = size
            private var stream: InputStream? = null
            private var resp: Response? = null
            private var streamPos = -1L

            private fun reopen(pos: Long) {
                runCatching { resp?.close() }
                val r = http.newCall(Request.Builder().url(u).header("Range", "bytes=$pos-").build()).execute().check()
                resp = r; stream = r.body!!.byteStream(); streamPos = pos
                if (r.code == 200 && pos > 0) { // server ignored Range — skip ahead
                    var skip = pos
                    while (skip > 0) { val s = stream!!.skip(skip); if (s <= 0) break; skip -= s }
                }
            }

            @Synchronized
            override fun read(position: Long, buffer: ByteArray, offset: Int, length: Int): Int {
                if (size in 0..position) return -1
                if (stream == null || position != streamPos) reopen(position)
                val n = stream!!.read(buffer, offset, length)
                if (n > 0) streamPos += n
                return n
            }

            override fun close() { runCatching { resp?.close() } }
        }
    }

    override fun ensureDir(path: String) {
        var acc = ""
        for (part in path.trim('/').split('/').filter { it.isNotEmpty() }) {
            acc = joinPath(acc, part)
            if (!exists(acc)) http.newCall(Request.Builder().url(url(acc, true)).method("MKCOL", null).build()).execute().close()
        }
    }

    override fun exists(path: String): Boolean = runCatching {
        http.newCall(Request.Builder().url(url(path)).method("PROPFIND", PROPFIND_BODY.toRequestBody(XML)).header("Depth", "0").build())
            .execute().use { it.isSuccessful || it.code == 207 }
    }.getOrDefault(false)

    override fun upload(local: File, path: String) {
        ensureDir(parentOf(path))
        http.newCall(Request.Builder().url(url(path)).put(local.asRequestBody("application/octet-stream".toMediaType())).build())
            .execute().check().close()
    }

    override fun close() {}

    private data class DavItem(val href: String, val dir: Boolean, val size: Long, val mtime: Long)

    private fun parse(input: InputStream): List<DavItem> {
        val out = mutableListOf<DavItem>()
        val p = Xml.newPullParser()
        p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        p.setInput(input, null)
        var href = ""; var dir = false; var size = 0L; var mtime = 0L; var tag = ""
        val fmt = SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss zzz", Locale.US)
        while (p.next() != XmlPullParser.END_DOCUMENT) {
            when (p.eventType) {
                XmlPullParser.START_TAG -> {
                    tag = p.name
                    if (tag == "response") { href = ""; dir = false; size = 0; mtime = 0 }
                    if (tag == "collection") dir = true
                }
                XmlPullParser.TEXT -> {
                    val t = p.text.trim()
                    if (t.isNotEmpty()) when (tag) {
                        "href" -> href = t
                        "getcontentlength" -> size = t.toLongOrNull() ?: 0
                        "getlastmodified" -> mtime = runCatching { fmt.parse(t)!!.time }.getOrDefault(0)
                    }
                }
                XmlPullParser.END_TAG -> {
                    if (p.name == "response" && href.isNotEmpty()) out += DavItem(href, dir, size, mtime)
                    tag = ""
                }
            }
        }
        return out
    }

    private companion object {
        val XML = "application/xml; charset=utf-8".toMediaType()
        const val PROPFIND_BODY = """<?xml version="1.0" encoding="utf-8"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/><d:getcontentlength/><d:getlastmodified/></d:prop></d:propfind>"""
    }
}
