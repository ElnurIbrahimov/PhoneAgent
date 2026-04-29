package com.phoneagent.perception

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import androidx.activity.result.ActivityResultLauncher
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.DisplayMetrics
import android.view.WindowManager
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import java.io.ByteArrayOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

interface ScreenCaptureManager {
    fun isAvailable(): Boolean
    fun startCapture(launcher: ActivityResultLauncher<Intent>)
    fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?)
    fun stopCapture()
    suspend fun captureScreenshot(): ByteArray
    fun getLatestBitmap(): Bitmap?
    fun getLastCapture(): ByteArray?
}

class ScreenCaptureManagerImpl(private val context: Context) : ScreenCaptureManager {

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    @Volatile private var latestBitmap: Bitmap? = null
    private var initialized = false
    private var displayWidth = 0
    private var displayHeight = 0
    private var displayDensity = 0
    private val handler = Handler(Looper.getMainLooper())

    override fun isAvailable(): Boolean = initialized && mediaProjection != null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_CODE || resultCode != Activity.RESULT_OK || data == null) return

        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        releaseResources()
        mediaProjection = projectionManager.getMediaProjection(resultCode, data)

        val metrics = DisplayMetrics()
        val wm = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        @Suppress("DEPRECATION") wm.defaultDisplay.getRealMetrics(metrics)
        displayWidth = metrics.widthPixels
        displayHeight = metrics.heightPixels
        displayDensity = metrics.densityDpi

        initialized = true
    }

    override fun startCapture(launcher: ActivityResultLauncher<Intent>) {
        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        launcher.launch(projectionManager.createScreenCaptureIntent())
    }

    override fun stopCapture() {
        releaseResources()
        mediaProjection?.stop()
        mediaProjection = null
        initialized = false
        latestBitmap?.recycle()
        latestBitmap = null
    }

    override suspend fun captureScreenshot(): ByteArray = withTimeout(15_000) {
        if (!initialized || mediaProjection == null) {
            throw IllegalStateException("Screen capture not initialized. Grant permission first.")
        }

        releaseVirtualDisplayAndReader()

        imageReader = ImageReader.newInstance(displayWidth, displayHeight, PixelFormat.RGBA_8888, 2)
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "PhoneAgentScreenCapture",
            displayWidth, displayHeight, displayDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader!!.surface, null, handler
        )

        try {
            suspendCancellableCoroutine { continuation ->
                var imageAcquired = false
                imageReader?.setOnImageAvailableListener({ reader ->
                    if (imageAcquired) return@setOnImageAvailableListener
                    val image = reader.acquireLatestImage()
                    if (image != null) {
                        imageAcquired = true
                        try {
                            val planes = image.planes
                            val buffer = planes[0].buffer
                            val pixelStride = planes[0].pixelStride
                            val rowStride = planes[0].rowStride
                            val rowPadding = rowStride - pixelStride * displayWidth
                            val bitmapPadding = if (pixelStride > 0) rowPadding / pixelStride else 0
                            val bitmap = Bitmap.createBitmap(displayWidth + bitmapPadding, displayHeight, Bitmap.Config.ARGB_8888)
                            bitmap.copyPixelsFromBuffer(buffer)
                            val cropped = Bitmap.createBitmap(bitmap, 0, 0, displayWidth, displayHeight)
                            synchronized(this) {
                                latestBitmap = cropped
                            }
                            bitmap.recycle()

                            val baos = ByteArrayOutputStream()
                            cropped.compress(Bitmap.CompressFormat.JPEG, 80, baos)
                            val bytes = baos.toByteArray()
                            baos.close()

                            continuation.resume(bytes)
                        } catch (e: Exception) {
                            continuation.resumeWithException(e)
                        } finally {
                            image.close()
                        }
                    }
                }, handler)
            }
        } finally {
            releaseVirtualDisplayAndReader()
        }
    }

    @Synchronized
    override fun getLatestBitmap(): Bitmap? {
        val bitmap = latestBitmap
        if (bitmap?.isRecycled == true) return null
        return bitmap
    }

    @Synchronized
    override fun getLastCapture(): ByteArray? {
        val bitmap = latestBitmap
        if (bitmap == null || bitmap.isRecycled) return null
        val baos = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos)
        val bytes = baos.toByteArray()
        baos.close()
        return bytes
    }

    private fun releaseVirtualDisplayAndReader() {
        virtualDisplay?.release()
        virtualDisplay = null
        imageReader?.close()
        imageReader = null
    }

    private fun releaseResources() {
        releaseVirtualDisplayAndReader()
    }

    companion object {
        const val REQUEST_CODE = 9001
    }
}
