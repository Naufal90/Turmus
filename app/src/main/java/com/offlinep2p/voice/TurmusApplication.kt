package com.offlinep2p.voice

import android.app.Application
import com.offlinep2p.core.common.DebugLogger

class TurmusApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        DebugLogger.info(
            "APP",
            "Application started",
        )

        installCrashHandler()
    }

    private fun installCrashHandler() {
        val previousHandler =
            Thread.getDefaultUncaughtExceptionHandler()

        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->

            DebugLogger.error(
                tag = "CRASH",
                message = "Uncaught exception on thread=${thread.name}",
                throwable = throwable,
            )

            previousHandler?.uncaughtException(
                thread,
                throwable,
            )
        }
    }
}
