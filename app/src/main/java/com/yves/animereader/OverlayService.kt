package com.yves.animereader

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.DisplayMetrics
import android.util.Log
import android.view.WindowManager
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class OverlayService : Service() {
    
    private var overlayManager: OverlayViewManager? = null
    private lateinit var ttsManager: TtsManager
    
    // Composants pour la capture d'écran
    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    
    // OCR Google ML Kit
    private val textRecognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    
    // Pour ne pas lire deux fois le même texte (Le système "Anti-Spam")
    private var lastReadText = ""
    private val captureHandler = Handler(Looper.getMainLooper())
    private var isProcessingImage = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        
        createNotificationChannel()
        startForeground(1, createNotification())
        
        // Initialiser notre TtsManager (La Voix Orus)
        ttsManager = TtsManager(this)
        
        overlayManager = OverlayViewManager(this)
        overlayManager?.showFloatingBubble()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val resultCode = intent?.getIntExtra("RESULT_CODE", 0) ?: 0
        val data = intent?.getParcelableExtra<Intent>("DATA")
        
        if (resultCode != 0 && data != null && mediaProjection == null) {
            val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, data)
            
            // Démarrer la boucle de capture d'écran
            setupScreenCapture()
            startScreenCaptureLoop()
            
            Toast.makeText(this, "AnimeReader : L'Oeil est ouvert, pret a lire !", Toast.LENGTH_SHORT).show()
        }
        
        return START_NOT_STICKY
    }

    private fun setupScreenCapture() {
        val windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getRealMetrics(metrics)
        
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi

        imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        
        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "AnimeReaderScreenCapture",
            width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            imageReader?.surface, null, null
        )
    }

    private fun startScreenCaptureLoop() {
        // On vérifie l'écran toutes les 1 seconde
        val captureRunnable = object : Runnable {
            override fun run() {
                processLatestScreenFrame()
                captureHandler.postDelayed(this, 1000)
            }
        }
        captureHandler.post(captureRunnable)
    }

    private fun processLatestScreenFrame() {
        if (isProcessingImage) return
        
        val readingArea = overlayManager?.getReadingArea()
        if (readingArea == null || !overlayManager!!.isRectangleVisible) return

        val image = imageReader?.acquireLatestImage() ?: return
        isProcessingImage = true

        try {
            val planes = image.planes
            val buffer = planes[0].buffer
            val pixelStride = planes[0].pixelStride
            val rowStride = planes[0].rowStride
            val rowPadding = rowStride - pixelStride * image.width

            val bitmap = Bitmap.createBitmap(
                image.width + rowPadding / pixelStride,
                image.height,
                Bitmap.Config.ARGB_8888
            )
            bitmap.copyPixelsFromBuffer(buffer)

            val cropX = Math.max(0, readingArea.left)
            val cropY = Math.max(0, readingArea.top)
            val cropW = Math.min(bitmap.width - cropX, readingArea.width())
            val cropH = Math.min(bitmap.height - cropY, readingArea.height())

            val croppedBitmap = Bitmap.createBitmap(bitmap, cropX, cropY, cropW, cropH)
            
            processTextFromImage(croppedBitmap)

        } catch (e: Exception) {
            Log.e("AnimeReader", "Erreur capture: ${e.message}")
            isProcessingImage = false
        } finally {
            image.close()
        }
    }

    private fun processTextFromImage(bitmap: Bitmap) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        
        textRecognizer.process(inputImage)
            .addOnSuccessListener { visionText ->
                val rawText = visionText.text
                if (rawText.isNotBlank()) {
                    val cleanedText = cleanOcrTextForAudio(rawText)
                    
                    if (cleanedText != lastReadText && cleanedText.length > 2) {
                        lastReadText = cleanedText
                        Log.i("AnimeReader", "NOUVEAU TEXTE DETECTE : $cleanedText")
                        
                        // LECTURE AUDIO INSTANTANEE !
                        ttsManager.speak(cleanedText)
                    }
                }
                isProcessingImage = false
            }
            .addOnFailureListener {
                isProcessingImage = false
            }
    }

    private fun cleanOcrTextForAudio(rawText: String): String {
        var text = rawText
        text = text.replace(Regex("-\\s*\\n\\s*"), "")
        text = text.replace(Regex("\\n"), " ")
        text = text.replace(Regex("~+"), "...")
        text = text.replace(Regex("[^\\p{L}\\p{N}\\p{P}\\s]"), "")
        text = text.replace(Regex("\\s{2,}"), " ")
        return text.trim()
    }

    override fun onDestroy() {
        super.onDestroy()
        captureHandler.removeCallbacksAndMessages(null)
        virtualDisplay?.release()
        imageReader?.close()
        mediaProjection?.stop()
        overlayManager?.removeAllViews()
        ttsManager.stop()
        Toast.makeText(this, "AnimeReader est arrete", Toast.LENGTH_SHORT).show()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel("animereader_channel", "AnimeReader Service", NotificationManager.IMPORTANCE_LOW)
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, "animereader_channel")
            .setContentTitle("AnimeReader")
            .setContentText("En attente de bulles de dialogue...")
            .setSmallIcon(android.R.drawable.ic_menu_camera)
            .build()
    }
}