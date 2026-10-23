package com.tabgl.kaleido.data

import android.content.Context
import android.net.Uri
import android.util.Xml
import java.io.InputStream
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okio.BufferedSink
import okio.source
import org.xmlpull.v1.XmlPullParser

/** A small WebDAV client: create folders, list files, and upload and download them. */
class WebDavClient(baseUrl: String, user: String, password: String) {
    private val root = baseUrl.trim().trimEnd('/')
    private val auth = Credentials.basic(user, password)
    private val http = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(300, TimeUnit.SECONDS)
        .build()

    private fun urlFor(path: String): String =
        root + "/" + path.split('/').filter { it.isNotEmpty() }.joinToString("/") { Uri.encode(it) }

    private fun call(method: String, path: String, body: RequestBody? = null, depth: String? = null): okhttp3.Response {
        val builder = Request.Builder().url(urlFor(path)).header("Authorization", auth).method(method, body)
        if (depth != null) builder.header("Depth", depth)
        return http.newCall(builder.build()).execute()
    }

    /** Creates the folder. A 405 answer means it already exists, which is fine. */
    fun ensureFolder(path: String) {
        call("MKCOL", path).use { }
    }

    /** Names of the files (not folders) directly inside [path]. */
    fun listFileNames(path: String): List<String> {
        val propfind = """<?xml version="1.0" encoding="utf-8"?>""" +
            """<d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""
        val body = propfind.toRequestBody("application/xml; charset=utf-8".toMediaType())
        call("PROPFIND", path, body, depth = "1").use { response ->
            if (!response.isSuccessful) error("Listing failed: HTTP ${response.code}")
            val parser = Xml.newPullParser()
            parser.setInput(response.body!!.byteStream(), "UTF-8")
            val names = mutableListOf<String>()
            var event = parser.eventType
            while (event != XmlPullParser.END_DOCUMENT) {
                if (event == XmlPullParser.START_TAG && parser.name.substringAfter(':') == "href") {
                    val href = parser.nextText()
                    if (!href.endsWith("/")) names += Uri.decode(href.substringAfterLast('/'))
                }
                event = parser.next()
            }
            return names
        }
    }

    fun upload(path: String, mime: String, open: () -> InputStream) {
        val body = object : RequestBody() {
            override fun contentType() = mime.toMediaType()
            override fun writeTo(sink: BufferedSink) {
                open().use { sink.writeAll(it.source()) }
            }
        }
        call("PUT", path, body).use { if (!it.isSuccessful) error("Upload failed: HTTP ${it.code}") }
    }

    fun download(path: String, consume: (InputStream) -> Unit) {
        call("GET", path).use { response ->
            if (!response.isSuccessful) error("Download failed: HTTP ${response.code}")
            response.body!!.byteStream().use(consume)
        }
    }
}

/** Two-way sync with a WebDAV folder: uploads what the server lacks, downloads what this phone lacks. */
object WebDavSync {
    const val FOLDER = "Kaleido"
    private const val LOCAL_SUBDIR = "WebDAV"

    /** Returns a one-line summary for the UI. Errors come back as text rather than being thrown. */
    suspend fun run(context: Context, settings: AppSettings, onProgress: (String) -> Unit): String =
        withContext(Dispatchers.IO) {
            try {
                val client = WebDavClient(settings.webdavUrl, settings.webdavUser, settings.webdavPassword)
                client.ensureFolder(FOLDER)
                val tag = settings.deviceTag

                // Upload: every local photo the server doesn't have yet.
                val synced = settings.syncedIds().toMutableSet()
                val toUpload = MediaRepository.loadPhotos(context).filter { it.id !in synced }
                toUpload.forEachIndexed { index, photo ->
                    onProgress("Uploading ${index + 1} of ${toUpload.size}")
                    val remoteName = "${tag}_${photo.id}_${photo.name}"
                    client.upload("$FOLDER/$remoteName", mimeFor(photo.name)) {
                        context.contentResolver.openInputStream(photo.uri) ?: error("Can't read ${photo.name}")
                    }
                    synced += photo.id
                    // Checkpoint now and then, so a dropped connection doesn't redo the whole batch.
                    if ((index + 1) % 25 == 0) settings.saveSyncedIds(synced)
                }
                settings.saveSyncedIds(synced)

                // Download: server files that aren't from this phone and aren't already here.
                val downloaded = settings.downloadedNames().toMutableSet()
                val fresh = client.listFileNames(FOLDER)
                    .filter { it !in downloaded && !it.startsWith("${tag}_") }
                fresh.forEachIndexed { index, remoteName ->
                    onProgress("Downloading ${index + 1} of ${fresh.size}")
                    // Remote names are "<tag>_<id>_<name>"; keep only the original name locally.
                    val displayName = remoteName.split('_', limit = 3).getOrNull(2) ?: remoteName
                    client.download("$FOLDER/$remoteName") { input ->
                        writePicture(context, displayName, mimeFor(displayName), LOCAL_SUBDIR) { out ->
                            input.copyTo(out)
                            true
                        }
                    }
                    downloaded += remoteName
                }
                settings.saveDownloadedNames(downloaded)
                "Uploaded ${toUpload.size}, downloaded ${fresh.size}"
            } catch (e: Exception) {
                "Sync failed: ${e.message ?: e.javaClass.simpleName}"
            }
        }
}

fun mimeFor(name: String): String = when (name.substringAfterLast('.', "").lowercase(Locale.ROOT)) {
    "jpg", "jpeg" -> "image/jpeg"
    "png" -> "image/png"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "heic", "heif" -> "image/heic"
    else -> "application/octet-stream"
}
