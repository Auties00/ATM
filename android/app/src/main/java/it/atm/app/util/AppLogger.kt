package it.atm.app.util

import timber.log.Timber

object AppLogger {
    fun init(debug: Boolean) {
        if (debug) {
            Timber.plant(Timber.DebugTree())
        }
    }
}
