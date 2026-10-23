package com.tabgl.kaleido.data

import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Keeps the most recent uncaught crash on disk so it can be shown and copied from Settings. */
object CrashLog {
    private const val FILE = "last_crash.txt"

    fun write(context: Context, threadName: String, error: Throwable) {
        val trace = StringWriter().also { error.printStackTrace(PrintWriter(it)) }.toString()
        val report = "Device: ${Build.MANUFACTURER} ${Build.MODEL}, Android ${Build.VERSION.RELEASE}\n" +
            "Thread: $threadName\n\n$trace"
        runCatching { File(context.filesDir, FILE).writeText(report) }
    }

    fun read(context: Context): String? =
        runCatching { File(context.filesDir, FILE).takeIf { it.exists() }?.readText() }.getOrNull()

    fun clear(context: Context) {
        runCatching { File(context.filesDir, FILE).delete() }
    }
}
