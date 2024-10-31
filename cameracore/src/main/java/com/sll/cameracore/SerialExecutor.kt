package com.sll.cameracore

import android.os.Handler
import java.util.concurrent.Executor

/**
 *  author : konone
 *  date : 2022/9/24
 */
class SerialExecutor(private val handler: Handler) : Executor {
    private val tasks: java.util.ArrayDeque<Runnable> = java.util.ArrayDeque()
    private var active: Runnable? = null

    fun clear() {
        LogUtil.d(TAG, "clear: clear task ${tasks.size}")
        tasks.clear()
    }

    override fun execute(command: Runnable) {
        tasks.add(Runnable {
            try {
                command.run()
            } finally {
                scheduleNext()
            }
        })
        if (active == null) {
            scheduleNext()
        }
    }

    private fun scheduleNext() {
        active = tasks.poll()
        active?.let {
            handler.post(it)
        }
    }

    companion object {
        private const val TAG = "SerialExecutor"
    }
}