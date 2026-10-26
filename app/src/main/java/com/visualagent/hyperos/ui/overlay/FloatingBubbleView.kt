package com.visualagent.hyperos.ui.overlay

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Rect
import android.os.Build
import android.provider.Settings
import android.view.Choreographer
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import com.visualagent.hyperos.MainActivity
import com.visualagent.hyperos.R
import com.visualagent.hyperos.agent.AgentRunner
import com.visualagent.hyperos.agent.GeminiLiveVoiceManager
import com.visualagent.hyperos.ui.sound.McSoundManager
import kotlin.math.abs
import kotlin.math.sin

/**
 * 原生 Minecraft 方块材质高刷悬浮球：
 * - 待机状态渲染真实的 Minecraft 草方块贴图 (mc_grass_block_icon)
 * - 运行/语音状态渲染真实的 Minecraft 命令方块贴图 (mc_command_block_icon)
 * - Choreographer 原生跟随高刷，拖拽时自动关闭发光边框计算
 */
class FloatingBubbleView(
    context: Context,
    private val windowManager: WindowManager,
    private val layoutParams: WindowManager.LayoutParams
) : View(context), Choreographer.FrameCallback {

    private val density = resources.displayMetrics.density
    private val bubbleSizePx = (54 * density).toInt()

    private val grassBitmap: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.mc_grass_block_icon)
    private val cmdBitmap: Bitmap = BitmapFactory.decodeResource(resources, R.drawable.mc_command_block_icon)
    private val dstRect = Rect(0, 0, bubbleSizePx, bubbleSizePx)

    private val bitmapPaint = Paint().apply {
        isFilterBitmap = false // 保持原汁原味的像素颗粒感，禁止双线性模糊
        isAntiAlias = false
    }
    private val borderPaint = Paint().apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textSize = 10.5f * density
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
        setShadowLayer(2f * density, 1.5f * density, 1.5f * density, Color.BLACK)
    }

    private var phaseRad = 0f
    private var isAttached = false
    private var isDragging = false
    private var downRawX = 0f
    private var downRawY = 0f
    private var initialWinX = 0
    private var initialWinY = 0
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        isAttached = true
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onDetachedFromWindow() {
        isAttached = false
        Choreographer.getInstance().removeFrameCallback(this)
        super.onDetachedFromWindow()
    }

    override fun doFrame(frameTimeNanos: Long) {
        if (!isAttached) return
        if (!isDragging) {
            val running = AgentRunner.isRunning.value
            val liveVoice = GeminiLiveVoiceManager.isLiveActive.value
            val speed = if (running || liveVoice) 0.14f else 0.04f
            phaseRad = (phaseRad + speed) % (Math.PI.toFloat() * 2f)
            invalidate()
        }
        Choreographer.getInstance().postFrameCallback(this)
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        setMeasuredDimension(bubbleSizePx, bubbleSizePx)
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width
        val h = height
        dstRect.set(0, 0, w, h)

        val isRunning = AgentRunner.isRunning.value
        val isVoice = GeminiLiveVoiceManager.isLiveActive.value

        // 1. 绘制真实的 Minecraft 草方块或命令方块材质贴图
        val bmp = if (isRunning || isVoice) cmdBitmap else grassBitmap
        canvas.drawBitmap(bmp, null, dstRect, bitmapPaint)

        // 2. 非拖拽时绘制附魔呼吸像素边框
        if (!isDragging) {
            val alpha = ((sin(phaseRad.toDouble()) * 0.5 + 0.5) * 160 + 50).toInt()
            borderPaint.color = when {
                isRunning -> Color.argb(alpha, 255, 85, 85)
                isVoice -> Color.argb(alpha, 85, 255, 255)
                else -> Color.argb(alpha, 120, 230, 90)
            }
            val inset = borderPaint.strokeWidth / 2f
            canvas.drawRect(inset, inset, w - inset, h - inset, borderPaint)
        }

        // 3. 底部像素状态标签
        val label = when {
            isDragging -> "移动"
            isRunning -> "停止"
            isVoice -> "通话"
            else -> "AI"
        }
        canvas.drawText(label, w / 2f, h - 7f * density, textPaint)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                initialWinX = layoutParams.x
                initialWinY = layoutParams.y
                isDragging = false
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - downRawX
                val dy = event.rawY - downRawY
                if (!isDragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) {
                    isDragging = true
                    invalidate()
                }
                if (isDragging) {
                    layoutParams.x = (initialWinX + dx).toInt()
                    layoutParams.y = (initialWinY + dy).toInt()
                    try {
                        windowManager.updateViewLayout(this, layoutParams)
                    } catch (_: Exception) {}
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (isDragging) {
                    isDragging = false
                    invalidate()
                } else {
                    handleBubbleClick()
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun handleBubbleClick() {
        McSoundManager.playClick()
        if (AgentRunner.isRunning.value) {
            AgentRunner.cancelCurrentTask("已通过悬浮方块停止任务")
            return
        }
        val intent = Intent(context, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            putExtra(MainActivity.EXTRA_OPEN_TAB, MainActivity.TAB_CHAT)
        }
        context.startActivity(intent)
    }
}

object FloatingOverlayController {
    private var bubbleView: FloatingBubbleView? = null
    private var currentWm: WindowManager? = null

    fun attachToAccessibilityOverlay(serviceContext: Context) {
        if (bubbleView != null) return
        val wm = serviceContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 460
        }
        val view = FloatingBubbleView(serviceContext, wm, params)
        try {
            wm.addView(view, params)
            bubbleView = view
            currentWm = wm
        } catch (_: Exception) {}
    }

    fun attachToApplicationOverlayIfPermitted(context: Context) {
        if (bubbleView != null) return
        if (!Settings.canDrawOverlays(context)) return
        val appCtx = context.applicationContext
        val wm = appCtx.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 24
            y = 460
        }
        val view = FloatingBubbleView(appCtx, wm, params)
        try {
            wm.addView(view, params)
            bubbleView = view
            currentWm = wm
        } catch (_: Exception) {}
    }

    fun detachOverlay() {
        val view = bubbleView ?: return
        try {
            currentWm?.removeView(view)
        } catch (_: Exception) {}
        bubbleView = null
        currentWm = null
    }
}
