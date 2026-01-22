package com.bitchat.android.util

import android.util.Log

private const val DEFAULT_TAG = "bitchat"

/**
 * Sends a DEBUG log message.
 * @param message The message you would like logged.
 * @param tag Used to identify the source of a log message. It usually identifies
 *        the class or activity where the log call occurs. Defaults to "bitchat".
 */
fun logd(message: String, tag: String = DEFAULT_TAG) {
    Log.d(tag, "####"+message)
}

/**
 * Sends an ERROR log message.
 * @param message The message you would like logged.
 * @param throwable An exception to log.
 * @param tag Used to identify the source of a log message. It usually identifies
 *        the class or activity where the log call occurs. Defaults to "bitchat".
 */
fun loge(message: String, throwable: Throwable? = null, tag: String = DEFAULT_TAG) {
    Log.e(tag, message, throwable)
}

/**
 * Sends a WARN log message.
 * @param message The message you would like logged.
 * @param tag Used to identify the source of a log message. It usually identifies
 *        the class or activity where the log call occurs. Defaults to "bitchat".
 */
fun logw(message: String, tag: String = DEFAULT_TAG) {
    Log.w(tag, message)
}
