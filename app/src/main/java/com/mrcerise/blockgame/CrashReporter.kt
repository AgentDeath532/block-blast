package com.mrcerise.blockgame

import android.app.Application
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

/** Captures any uncaught exception to a private file so it can be reported/shown. */
object CrashReporter {

    private const val FILE = "crash.txt"

    fun install(application: Application) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            try {
                write(application, thread, throwable)
            } catch (_: Throwable) {
                // never let crash handling itself crash
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun body(thread: Thread?, throwable: Throwable): String {
        val sw = StringWriter()
        throwable.printStackTrace(PrintWriter(sw))
        return buildString {
            append("Block Blast crash report\n")
            append("Device : ").append(Build.MANUFACTURER).append(' ').append(Build.MODEL).append('\n')
            append("Android: ").append(Build.VERSION.RELEASE)
            append(" (SDK ").append(Build.VERSION.SDK_INT).append(")\n")
            append("Thread : ").append(thread?.name ?: "?").append("\n\n")
            append(sw.toString())
        }
    }

    fun write(context: Context, thread: Thread?, throwable: Throwable) {
        try {
            File(context.filesDir, FILE).writeText(body(thread, throwable))
        } catch (_: Throwable) {
        }
    }

    fun read(context: Context): String? =
        try {
            val f = File(context.filesDir, FILE)
            if (f.exists()) f.readText() else null
        } catch (_: Throwable) {
            null
        }

    fun clear(context: Context) {
        try {
            File(context.filesDir, FILE).delete()
        } catch (_: Throwable) {
        }
    }
}

class BlockBlastApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        CrashReporter.install(this)
    }
}
