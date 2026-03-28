package com.yves.animereader

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView

class OverlayViewManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    
    // UI Elements
    private var floatingBubble: FrameLayout? = null
    private var borderView: FrameLayout? = null
    private var handleView: FrameLayout? = null
    
    var isRectangleVisible = false
        private set

    // Variables pour stocker la position exacte du rectangle
    private var rectX = 200
    private var rectY = 500
    private var rectWidth = 600
    private var rectHeight = 300

    private val handler = Handler(Looper.getMainLooper())
    private val fadeOutRunnable = Runnable {
        borderView?.animate()?.alpha(0.15f)?.setDuration(500)?.start()
        handleView?.animate()?.alpha(0.15f)?.setDuration(500)?.start()
    }

    // --- NOUVEAU : Méthode pour récupérer la zone de lecture ---
    fun getReadingArea(): Rect? {
        if (!isRectangleVisible) return null
        return Rect(rectX, rectY, rectX + rectWidth, rectY + rectHeight)
    }

    fun wakeUpOverlay() {
        borderView?.animate()?.alpha(1.0f)?.setDuration(200)?.start()
        handleView?.animate()?.alpha(1.0f)?.setDuration(200)?.start()
        handler.removeCallbacks(fadeOutRunnable)
        handler.postDelayed(fadeOutRunnable, 2000)
    }

    // ... [Le reste du code reste identique pour showFloatingBubble] ...
    @SuppressLint("ClickableViewAccessibility")
    fun showFloatingBubble() {
        if (floatingBubble != null) return

        floatingBubble = FrameLayout(context).apply {
            val icon = TextView(context).apply {
                text = "👁️"
                textSize = 24f
                gravity = Gravity.CENTER
                val shape = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#EE2196F3"))
                }
                background = shape
                setPadding(20, 20, 20, 20)
            }
            addView(icon)
        }

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            getOverlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 300
        }

        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f
        var isMoved = false

        floatingBubble?.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    isMoved = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    if (Math.abs(dx) > 10 || Math.abs(dy) > 10) isMoved = true
                    params.x = initialX + dx
                    params.y = initialY + dy
                    windowManager.updateViewLayout(floatingBubble, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!isMoved) toggleRectangle()
                    true
                }
                else -> false
            }
        }
        windowManager.addView(floatingBubble, params)
    }

    private fun toggleRectangle() {
        if (isRectangleVisible) {
            borderView?.let { windowManager.removeView(it) }
            handleView?.let { windowManager.removeView(it) }
            borderView = null
            handleView = null
            isRectangleVisible = false
            handler.removeCallbacks(fadeOutRunnable)
        } else {
            showReadingRectangle()
            isRectangleVisible = true
            wakeUpOverlay()
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun showReadingRectangle() {
        borderView = FrameLayout(context).apply {
            val shape = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                setColor(Color.TRANSPARENT)
                setStroke(8, Color.parseColor("#00FF00"))
                cornerRadius = 16f
            }
            background = shape
        }

        val borderParams = WindowManager.LayoutParams(
            rectWidth, rectHeight,
            getOverlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = rectX
            y = rectY
        }

        windowManager.addView(borderView, borderParams)

        handleView = FrameLayout(context).apply {
            val icon = TextView(context).apply {
                text = "✥"
                textSize = 18f
                setTextColor(Color.WHITE)
                gravity = Gravity.CENTER
                val shape = GradientDrawable().apply {
                    shape = GradientDrawable.OVAL
                    setColor(Color.parseColor("#99000000"))
                }
                background = shape
                setPadding(15, 15, 15, 15)
            }
            addView(icon)
        }

        val handleParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            getOverlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = borderParams.x - 20
            y = borderParams.y - 20
        }

        var initialBorderX = 0
        var initialBorderY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        handleView?.setOnTouchListener { _, event ->
            wakeUpOverlay()
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialBorderX = borderParams.x
                    initialBorderY = borderParams.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()
                    
                    rectX = initialBorderX + dx
                    rectY = initialBorderY + dy
                    
                    borderParams.x = rectX
                    borderParams.y = rectY
                    windowManager.updateViewLayout(borderView, borderParams)
                    
                    handleParams.x = borderParams.x - 20
                    handleParams.y = borderParams.y - 20
                    windowManager.updateViewLayout(handleView, handleParams)
                    true
                }
                else -> false
            }
        }
        windowManager.addView(handleView, handleParams)
    }

    fun removeAllViews() {
        handler.removeCallbacks(fadeOutRunnable)
        floatingBubble?.let { try { windowManager.removeView(it) } catch(e: Exception) {} }
        borderView?.let { try { windowManager.removeView(it) } catch(e: Exception) {} }
        handleView?.let { try { windowManager.removeView(it) } catch(e: Exception) {} }
        floatingBubble = null
        borderView = null
        handleView = null
    }

    private fun getOverlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
        else 
            WindowManager.LayoutParams.TYPE_PHONE
    }
}