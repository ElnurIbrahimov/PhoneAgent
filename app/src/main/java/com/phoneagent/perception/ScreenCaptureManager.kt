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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    @Volatile private var latestBitmap: Bitmap? = null
    private var initialized = false
    private var displayWidth = 0
    private var displayHeight = 0
    private var displayDensity = 0
    private val MAX_CAPTURE_DIM = 1080
    private val handler = Handler(Looper.getMainLooper())
    private val captureMutex = Mutex()

    override fun isAvailable(): Boolean = initialized && mediaProjection != null

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode != REQUEST_CODE || resultCode != Activity.RESULT_OK || data == null) return

        val projectionManager = context.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection?.stop()
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

        val mediaProj = mediaProjection ?: throw IllegalStateException("MediaProjection not available")

        captureMutex.withLock {
            val reader = ImageReader.newInstance(displayWidth, displayHeight, PixelFormat.RGBA_8888, 2)
            val display = mediaProj.createVirtualDisplay(
                "PhoneAgentScreenCapture",
                displayWidth, displayHeight, displayDensity,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                reader.surface, null, handler
            )

            try {
                suspendCancellableCoroutine { continuation ->
                    var imageAcquired = false
                    reader.setOnImageAvailableListener({ r ->
                        if (imageAcquired) return@setOnImageAvailableListener
                        val image = r.acquireLatestImage()
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
                                val scaled = scaleBitmap(cropped, MAX_CAPTURE_DIM)
                                synchronized(this) { latestBitmap = scaled }
                                bitmap.recycle()

                                val baos = ByteArrayOutputStream()
                                scaled.compress(Bitmap.CompressFormat.JPEG, 85, baos)
                                val bytes = baos.toByteArray()
                                baos.close()
                                cropped.recycle()
                                if (scaled !== cropped) scaled.recycle()

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
                display.release()
                reader.close()
            }
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

    companion object {
        const val REQUEST_CODE = 9001

        private fun scaleBitmap(src: Bitmap, maxDim: Int): Bitmap {
            if (src.width <= maxDim && src.height <= maxDim) return src
            val scale = minOf(maxDim.toFloat() / src.width, maxDim.toFloat() / src.height)
            val w = (src.width * scale).toInt()
            val h = (src.height * scale).toInt()
            return Bitmap.createScaledBitmap(src, w, h, true)
        }
    }
}