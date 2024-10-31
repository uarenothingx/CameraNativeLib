package com.sll.cameracore

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.ImageFormat
import android.hardware.HardwareBuffer
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCaptureSession.CaptureCallback
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureFailure
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.ImageReader
import android.os.Handler
import android.os.HandlerThread
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
    private val mCameraCallbackThread: HandlerThread = HandlerThread("camera-callback-thread")
        .apply {
            start()
        }
    private val mCameraCallbackHandler: Handler = Handler(mCameraCallbackThread.looper)
    private val mCameraCallbackExecutor = ExecutorCompat.create(mCameraCallbackHandler)
    private val mCameraOpenCloseLock = CameraLock(1)

    private val mCameraOperationThread: HandlerThread = HandlerThread("camera-operation-thread")
        .apply {
            start()
        }
    private val mCameraOperationHandler: Handler = Handler(mCameraOperationThread.looper)
    private val mCameraOperationExecutor = SerialExecutor(mCameraOperationHandler)

    private var mCameraDevice: CameraDevice? = null
    private var mCaptureSession: CameraCaptureSession? = null
    private var mCharacteristics: CameraCharacteristics? = null

    private val mImageThread: HandlerThread = HandlerThread("image-thread")
        .apply {
            start()
        }
    private val mImageHandler = Handler(mImageThread.looper)
    private var mImageReader: ImageReader? = null
    private val mImageLockObject = Object()
    private var mOutputConfiguration: OutputConfiguration? = null
    private val mImageByteListenerList = ArrayList<OnImageByteListener>()
    private val mIsNeedLogFirstFrame = AtomicBoolean(false)
    private var mPreviewSize = Size(1280, 720)

    private val mPreviewImageAvailableListener = object : ImageReader.OnImageAvailableListener {

        override fun onImageAvailable(reader: ImageReader) {
            synchronized(mImageLockObject) {
                val image = reader.acquireLatestImage() ?: return
                if (image.format != IMAGE_FORMAT) return
                if (mIsNeedLogFirstFrame.compareAndSet(true, false)) {
                    LogUtil.d(TAG, "first frame come")
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
    }

    private val mSessionCallback = object : CameraCaptureSession.StateCallback() {

        override fun onConfigured(session: CameraCaptureSession) {
            LogUtil.d(TAG, "onConfigured: $session")
            mCaptureSession = session
            configPreviewRequest()
        }

        override fun onConfigureFailed(session: CameraCaptureSession) {
            LogUtil.d(TAG, "onConfigureFailed: $session")
        }

        override fun onClosed(session: CameraCaptureSession) {
            super.onClosed(session)
            LogUtil.d(TAG, "session $session has closed")
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
        mCameraOperationExecutor.execute {
            try {
                val id = cameraId.toString()
                LogUtil.d(TAG, "open camera [${id}]")
                mCameraOpenCloseLock.tryAcquire("openCamera")
                mCameraManager.openCamera(id, mCameraStateCallback, mCameraCallbackHandler)
            } catch (e: Exception) {
                e.printStackTrace()
                mCameraOpenCloseLock.release("openCamera in exception")
                LogUtil.d(TAG, "open camera failed")
            }
        }
    }

    fun closeCamera() {
        mCameraOperationExecutor.clear()
        mCameraOperationExecutor.execute {
            try {
                mCameraOpenCloseLock.tryAcquire("close camera")
                synchronized(mImageLockObject) {
                    logClose(mImageReader, tag = "imagereader")
                    mImageReader = null
                }
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
        mCameraOperationExecutor.execute {
            LogUtil.d(TAG, "startPreview: size = $size")
            if (mCameraDevice == null) {
                LogUtil.d(TAG, "startPreview: camera device is null, maybe already closed")
                return@execute
            }
            val outputs = ArrayList<OutputConfiguration>()

            mImageReader = newImageReader(size)
            mImageReader?.setOnImageAvailableListener(mPreviewImageAvailableListener, mImageHandler)
            mOutputConfiguration = OutputConfiguration(mImageReader!!.surface)
            outputs.add(mOutputConfiguration!!)

            val sessionConfig = SessionConfiguration(
                SessionConfiguration.SESSION_REGULAR,
                outputs,
                mCameraCallbackExecutor,
                mSessionCallback
            )
            LogUtil.d(TAG, "createCaptureSession")
            try {
                mCameraDevice?.createCaptureSession(sessionConfig)
            } catch (e: Exception) {
                e.printStackTrace()
                LogUtil.d(TAG, "startPreview: config streams error")
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
                    LogUtil.d(TAG, "buildPreviewRequest: camera device is null")
                    return
                }
            request.addTarget(mImageReader!!.surface)
            mIsNeedLogFirstFrame.compareAndSet(false, true)
            mCaptureSession?.setRepeatingRequest(
                request.build(),
                mPreviewCaptureCallback,
                mCameraCallbackHandler
            )
        } catch (e: Exception) {
            e.printStackTrace()
            LogUtil.d(TAG, "configPreviewRequest: config streams error")
        }
    }

    private val mPreviewCaptureCallback = object : CaptureCallback() {

        override fun onCaptureFailed(
            session: CameraCaptureSession,
            request: CaptureRequest,
            failure: CaptureFailure
        ) {
            super.onCaptureFailed(session, request, failure)
            LogUtil.d(TAG, "onCaptureFailed: ")
        }

        override fun onCaptureBufferLost(
            session: CameraCaptureSession,
            request: CaptureRequest,
            target: Surface,
            frameNumber: Long
        ) {
            super.onCaptureBufferLost(session, request, target, frameNumber)
            LogUtil.d(TAG, "onCaptureBufferLost: ")
        }
    }

    private fun logClose(closeable: AutoCloseable?, tag: String = closeable?.toString() ?: "") {
        try {
            if (closeable != null) {
                LogUtil.d(TAG, "$tag close start")
                closeable.close()
                LogUtil.d(TAG, "$tag close complete")
            } else {
                LogUtil.e(TAG, "$tag is null, not exec close")
            }
        } catch (e: Exception) {
            LogUtil.d(TAG, "$tag close error, ${e.message}")
            e.printStackTrace()
        }
    }

    companion object {
        private const val TAG = "CameraContext"
        private const val IMAGE_BUFFER_SIZE: Int = 3
        private const val IMAGE_FORMAT = ImageFormat.PRIVATE
    }
}