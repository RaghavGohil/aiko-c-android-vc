package com.deeplearningtitans.aikocompanionvc

import android.app.Service
import android.content.Intent
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.view.GestureDetector
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageView
import android.widget.TextView
import kotlin.math.abs
import kotlin.math.max
import kotlin.random.Random
import androidx.core.graphics.drawable.toDrawable


class FloatingTamagotchiService : Service() {
    private var floatingView: View? = null
    private lateinit var windowManager: WindowManager
    private lateinit var layoutParams: WindowManager.LayoutParams
    private var gestureDetector: GestureDetector? = null
    private lateinit var tamagotchiImage: ImageView
    private lateinit var messageText: TextView
    private val handler = Handler()
    private val random = Random(System.currentTimeMillis())
    private var isDragging = false // Track if user is moving Aiko

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        addFloatingTamagotchi()
        startRoaming()
    }

    private fun addFloatingTamagotchi() {
        val layoutInflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        floatingView = layoutInflater.inflate(R.layout.floating_tamagotchi, null)

        tamagotchiImage = floatingView!!.findViewById(R.id.tamagotchi_sprite)
        messageText = floatingView!!.findViewById(R.id.messageText)

        // Disable smoothing for pixel art
//        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.tamagotchi)
//        tamagotchiImage.setImageBitmap(bitmap)

//        val bitmap = BitmapFactory.decodeResource(
//            resources, R.drawable.tamagotchi
//        )
//        val drawable = bitmap.toDrawable(resources)
//        drawable.setFilterBitmap(false)
//        tamagotchiImage.setImageDrawable(drawable)

        val options = BitmapFactory.Options().apply {
            inScaled = false // Prevent automatic scaling
        }

        val bitmap = BitmapFactory.decodeResource(resources, R.drawable.tamagotchi, options)

// Create a BitmapDrawable and explicitly disable filtering
        val drawable = bitmap.toDrawable(resources).apply {
            paint.isFilterBitmap = false  // Turns off anti-aliasing
            paint.isDither = false        // Avoids dithering (color blending)
        }

        tamagotchiImage.setImageDrawable(drawable)
        tamagotchiImage.setLayerType(View.LAYER_TYPE_SOFTWARE, null) // Ensures no hardware smoothing


        layoutParams = WindowManager.LayoutParams(
            150, 150,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )

        layoutParams.gravity = Gravity.TOP or Gravity.START
        layoutParams.x = 300
        layoutParams.y = 500

        // Initialize Gesture Detector
        gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDoubleTap(e: MotionEvent): Boolean {
                openMainActivity()
                return true
            }
        })

        // Handle Touch and Dragging
        floatingView?.setOnTouchListener(object : View.OnTouchListener {
            private var initialX = 0
            private var initialY = 0
            private var initialTouchX = 0f
            private var initialTouchY = 0f

            override fun onTouch(v: View?, event: MotionEvent): Boolean {
                gestureDetector?.onTouchEvent(event)

                when (event.action) {
                    MotionEvent.ACTION_DOWN -> {
                        isDragging = true // Stop automatic movement
                        initialX = layoutParams.x
                        initialY = layoutParams.y
                        initialTouchX = event.rawX
                        initialTouchY = event.rawY
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        layoutParams.x = initialX + (event.rawX - initialTouchX).toInt()
                        layoutParams.y = initialY + (event.rawY - initialTouchY).toInt()
                        windowManager.updateViewLayout(floatingView, layoutParams)
                        return true
                    }

                    MotionEvent.ACTION_UP -> {
                        isDragging = false // Resume movement after user releases
                        return true
                    }
                }
                return false
            }
        })

        floatingView?.let { windowManager.addView(it, layoutParams) }
    }

    private fun startRoaming() {
        handler.postDelayed(object : Runnable {
            override fun run() {
                if (!isDragging) { // Move only if not being dragged
                    moveAikoToRandomPosition()
                }
                handler.postDelayed(this, random.nextLong(5000, 10000))
            }
        }, 5000)
    }

    private fun moveAikoToRandomPosition() {
        val targetX = random.nextInt(100, 800)
        val targetY = random.nextInt(100, 1500)
        animateMovement(targetX, targetY)

        // Occasionally show a message
        if (random.nextFloat() < 0.3) { // 30% chance
            messageText.visibility = View.VISIBLE
            messageText.text = getRandomMessage()
            handler.postDelayed({ messageText.visibility = View.GONE }, 2000)
        }
    }

    private fun animateMovement(targetX: Int, targetY: Int) {
        val duration = random.nextLong(2000, 5000) // Move over 2-5 seconds
        val startX = layoutParams.x
        val startY = layoutParams.y
        val deltaX = targetX - startX
        val deltaY = targetY - startY
        val steps = max(abs(deltaX), abs(deltaY)) / 5 // Number of frames
        val stepTime = duration / steps

        var step = 0
        val moveHandler = Handler()

        val moveRunnable = object : Runnable {
            override fun run() {
                if (step < steps && !isDragging) { // Stop if dragging
                    layoutParams.x = startX + (deltaX * step / steps)
                    layoutParams.y = startY + (deltaY * step / steps)
                    windowManager.updateViewLayout(floatingView, layoutParams)
                    step++
                    moveHandler.postDelayed(this, stepTime)
                }
            }
        }
        moveHandler.post(moveRunnable)
    }

    private fun getRandomMessage(): String {
        val messages = listOf("Feed me!", "I'm bored!", "Hello!", "Play with me!", "Stop doomscrolling!")
        return messages.random()
    }

    private fun openMainActivity() {
        val intent = Intent(this, MainActivity::class.java)
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        startActivity(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        floatingView?.let { windowManager.removeView(it) }
        handler.removeCallbacksAndMessages(null)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
