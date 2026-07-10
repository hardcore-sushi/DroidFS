package sushi.hardcore.droidfs.util

import android.app.ActivityManager
import android.app.Application
import android.content.Context
import android.os.Build
import android.util.Log
import java.io.PrintWriter
import java.io.StringWriter

object CrashDiagnostics {
    private const val TAG = "CrashDiagnostics"
    private const val PREFS = "crash_diagnostics"
    private const val KEY_PENDING_REPORT = "pending_report"
    private const val KEY_LAST_EXIT_TIMESTAMP = "last_exit_timestamp"
    private const val KEY_BREADCRUMBS = "breadcrumbs"
    private const val MAX_BREADCRUMBS = 5000

    fun install(application: Application) {
        capturePreviousProcessExit(application)
        val defaultHandler = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            storeReport(
                application,
                buildString {
                    appendLine("Uncaught exception on thread ${thread.name}")
                    appendLine()
                    appendLine(stackTrace(throwable))
                    appendBreadcrumbs(application)
                }
            )
            defaultHandler?.uncaughtException(thread, throwable)
        }
    }

    fun record(context: Context, message: String) {
        Log.d(TAG, message)
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val line = "${System.currentTimeMillis()}: $message\n"
        val current = prefs.getString(KEY_BREADCRUMBS, "") ?: ""
        val combined = (current + line).takeLast(MAX_BREADCRUMBS)
        prefs.edit().putString(KEY_BREADCRUMBS, combined).apply()
    }

    fun consumeReport(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val report = prefs.getString(KEY_PENDING_REPORT, null)
        if (report != null) {
            prefs.edit().remove(KEY_PENDING_REPORT).apply()
        }
        return report
    }

    fun stackTrace(throwable: Throwable): String {
        val writer = StringWriter()
        throwable.printStackTrace(PrintWriter(writer))
        return writer.toString()
    }

    private fun capturePreviousProcessExit(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return
        }
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastTimestamp = prefs.getLong(KEY_LAST_EXIT_TIMESTAMP, 0)
        val activityManager = context.getSystemService(ActivityManager::class.java)
        val exitInfo = activityManager.getHistoricalProcessExitReasons(context.packageName, 0, 5)
            .firstOrNull { info ->
                info.timestamp > lastTimestamp &&
                    (info.reason == ApplicationExitInfoCompat.REASON_CRASH ||
                        info.reason == ApplicationExitInfoCompat.REASON_CRASH_NATIVE ||
                        info.reason == ApplicationExitInfoCompat.REASON_ANR)
            } ?: return

        prefs.edit().putLong(KEY_LAST_EXIT_TIMESTAMP, exitInfo.timestamp).apply()
        storeReport(
            context,
            buildString {
                appendLine("Previous process exit")
                appendLine("reason=${reasonName(exitInfo.reason)}")
                appendLine("status=${exitInfo.status}")
                appendLine("importance=${exitInfo.importance}")
                appendLine("process=${exitInfo.processName}")
                appendLine("timestamp=${exitInfo.timestamp}")
                if (!exitInfo.description.isNullOrBlank()) {
                    appendLine("description=${exitInfo.description}")
                }
                appendBreadcrumbs(context)
            }
        )
    }

    private fun storeReport(context: Context, report: String) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_PENDING_REPORT, report.take(12000))
            .commit()
    }

    private fun StringBuilder.appendBreadcrumbs(context: Context) {
        val breadcrumbs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getString(KEY_BREADCRUMBS, null)
        if (!breadcrumbs.isNullOrBlank()) {
            appendLine()
            appendLine("Recent breadcrumbs:")
            append(breadcrumbs)
        }
    }

    private fun reasonName(reason: Int): String {
        return when (reason) {
            ApplicationExitInfoCompat.REASON_CRASH -> "CRASH"
            ApplicationExitInfoCompat.REASON_CRASH_NATIVE -> "CRASH_NATIVE"
            ApplicationExitInfoCompat.REASON_ANR -> "ANR"
            else -> reason.toString()
        }
    }

    private object ApplicationExitInfoCompat {
        const val REASON_CRASH = 4
        const val REASON_CRASH_NATIVE = 5
        const val REASON_ANR = 6
    }
}
