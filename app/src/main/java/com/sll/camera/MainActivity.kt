package com.sll.camera

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import android.os.Bundle
import android.util.Size
import android.widget.ImageView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.sll.cameracore.CameraContext
import com.sll.cameracore.NativeHelper
import com.sll.cameracore.OnImageByteListener
import java.io.ByteArrayOutputStream


class MainActivity : ComponentActivity() {

    private val mCameraContext = CameraContext()

    private lateinit var mIvPreview: ImageView

    private val mPreviewSizes = arrayOf(Size(2560, 1600))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        mIvPreview = findViewById(R.id.iv_image)

        mCameraContext.addImageAvailableListener(object : OnImageByteListener {
            private var imageBytes = ByteArray(1)
            private val outputStream = ByteArrayOutputStream()

            override fun onImageAvailable(image: Image) {

                val crop = image.cropRect
                val w = crop.width()
                val h = crop.height()
                val totalSize = w * h * 3 / 2

                if (imageBytes.size != totalSize) {
                    imageBytes = ByteArray(totalSize)
                }
                image.hardwareBuffer?.let {
                    NativeHelper.nativeGetBytesFromHardwareBuffer(it, imageBytes)
                    it.close()
                }

                outputStream.reset()
//                // nv21 -> jpeg
                val yuvImage = YuvImage(imageBytes, ImageFormat.NV21, w, h, intArrayOf(w, w, w))
                yuvImage.compressToJpeg(Rect(0, 0, w, h), 50, outputStream)

                val toByteArray = outputStream.toByteArray()
                // jpeg -> bitmap
                val bitmap = BitmapFactory.decodeByteArray(toByteArray, 0, toByteArray.size)

                runOnUiThread {
                    if (bitmap != null && !bitmap.isRecycled) {
                        // show bitmap
                        mIvPreview.setImageBitmap(bitmap)
                    }
                }
            }
        })

        if (!hasCameraPermission()) {
            registerForActivityResult(ActivityResultContracts.RequestPermission()) {
                if (it) openCamera()
            }
        }

    }

    override fun onResume() {
        super.onResume()
        openCamera()
    }

    override fun onPause() {
        super.onPause()
        mCameraContext.closeCamera()
    }

    private fun openCamera() {
        if (hasCameraPermission()) {
            mCameraContext.setPreviewSize(mPreviewSizes[0])
            mCameraContext.openCamera(0)
        }
    }

    private fun hasCameraPermission() = ContextCompat.checkSelfPermission(
        this, Manifest.permission.CAMERA
    ) == PackageManager.PERMISSION_GRANTED
}