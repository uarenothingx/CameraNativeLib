package com.sll.cameracore

import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit

/**
 * @author Shenlinliang
 * @date 2022/11/7
 */
class CameraLock(permits: Int) : Semaphore(permits, true) {

    fun tryAcquire(optMsg: String) {
        LogUtil.d(TAG, "$optMsg is trying acquire, $this")
        if (!tryAcquire(2500L, TimeUnit.MILLISECONDS)) {
            throw RuntimeException("Time out waiting to $optMsg")
        }
        LogUtil.d(TAG, "$optMsg has acquired lock, $this")
    }

    fun release(optMsg: String) {
        if (availablePermits() >= 1) {
            LogUtil.d(TAG, optMsg + " return, availablePermits is " + availablePermits())
            return
        }
        release()
        LogUtil.d(TAG, "$optMsg released")
    }

    companion object {
        private val TAG = "CameraLock"
    }
}