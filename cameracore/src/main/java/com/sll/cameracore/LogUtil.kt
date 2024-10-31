package com.sll.cameracore

import android.util.Log

/**
 * @author Shenlinliang
 * @date 2022/11/7
 */
object LogUtil {
    private const val TAG_PREFIX = "CameraLib"

    fun d(tag: String, msg: String) {
        if (shouldLog(tag, Log.DEBUG)) {
            Log.d(addTagPrefix(tag), addMessagePrefix(msg))
        }
    }

    fun i(tag: String, msg: String) {
        if (shouldLog(tag, Log.INFO)) {
            Log.d(addTagPrefix(tag), addMessagePrefix(msg))
        }
    }

    fun e(tag: String, msg: String) {
        if (shouldLog(tag, Log.ERROR)) {
            Log.d(addTagPrefix(tag), addMessagePrefix(msg))
        }
    }

    fun w(tag: String, msg: String) {
        if (shouldLog(tag, Log.WARN)) {
            Log.d(addTagPrefix(tag), addMessagePrefix(msg))
        }
    }

    private fun addTagPrefix(tag: String) = "$TAG_PREFIX${"_"}${tag}"

    private fun addMessagePrefix(msg: String) = "Thread[${Thread.currentThread().name}]: $msg"

    @JvmStatic
    fun shouldLog(tag: String, level: Int): Boolean {
        // The prefix can be used as an override tag to see all logs
        return true
        // return Log.isLoggable(TAG_PREFIX, level) || Log.isLoggable(tag, level)
    }
}