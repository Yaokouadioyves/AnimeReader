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
import kotlin.math.max

class OverlayViewManager(private val context: Context) {

    private val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
    
    // UI Elements
    private var floatingBubble: FrameLayout? = null
    private var borderView: FrameLayout? = null
    private var handleMoveView: FrameLayout? = null
    private var handleResizeView: FrameLayout? = null
    
    var isRectangleVisible = false
        private set

    // Variables pour stocker la position exacte du rectangle
    private var rectX = 100
    private var rectY = 400
    private var rectWidth = 600
    private var rectHeight = 300

    // Constantes de taille minimum pour le rectangle
    private val MIN_WIDTH = 200
    private val MIN_HEIGHT = 150

    private val handler = Handler(Looper.getMainLooper())
    private val fadeOutRunnable = Runnable {
        borderView?.animate()?.alpha(0.15f)?.setDuration(500)?.start()
        handleMoveView?.animate()?.alpha(0.15f)?.setDuration(500)?.start()
        handleResizeView?.animate()?.alpha(0.15f)?.setDuration(500)?.start()
    }

    fun getReadingArea(): Rect? {
        if (!isRectangleVisible) return null
        return Rect(rectX, rectY, rectX + rectWidth, rectY + rectHeight)
    }

    fun wakeUpOverlay() {
        borderView?.animate()?.alpha(1.0f)?.setDuration(200)?.start()
        handleMoveView?.animate()?.alpha(1.0f)?.setDuration(200)?.start()
        handleResizeView?.animate()?.alpha(1.0f)?.setDuration(200)?.start()
        handler.removeCallbacks(fadeOutRunnable)
        handler.postDelayed(fadeOutRunnable, 2000)
    }

    @SuppressLint("ClickableViewAccessibility")
    fun showFloatingBubble() {
        if (floatingBubble != null) return

        floatingBubble = FrameLayout(context).apply {
            val icon = TextView(context).apply {
                text = "▶️"
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
            handleMoveView?.let { windowManager.removeView(it) }
            handleResizeView?.let { windowManager.removeView(it) }
            borderView = null
            handleMoveView = null
            handleResizeView = null
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
        // 1. Le Rectangle (Bordure)
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

        // 2. La Poignée de Déplacement (Haut-Gauche)
        handleMoveView = FrameLayout(context).apply {
            val icon = TextView(context).apply {
                text = "✢" // Croix pour bouger
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

        val handleMoveParams = WindowManager.LayoutParams(
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

        handleMoveView?.setOnTouchListener { _, event ->
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
                    
                    handleMoveParams.x = borderParams.x - 20
                    handleMoveParams.y = borderParams.y - 20
                    windowManager.updateViewLayout(handleMoveView, handleMoveParams)
                    
                    handleResizeView?.let {
                        val resizeParams = it.layoutParams as WindowManager.LayoutParams
                        resizeParams.x = borderParams.x + rectWidth - 30
                        resizeParams.y = borderParams.y + rectHeight - 30
                        windowManager.updateViewLayout(it, resizeParams)
                    }
                    true
                }
                else -> false
            }
        }
        windowManager.addView(handleMoveView, handleMoveParams)

        // 3. La Poignée de Redimensionnement (Bas-Droite)
        handleResizeView = FrameLayout(context).apply {
            val icon = TextView(context).apply {
                text = "↘" // Flèche de redimensionnement
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

        val handleResizeParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            getOverlayType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = borderParams.x + rectWidth - 30
            y = borderParams.y + rectHeight - 30
        }

        var initialWidth = 0
        var initialHeight = 0
        var resizeInitialTouchX = 0f
        var resizeInitialTouchY = 0f

        handleResizeView?.setOnTouchListener { _, event ->
            wakeUpOverlay()
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialWidth = rectWidth
                    initialHeight = rectHeight
                    resizeInitialTouchX = event.rawX
                    resizeInitialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - resizeInitialTouchX).toInt()
                    val dy = (event.rawY - resizeInitialTouchY).toInt()
                    
                    rectWidth = max(MIN_WIDTH, initialWidth + dx)
                    rectHeight = max(MIN_HEIGHT, initialHeight + dy)
                    
                    borderParams.width = rectWidth
                    borderParams.height = rectHeight
                    windowManager.updateViewLayout(borderView, borderParams)
                    
                    handleResizeParams.x = borderParams.x + rectWidth - 30
                    handleResizeParams.y = borderParams.y + rectHeight - 30
                    windowManager.updateViewLayout(handleResizeView, handleResizeParams)
                    true
                }
                else -> false
            }
        }
        windowManager.addView(handleResizeView, handleResizeParams)
    }

    fun removeAllViews() {
        handler.removeCallbacks(fadeOutRunnable)
        floatingBubble?.let { try { windowManager.removeView(it) } catch(e: Exception) {} }
        borderView?.let { try { windowManager.removeView(it) } catch(e: Exception) {} }
        handleMoveView?.let { try { windowManager.removeView(it) } catch(e: Exception) {} }
        handleResizeView?.let { try { windowManager.removeView(it) } catch(e: Exception) {} }
        floatingBubble = null
        borderView = null
        handleMoveView = null
        handleResizeView = null
    }

    private fun getOverlayType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) 
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY 
        else 
            WindowManager.LayoutParams.TYPE_PHONE
    }
}