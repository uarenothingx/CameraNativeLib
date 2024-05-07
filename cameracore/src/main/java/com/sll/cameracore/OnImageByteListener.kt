package com.sll.cameracore

import android.media.Image

/**
 * @author Shenlinliang
 * @date 2022/11/30
 */
interface OnImageByteListener {
    fun onImageAvailable(image: Image)
}