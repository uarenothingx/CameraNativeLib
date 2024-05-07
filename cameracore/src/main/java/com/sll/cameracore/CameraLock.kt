package com.sll.cameracore

import android.util.Log
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * @author Shenlinliang
 * @date 2022/11/7
 */
class CameraLock(permits: Int) : Semaphore(permits, true) {

    fun tryAcquire(optMsg: String) {
        Log.d(TAG, "$optMsg is trying acquire, $this")
        if (!tryAcquire(2500L, TimeUnit.MILLISECONDS)) {
            throw RuntimeException("Time out waiting to $optMsg")
        }
        Log.d(TAG, "$optMsg has acquired lock, $this")
    }

    fun release(optMsg: String) {
        if (availablePermits() >= 1) {
            Log.d(TAG, optMsg + " return, availablePermits is " + availablePermits())
            return
        }
        release()
        Log.d(TAG, "$optMsg released")
    }

    companion object {
        private val TAG = "CameraLock"
    }
}