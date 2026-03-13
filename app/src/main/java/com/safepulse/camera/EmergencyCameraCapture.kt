package com.safepulse.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * Emergency camera capture for taking silent front camera photos
 * Used for triple volume button emergency feature
 */
class EmergencyCameraCapture(private val context: Context) {
    
    companion object {
        private const val TAG = "EmergencyCameraCapture"
        private const val MAX_IMAGE_SIZE_KB = 500 // For MMS compatibility
    }
    
    private var imageCapture: ImageCapture? = null
    private var cameraProvider: ProcessCameraProvider? = null
    
    /**
     * Capture photo from front camera silently (no preview, no sound)
     * Returns the file path of the captured image
     */
    suspend fun captureEmergencyPhoto(lifecycleOwner: LifecycleOwner): File? {
        return try {
            Log.i(TAG, "📸 Starting emergency photo capture...")
            
            val provider = getCameraProvider()
            
            // Camera operations MUST run on main thread
            val cameraBound = withContext(Dispatchers.Main) {
                cameraProvider = provider
                
                // Setup image capture
                imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(android.view.Surface.ROTATION_0)
                    .build()
                
                // Bind to lifecycle with front camera
                val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
                
                try {
                    provider.unbindAll()
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        imageCapture
                    )
                    
                    Log.d(TAG, "✅ Camera bound successfully")
                    true
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Failed to bind camera", e)
                    false
                }
            }

            if (!cameraBound) {
                return null
            }
            
            // Small delay to ensure camera is fully initialized
            kotlinx.coroutines.delay(100)
            
            // Create output file
            val photoFile = createPhotoFile()
            val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()
            
            // Capture photo (can be on background thread)
            val result = captureImage(outputOptions)
            
            // Unbind camera on main thread
            withContext(Dispatchers.Main) {
                cameraProvider?.unbindAll()
                cameraProvider = null
                imageCapture = null
            }
            
            if (result) {
                Log.i(TAG, "✅ Photo captured successfully: ${photoFile.absolutePath}")
                Log.i(TAG, "   File size: ${photoFile.length() / 1024}KB")
                
                // Compress if needed
                val compressedFile = compressIfNeeded(photoFile)
                Log.i(TAG, "   Compressed size: ${compressedFile.length() / 1024}KB")
                
                compressedFile
            } else {
                Log.e(TAG, "❌ Photo capture failed")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ Emergency photo capture error", e)
            null
        }
    }
    
    /**
     * Get camera provider
     */
    private suspend fun getCameraProvider(): ProcessCameraProvider {
        return suspendCancellableCoroutine { continuation ->
            val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
            cameraProviderFuture.addListener({
                try {
                    continuation.resume(cameraProviderFuture.get())
                } catch (e: Exception) {
                    continuation.resumeWithException(e)
                }
            }, ContextCompat.getMainExecutor(context))
        }
    }
    
    /**
     * Capture image and wait for result
     */
    private suspend fun captureImage(outputOptions: ImageCapture.OutputFileOptions): Boolean {
        return suspendCancellableCoroutine { continuation ->
            imageCapture?.takePicture(
                outputOptions,
                ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        continuation.resume(true)
                    }
                    
                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Image capture error: ${exception.message}", exception)
                        continuation.resume(false)
                    }
                }
            )
        }
    }
    
    /**
     * Create photo file in cache directory
     */
    private fun createPhotoFile(): File {
        val timestamp = System.currentTimeMillis()
        val filename = "emergency_photo_$timestamp.jpg"
        return File(context.cacheDir, filename)
    }
    
    /**
     * Compress image if it exceeds MAX_IMAGE_SIZE_KB
     */
    private fun compressIfNeeded(photoFile: File): File {
        val fileSizeKB = photoFile.length() / 1024
        
        if (fileSizeKB <= MAX_IMAGE_SIZE_KB) {
            Log.d(TAG, "Photo size OK, no compression needed")
            return photoFile
        }
        
        Log.i(TAG, "Compressing photo from ${fileSizeKB}KB to <${MAX_IMAGE_SIZE_KB}KB...")
        
        try {
            // Load bitmap
            val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
            
            // Calculate compression quality needed
            var quality = 85
            var compressedFile: File
            
            do {
                compressedFile = File(context.cacheDir, "emergency_photo_compressed_${System.currentTimeMillis()}.jpg")
                FileOutputStream(compressedFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, out)
                }
                
                val newSizeKB = compressedFile.length() / 1024
                Log.d(TAG, "Compression quality $quality% -> ${newSizeKB}KB")
                
                if (newSizeKB <= MAX_IMAGE_SIZE_KB) {
                    // Delete original
                    photoFile.delete()
                    bitmap.recycle()
                    return compressedFile
                }
                
                quality -= 10
                compressedFile.delete()
                
            } while (quality >= 20)
            
            // If still too large, resize
            val scaledBitmap = Bitmap.createScaledBitmap(
                bitmap, 
                bitmap.width / 2, 
                bitmap.height / 2, 
                true
            )
            bitmap.recycle()
            
            compressedFile = File(context.cacheDir, "emergency_photo_scaled_${System.currentTimeMillis()}.jpg")
            FileOutputStream(compressedFile).use { out ->
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 70, out)
            }
            scaledBitmap.recycle()
            
            photoFile.delete()
            return compressedFile
            
        } catch (e: Exception) {
            Log.e(TAG, "Compression failed, using original", e)
            return photoFile
        }
    }
    
    /**
     * Cleanup captured photos
     */
    fun cleanup() {
        try {
            cameraProvider?.unbindAll()
            cameraProvider = null
            imageCapture = null
            
            // Delete old emergency photos from cache
            context.cacheDir.listFiles()?.forEach { file ->
                if (file.name.startsWith("emergency_photo_")) {
                    val age = System.currentTimeMillis() - file.lastModified()
                    if (age > 24 * 60 * 60 * 1000) { // Older than 24 hours
                        file.delete()
                        Log.d(TAG, "Deleted old photo: ${file.name}")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Cleanup error", e)
        }
    }
}
