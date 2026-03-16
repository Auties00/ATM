package it.atm.app.util

import timber.log.Timber

object AppLogger {
    fun init(debug: Boolean) {
        if (debug) {
            Timber.plant(Timber.DebugTree())
        }
    }

    fun d(tag: String, message: String, vararg args: Any?) = Timber.tag(tag).d(message, *args)

    fun w(tag: String, message: String, vararg args: Any?) = Timber.tag(tag).w(message, *args)

    fun e(tag: String, message: String, vararg args: Any?) = Timber.tag(tag).e(message, *args)
}
