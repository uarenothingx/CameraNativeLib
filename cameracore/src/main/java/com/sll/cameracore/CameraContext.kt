package com.sll.cameracore

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.HardwareBuffer
import android.hardware.camera2.*
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import androidx.core.os.ExecutorCompat
import java.util.concurrent.atomic.AtomicBoolean

/**
 * @author Shenlinliang
 * @date 2022/11/7
 */
@SuppressLint("MissingPermission")
class CameraContext {
    private val applicationContext = ContextProvider.applicationContext
    private val mCameraManager =
        applicationContext.getSystemService(Context.CAMERA_SERVICE) as CameraManager
    private val mCameraThread: HandlerThread = HandlerThread("camera-thread")
        .apply {
            start()
        }
    private val mCameraHandler: Handler = Handler(mCameraThread.looper)
    private val mCameraExecutor = ExecutorCompat.create(mCameraHandler)
    private val mCameraOpenCloseLock = CameraLock(1)

    private var mCameraDevice: CameraDevice? = null
    private var mCaptureSession: CameraCaptureSession? = null
    private var mCharacteristics: CameraCharacteristics? = null

    private val mImageThread: HandlerThread = HandlerThread("image-thread")
        .apply {
            start()
        }
    private val mImageHandler = Handler(mImageThread.looper)
    private var mImageReader: ImageReader? = null
    private var mOutputConfiguration: OutputConfiguration? = null
    private val mImageByteListenerList = ArrayList<OnImageByteListener>()
    private val mIsNeedLogFirstFrame = AtomicBoolean(false)
    private var mPreviewSize = Size(1280, 720)

    private val mPreviewImageAvailableListener = object : ImageReader.OnImageAvailableListener {

        override fun onImageAvailable(reader: ImageReader) {
            val image = reader.acquireLatestImage() ?: return
            if (image.format != IMAGE_FORMAT) return
            if (mIsNeedLogFirstFrame.compareAndSet(true, false)) {
                Log.d(TAG, "first frame come")
            }
            mImageByteListenerList.forEach {
                it.onImageAvailable(image)
            }
            try {
                image.close()
            } catch (ignore: Exception) {
            }
        }
    }

    private val mSessionCallback = object : CameraCaptureSession.StateCallback() {

        override fun onConfigured(session: CameraCaptureSession) {
            Log.d(TAG, "onConfigured: $session")
            mCaptureSession = session
            configPreviewRequest()
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            Log.d(TAG, "onConfigureFailed: $session")
        }

        override fun onClosed(session: CameraCaptureSession) {
            super.onClosed(session)
            Log.d(TAG, "session $session has closed")
        }
    }

    private val mCameraStateCallback = object : CameraDevice.StateCallback() {

        override fun onOpened(camera: CameraDevice) {
            mCameraDevice = camera
            mCharacteristics = mCameraManager.getCameraCharacteristics(camera.id)
            mCameraOpenCloseLock.release("openCamera onOpened")
            calculatePreviewSize()
        }

        override fun onClosed(camera: CameraDevice) {
        }

        override fun onDisconnected(camera: CameraDevice) {
            if (mCameraOpenCloseLock.availablePermits() < 1) {
                mCameraOpenCloseLock.release("openCamera onDisconnected")
            }
        }

        override fun onError(camera: CameraDevice, error: Int) {
            if (mCameraOpenCloseLock.availablePermits() < 1) {
                mCameraOpenCloseLock.release("openCamera onError")
            }
        }
    }

    fun openCamera(cameraId: Int) {
        mCameraExecutor.execute {
            try {
                val id = cameraId.toString()
                Log.d(TAG, "open camera [${id}]")
                mCameraOpenCloseLock.tryAcquire("openCamera")
                mCameraManager.openCamera(id, mCameraStateCallback, null)
            } catch (e: Exception) {
                e.printStackTrace()
                mCameraOpenCloseLock.release("openCamera in exception")
                Log.d(TAG, "open camera failed")
            }
        }
    }

    fun closeCamera() {
        mCameraExecutor.execute {
            try {
                mCameraOpenCloseLock.tryAcquire("close camera")
                logClose(mImageReader, tag = "imagereader")
                mImageReader = null
                mCaptureSession?.stopRepeating()
                logClose(mCaptureSession, tag = "session")
                mCaptureSession = null
                logClose(mCameraDevice, tag = "device")
                mCameraDevice = null
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                mCameraOpenCloseLock.release("close camera")
            }
        }
    }

    fun addImageAvailableListener(listener: OnImageByteListener) {
        mImageHandler.post { mImageByteListenerList.add(listener) }
    }

    fun removeImageAvailableListener(listener: OnImageByteListener) {
        mImageHandler.post { mImageByteListenerList.remove(listener) }
    }

    fun setPreviewSize(size: Size) {
        mPreviewSize = size
    }

    private fun startPreview(size: Size) {
        mCameraExecutor.execute {
            Log.d(TAG, "startPreview: size = $size")
            val outputs = ArrayList<OutputConfiguration>()

            mImageReader = newImageReader(size)
            mImageReader?.setOnImageAvailableListener(mPreviewImageAvailableListener, mImageHandler)
            mOutputConfiguration = OutputConfiguration(mImageReader!!.surface)
            outputs.add(mOutputConfiguration!!)

            val sessionConfig = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputs,
                mCameraExecutor,
                mSessionCallback
            )
            Log.d(TAG, "createCaptureSession")
            try {
                mCameraDevice?.createCaptureSession(sessionConfig)
            } catch (e: Exception) {
                e.printStackTrace()
                Log.d(TAG, "startPreview: config streams error")
            }
        }
    }

    private fun calculatePreviewSize() {
        startPreview(mPreviewSize)
    }

    private fun newImageReader(size: Size): ImageReader {
        return ImageReader.newInstance(
            size.width, size.height, IMAGE_FORMAT, IMAGE_BUFFER_SIZE,
            if (IMAGE_FORMAT == ImageFormat.PRIVATE) {
                HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE
            } else {
                HardwareBuffer.USAGE_CPU_READ_OFTEN
            }
        ).also { mImageReader = it }
    }

    private fun configPreviewRequest() {
        try {
            val request =
                mCameraDevice?.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW) ?: kotlin.run {
                    Log.d(TAG, "buildPreviewRequest: camera device is null")
                    return
                }
            request.addTarget(mImageReader!!.surface)
            mIsNeedLogFirstFrame.compareAndSet(false, true)
            mCaptureSession?.setRepeatingRequest(
                request.build(),
                mPreviewCaptureCallback,
                mCameraHandler
            )
        } catch (e: Exception) {
            e.printStackTrace()
            Log.d(TAG, "configPreviewRequest: config streams error")
        }
    }

    private val mPreviewCaptureCallback = object : CaptureCallback() {

        override fun onCaptureFailed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            failure: CaptureFailure
        ) {
            super.onCaptureFailed(session, request, failure)
            Log.d(TAG, "onCaptureFailed: ")
        }

        override fun onCaptureBufferLost(
            session: CameraCaptureSession,
            request: CaptureRequest,
            target: Surface,
            frameNumber: Long
        ) {
            super.onCaptureBufferLost(session, request, target, frameNumber)
            Log.d(TAG, "onCaptureBufferLost: ")
        }
    }

    private fun logClose(closeable: AutoCloseable?, tag: String = closeable?.toString() ?: "") {
        try {
            if (closeable != null) {
                Log.d(TAG, "$tag close start")
                closeable.close()
                Log.d(TAG, "$tag close complete")
            } else {
                Log.e(TAG, "$tag is null, not exec close")
            }
        } catch (e: Exception) {
            Log.d(TAG, "$tag close error, ${e.message}")
            e.printStackTrace()
        }
    }

    companion object {
        private const val TAG = "CameraContext"
        private const val IMAGE_BUFFER_SIZE: Int = 3
        private const val IMAGE_FORMAT = ImageFormat.PRIVATE
    }
}