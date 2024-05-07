package com.sll.cameracore

import android.hardware.HardwareBuffer
import android.media.Image
import java.nio.ByteBuffer

object NativeHelper {

    // Used to load the 'cameracore' library on application startup.
    init {
        System.loadLibrary("native-helper")
    }

    fun getYuvFromImage(image: Image, data: ByteArray) {
        try {
            val crop = image.cropRect
            val width = crop.width()
            val height = crop.height()
            val planes = image.planes
            val yBuffer = planes[0].buffer
            val uBuffer = planes[1].buffer
            val vBuffer = planes[2].buffer
            val yStride = planes[0].rowStride
            val uvPixelStride = planes[1].pixelStride
            formatNV21Data(yBuffer, uBuffer, vBuffer, width, height, yStride, uvPixelStride, data)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private external fun formatNV21Data(
        yBuffer: ByteBuffer, uBuffer: ByteBuffer, vBuffer: ByteBuffer,
        width: Int, height: Int, yStride: Int, uvPixelStride: Int, dst: ByteArray
    )

    external fun nativeGetBytesFromHardwareBuffer(
        buffer: HardwareBuffer,
        bytes: ByteArray
    )

}