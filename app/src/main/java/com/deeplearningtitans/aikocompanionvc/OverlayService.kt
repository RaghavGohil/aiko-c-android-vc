package com.deeplearningtitans.aikocompanionvc

import android.animation.ValueAnimator
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper // Import Looper
import android.util.DisplayMetrics
import android.view.*
import android.view.animation.DecelerateInterpolator
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.animation.doOnEnd
import androidx.core.animation.doOnStart
import java.util.*
import kotlin.math.abs
import kotlin.math.roundToInt // Import roundToInt

class OverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var floatingView: View
    private lateinit var drawerView: View
    private lateinit var petImageView: ImageView // For the Tamagotchi image
    private lateinit var floatingParams: WindowManager.LayoutParams
    private lateinit var drawerParams: WindowManager.LayoutParams
    private lateinit var speechTextView: TextView // For the speech bubble

    // Tamagotchi state
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f
    private var isDragging = false
    private var isTamagotchiVisible = true // Track Tamagotchi visibility explicitly
    private var defaultX = 100
    private var defaultY = 100

    // Drawer state
    private var drawerWidth = 300 // Use DP or measure view for better results
    private var drawerPeekOffset = 50 // How much the drawer shows when minimized (peeking)
    private val drawerSide = "left" // Drawer comes from the left
    private var currentDrawerState = DrawerState.HIDDEN
    private var initialDrawerX = 0
    private var lastTouchXDrawer = 0f
    private var drawerAnimator: ValueAnimator? = null // To manage ongoing drawer animations

    // Swipe detection constants
    private val SWIPE_THRESHOLD_VELOCITY = 100 // pixels per second
    private val SWIPE_MIN_DISTANCE = 50 // pixels (adjust as needed)

    // Animation State
    private var isPetIdle = true
    private var currentFrameIndex = 0
    private val animationHandler = Handler(Looper.getMainLooper())
    private val frameUpdateDelay = 200L // Milliseconds between animation frames (adjust as needed)

    // --- Animation Frame Placeholders ---
    // Replace with your actual drawable resource IDs
    private val idleAnimationFrames = listOf(
        R.drawable.idle, // Replace with your actual idle frame 1 resource ID
    )
    private val walkingAnimationFrames = listOf(
        R.drawable.walk, // Replace with your actual walking frame 1 resource ID
        R.drawable.walk, // Replace with your actual walking frame 1 resource ID
    )
    // --- End Animation Frame Placeholders ---

    // Enum for Drawer States
    private enum class DrawerState {
        HIDDEN, // Completely off-screen
        PEEKING, // Partially visible (minimized)
        OPEN // Fully visible (maximized)
    }

    // Handler on the main thread
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        // Ensure dimensions are calculated after views are potentially measured
        // It's better to get screen dimensions here or measure views post-layout
        // For simplicity, we'll use fixed values for now, but convert DP later
        drawerWidth = dpToPx(70) // Adjusted drawer width
        drawerPeekOffset = dpToPx(60) // Adjusted peek offset

        addFloatingTamagotchi()
        addDrawer()
        simulateTamagotchiBehavior() // Start behavior simulation
        startAnimationLoop() // Start animation loop
    }

    // --- Tamagotchi Methods ---

    private fun addFloatingTamagotchi() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        floatingView = inflater.inflate(R.layout.floating_view, null) // Ensure this layout exists

        // Find views within the floating view layout
        petImageView = floatingView.findViewById(R.id.tamagotchiImage) // Ensure this ID exists in floating_view.xml
        speechTextView = floatingView.findViewById(R.id.speechTextView) // Ensure this ID exists in floating_view.xml

        floatingParams = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, // Allow moving outside bounds temporarily
            PixelFormat.TRANSLUCENT
        )

        floatingParams.gravity = Gravity.TOP or Gravity.START
        floatingParams.x = defaultX
        floatingParams.y = defaultY

        floatingView.setOnTouchListener { _, event ->
            handleTamagotchiTouch(event)
            true // Consume touch event
        }

        floatingView.visibility = View.VISIBLE // Start visible
        isTamagotchiVisible = true
        windowManager.addView(floatingView, floatingParams)
        setPetAnimationState(true) // Start with idle animation
    }

    private fun handleTamagotchiTouch(event: MotionEvent) {
        val screenWidth = getScreenWidth()
        val currentX = floatingParams.x
        val currentY = floatingParams.y

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                isDragging = true
                setPetAnimationState(false) // Switch to walking/dragged animation
                mainHandler.removeCallbacksAndMessages(null) // Stop random movement simulation
                initialX = currentX
                initialY = currentY
                initialTouchX = event.rawX
                initialTouchY = event.rawY
            }

            MotionEvent.ACTION_MOVE -> {
                if (isDragging) {
                    floatingParams.x = initialX + (event.rawX - initialTouchX).toInt()
                    floatingParams.y = initialY + (event.rawY - initialTouchY).toInt()
                    // Prevent dragging completely off vertically during drag
                    floatingParams.y = floatingParams.y.coerceIn(0, getScreenHeight() - floatingView.height)
                    windowManager.updateViewLayout(floatingView, floatingParams)
                }
            }

            MotionEvent.ACTION_UP -> {
                isDragging = false
                // Check if dragged off-screen horizontally
                if (currentX < -floatingView.width * 0.6f || currentX > screenWidth - floatingView.width * 0.4f) {
                    animateOutAndHideTamagotchi()
                } else {
                    // If not dragged off, stay where dropped and resume behavior
                    mainHandler.postDelayed({
                        if (isTamagotchiVisible && !isDragging) {
                            setPetAnimationState(true)
                            simulateTamagotchiBehavior()
                        }
                    }, 500) // Small delay before resuming
                }
            }
        }
    }


    private fun isTamagotchiOffScreen(x: Int): Boolean {
        // Check if the *center* of the Tamagotchi is off screen
        val centerThreshold = floatingView.width / 2
        return x < -centerThreshold || x > getScreenWidth() - centerThreshold
    }

    private fun animateOutAndHideTamagotchi() {
        isTamagotchiVisible = false // Mark as hiding
        stopAnimationLoop() // Stop animation
        mainHandler.removeCallbacksAndMessages(null) // Stop simulation

        val screenWidth = getScreenWidth()
        // Target X well off screen
        val targetX = if (floatingParams.x < screenWidth / 2) -floatingView.width * 2 else screenWidth + floatingView.width

        val animator = ValueAnimator.ofInt(floatingParams.x, targetX)
        animator.duration = 300 // Faster hide animation
        animator.interpolator = DecelerateInterpolator()
        animator.addUpdateListener {
            floatingParams.x = it.animatedValue as Int
            try { // Add try-catch for safety during view updates
                windowManager.updateViewLayout(floatingView, floatingParams)
            } catch (e: IllegalArgumentException) {
                // View might already be removed or detached
                animator.cancel() // Stop animation if view is gone
            }
        }
        animator.doOnEnd {
            floatingView.visibility = View.GONE // Fully hide after animation
            // Bring up the drawer - Make sure it's PEEKING initially
            animateDrawer(DrawerState.PEEKING)
        }
        animator.start()
    }

    private fun showAndResetTamagotchi() {
        floatingParams.x = defaultX
        floatingParams.y = defaultY
        floatingView.visibility = View.VISIBLE
        isTamagotchiVisible = true
        setPetAnimationState(true) // Start with idle animation
        try {
            windowManager.updateViewLayout(floatingView, floatingParams)
        } catch (e: IllegalArgumentException) {
            // Handle case where view might not be attached anymore (less likely here)
            windowManager.addView(floatingView, floatingParams) // Re-add if needed
        }
        simulateTamagotchiBehavior() // Restart random movement if needed
        startAnimationLoop() // Restart animation
    }


    // --- Drawer Methods ---

    private fun addDrawer() {
        val inflater = getSystemService(LAYOUT_INFLATER_SERVICE) as LayoutInflater
        drawerView = inflater.inflate(R.layout.drawer_layout, null) // Ensure this layout exists

        drawerParams = WindowManager.LayoutParams(
            drawerWidth, // Use calculated width
            WindowManager.LayoutParams.WRAP_CONTENT, // <---- THIS IS THE FIX!
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, // Allow partial visibility
            PixelFormat.TRANSLUCENT
        )

        // Position based on 'left' side and initial 'HIDDEN' state
        drawerParams.gravity = Gravity.TOP or Gravity.START // Keep gravity as is (TOP|START initially)
        // You could change gravity later if you want it centered vertically on screen
        // e.g., drawerParams.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        drawerParams.x = -drawerWidth // Start hidden off-screen left
        drawerParams.y = 0 // Align to top

        // --- Button Listeners ---
        drawerView.findViewById<ImageButton>(R.id.btnShowPet)?.setOnClickListener {
            animateDrawer(DrawerState.HIDDEN)
            showAndResetTamagotchi()
        }

        drawerView.findViewById<ImageButton>(R.id.btnChat)?.setOnClickListener {
            speechTextView.text = "Let's Chat!" // Use the class-level speechTextView
            animateDrawer(DrawerState.HIDDEN)
        }

        // --- Drawer Touch Listener for Swiping ---
        drawerView.setOnTouchListener { _, event ->
            handleDrawerTouch(event)
            true // Consume touch event
        }

        drawerView.visibility = View.GONE // Start hidden
        currentDrawerState = DrawerState.HIDDEN
        windowManager.addView(drawerView, drawerParams)
    }

    private fun handleDrawerTouch(event: MotionEvent) {
        val currentX = event.rawX

        when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                lastTouchXDrawer = currentX
                initialDrawerX = drawerParams.x // Store current position
                drawerAnimator?.cancel() // Stop any ongoing animation
            }
            MotionEvent.ACTION_MOVE -> {
                val deltaX = currentX - lastTouchXDrawer
                var newX = initialDrawerX + deltaX.toInt()

                // Constrain movement between hidden and open positions
                if (drawerSide == "left") {
                    newX = newX.coerceIn(-drawerWidth, 0) // Min: -width, Max: 0
                } else { // Adapt if using "right" side
                    val screenWidth = getScreenWidth()
                    newX = newX.coerceIn(screenWidth - drawerWidth, screenWidth)
                }
                drawerParams.x = newX
                try {
                    windowManager.updateViewLayout(drawerView, drawerParams)
                } catch (e: IllegalArgumentException) { /* View likely removed */ }
            }
            MotionEvent.ACTION_UP -> {
                val deltaX = currentX - lastTouchXDrawer
                // Simple velocity calculation (consider using VelocityTracker for more accuracy)
                val eventTime = event.eventTime - event.downTime
                val velocityX = if (eventTime > 0) (deltaX / eventTime) * 1000 else 0f // pixels/sec

                val isSignificantSwipe = abs(deltaX) > SWIPE_MIN_DISTANCE && abs(velocityX) > SWIPE_THRESHOLD_VELOCITY

                if (isSignificantSwipe) {
                    // Swiped significantly: Determine direction
                    if (drawerSide == "left") {
                        if (velocityX > 0) { // Swipe Right (towards screen center) -> Minimize (Peek)
                            animateDrawer(DrawerState.PEEKING)
                        } else { // Swipe Left (off screen) -> Maximize (Open)
                            animateDrawer(DrawerState.OPEN)
                        }
                    } else { // Right side drawer logic (if needed)
                        // if (velocityX < 0) { // Swipe Left -> Minimize
                        //    animateDrawer(DrawerState.PEEKING)
                        // } else { // Swipe Right -> Maximize
                        //    animateDrawer(DrawerState.OPEN)
                        // }
                    }
                } else {
                    // Not a significant swipe, snap based on current position
                    if (drawerSide == "left") {
                        // Calculate the threshold between PEEKING and OPEN
                        val peekPosition = -drawerWidth + drawerPeekOffset
                        val openPosition = 0
                        val snapThreshold = peekPosition + (openPosition - peekPosition) * 0.5f // Midpoint

                        if (drawerParams.x > snapThreshold) {
                            animateDrawer(DrawerState.OPEN)
                        } else {
                            animateDrawer(DrawerState.PEEKING)
                        }
                    } else { // Right side logic (adjust similarly)
                        // val screenWidth = getScreenWidth()
                        // val peekPosition = screenWidth - drawerPeekOffset
                        // val openPosition = screenWidth - drawerWidth
                        // val snapThreshold = openPosition + (peekPosition - openPosition) * 0.5f
                        // if (drawerParams.x < snapThreshold) {
                        //    animateDrawer(DrawerState.OPEN)
                        // } else {
                        //    animateDrawer(DrawerState.PEEKING)
                        // }
                    }
                }
            }
        }
    }


    private fun animateDrawer(targetState: DrawerState) {
        if (currentDrawerState == targetState && drawerAnimator?.isRunning != true) return // No change needed or already animating to target

        drawerAnimator?.cancel() // Cancel previous animation

        val fromX = drawerParams.x
        val toX = when (targetState) {
            DrawerState.HIDDEN -> if (drawerSide == "left") -drawerWidth else getScreenWidth()
            DrawerState.PEEKING -> if (drawerSide == "left") -drawerWidth + drawerPeekOffset else getScreenWidth() - drawerPeekOffset
            DrawerState.OPEN -> if (drawerSide == "left") 0 else getScreenWidth() - drawerWidth
        }

        // Only proceed if target position is different
        if (fromX == toX) {
            // Update state directly if position already matches
            currentDrawerState = targetState
            if (targetState == DrawerState.HIDDEN) {
                drawerView.visibility = View.GONE
            } else {
                drawerView.visibility = View.VISIBLE
            }
            return
        }


        val animator = ValueAnimator.ofInt(fromX, toX)
        animator.duration = 300 // Animation duration
        animator.interpolator = DecelerateInterpolator()

        animator.addUpdateListener {
            drawerParams.x = it.animatedValue as Int
            try {
                windowManager.updateViewLayout(drawerView, drawerParams)
            } catch (e: IllegalArgumentException) {
                animator.cancel() // Stop if view is removed
            }
        }

        animator.doOnStart {
            // Make drawer visible before animating *to* a visible state
            if (targetState != DrawerState.HIDDEN) {
                drawerView.visibility = View.VISIBLE
            }
            currentDrawerState = targetState // Update state at the start of animation
        }

        animator.doOnEnd {
            // Hide drawer *after* animating *to* hidden state
            if (targetState == DrawerState.HIDDEN) {
                drawerView.visibility = View.GONE
            }
            drawerAnimator = null // Clear the reference
        }

        drawerAnimator = animator // Store reference
        animator.start()
    }


    // --- Simulation & Animation ---

    private fun simulateTamagotchiBehavior() {
        // Stop any existing handler callbacks first
        mainHandler.removeCallbacksAndMessages(null)
        val random = Random()
        val screenWidth = getScreenWidth()
        val screenHeight = getScreenHeight()

        val runnable = object : Runnable {
            override fun run() {
                var didMove = false
                // Check if the Tamagotchi view is still attached, visible, and not being dragged
                if (isTamagotchiVisible && ::floatingView.isInitialized && floatingView.isAttachedToWindow && floatingView.visibility == View.VISIBLE && !isDragging) {
                    val viewWidth = floatingView.width
                    val viewHeight = floatingView.height

                    // Only attempt movement if view dimensions are valid
                    if (viewWidth > 0 && viewHeight > 0) {
                        val currentX = floatingParams.x
                        val currentY = floatingParams.y

                        // Decide if the Tamagotchi should move in this cycle
                        if (random.nextInt(3) == 0) { // Move roughly 1/3 of the time
                            val dx = random.nextInt(41) - 20 // Small random horizontal movement (-20 to +20)
                            val dy = random.nextInt(41) - 20 // Small random vertical movement (-20 to +20)

                            // Calculate the new potential position, ensuring it stays within screen bounds
                            val newX = (currentX + dx).coerceIn(0, screenWidth - viewWidth)
                            val newY = (currentY + dy).coerceIn(0, screenHeight - viewHeight)

                            // If the position has changed, update the layout
                            if (newX != currentX || newY != currentY) {
                                floatingParams.x = newX
                                floatingParams.y = newY
                                try {
                                    windowManager.updateViewLayout(floatingView, floatingParams)
                                    didMove = true // Indicate that movement occurred
                                } catch (e: Exception) {
                                    // If updating the view fails, stop the simulation to prevent further errors
                                    mainHandler.removeCallbacks(this)
                                    return
                                }
                            }
                        }

                        // Randomly change the speech bubble text
                        if (random.nextInt(15) == 0 && ::speechTextView.isInitialized) {
                            val messages = listOf("...", "Hmm?", "*looks around*", ":)", "^_^", "!")
                            speechTextView.text = messages.random()
                        }
                    }
                }

                // Update the Tamagotchi's animation state based on whether it moved
                setPetAnimationState(didMove.not()) // If didMove is true, set to walking (idle=false)

                // Schedule the next execution of this runnable after a random delay
                if (isTamagotchiVisible && !isDragging) {
                    mainHandler.postDelayed(this, (1000 + random.nextInt(2000)).toLong()) // Delay between 1 and 3 seconds
                }
            }
        }
        // Start the simulation loop with an initial delay
        if (isTamagotchiVisible && !isDragging) {
            mainHandler.postDelayed(runnable, 1500)
        }
    }

    // --- Animation Loop ---
    private val animationRunnable = object : Runnable {
        override fun run() {
            // Only animate if the Tamagotchi is visible and the ImageView is initialized and attached
            if (isTamagotchiVisible && ::petImageView.isInitialized && petImageView.isAttachedToWindow) {
                val currentAnimation = if (isPetIdle) idleAnimationFrames else walkingAnimationFrames

                // Ensure the animation frame list is not empty to prevent errors
                if (currentAnimation.isNotEmpty()) {
                    currentFrameIndex = (currentFrameIndex + 1) % currentAnimation.size
                    val frameResId = currentAnimation[currentFrameIndex]

                    try {
                        petImageView.setImageResource(frameResId)
                    } catch (e: Exception) {
                        // Handle potential issues with resource IDs
                        e.printStackTrace()
                        stopAnimationLoop() // Stop the animation loop if an error occurs
                        return
                    }

                    // Schedule the next frame update
                    animationHandler.postDelayed(this, frameUpdateDelay)
                }
            }
        }
    }

    private fun startAnimationLoop() {
        // Stop any existing animation loop to prevent multiple loops running
        stopAnimationLoop()
        // Start the animation loop by posting the first frame update
        if (isTamagotchiVisible) {
            animationHandler.post(animationRunnable)
        }
    }

    private fun stopAnimationLoop() {
        // Remove any pending callbacks for the animation runnable
        animationHandler.removeCallbacks(animationRunnable)
    }

    private fun setPetAnimationState(idle: Boolean) {
        // Only update the animation state if it's different from the current state
        if (isPetIdle != idle) {
            isPetIdle = idle
            currentFrameIndex = 0 // Reset the frame index when switching animation states
        }
    }

    // --- Helper Methods ---

    private fun getScreenWidth(): Int {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        return metrics.widthPixels
    }

    private fun getScreenHeight(): Int {
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        return metrics.heightPixels
    }

    // Helper to convert Density Pixels (DP) to actual pixels
    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).roundToInt()
    }


    override fun onDestroy() {
        super.onDestroy()
        // Ensure all handlers and animators are stopped to prevent memory leaks
        mainHandler.removeCallbacksAndMessages(null)
        stopAnimationLoop()
        drawerAnimator?.cancel()

        // Safely remove the views from the WindowManager if they are attached
        try {
            if (::floatingView.isInitialized && floatingView.isAttachedToWindow) {
                windowManager.removeView(floatingView)
            }
        } catch (_: Exception) {
            // Ignore exceptions if the view was already removed
        }
        try {
            if (::drawerView.isInitialized && drawerView.isAttachedToWindow) {
                windowManager.removeView(drawerView)
            }
        } catch (_: Exception) {
            // Ignore exceptions if the view was already removed
        }
    }
}