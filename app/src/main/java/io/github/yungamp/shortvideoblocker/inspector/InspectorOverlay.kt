package io.github.yungamp.shortvideoblocker.inspector

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.graphics.toColorInt
import kotlin.math.abs

/**
 * Small draggable button shown on top of YouTube/Instagram while the inspector is active.
 * Tap → list of scenarios for the current app → tap a scenario → dump is captured.
 *
 * Uses TYPE_ACCESSIBILITY_OVERLAY (no extra permission) and FLAG_NOT_FOCUSABLE, so the
 * target app keeps the active window while we capture it.
 */
class InspectorOverlay(
    private val service: AccessibilityService,
    private val doneCounts: () -> Map<String, Int>,
    private val onScenarioChosen: (Scenario) -> Unit,
) {
    private val wm = service.getSystemService(WindowManager::class.java)
    private val touchSlop = ViewConfiguration.get(service).scaledTouchSlop

    private var root: LinearLayout? = null
    private var button: TextView? = null
    private var panel: ScrollView? = null
    private var currentPkg: String? = null
    private var busy = false

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        x = 0
        y = dp(220)
    }

    val isShown: Boolean get() = root != null

    fun show(pkg: String) {
        if (root != null && currentPkg == pkg) return
        hide()
        currentPkg = pkg
        val layout = LinearLayout(service).apply { orientation = LinearLayout.VERTICAL }
        val btn = createButton()
        layout.addView(btn)
        root = layout
        button = btn
        wm.addView(layout, params)
        updateButton()
    }

    fun hide() {
        root?.let { runCatching { wm.removeView(it) } }
        root = null
        button = null
        panel = null
    }

    fun setBusy(value: Boolean) {
        busy = value
        updateButton()
    }

    private fun updateButton() {
        val b = button ?: return
        b.text = if (busy) "…" else "SVB"
        b.alpha = if (busy) 0.5f else 0.85f
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createButton(): TextView = TextView(service).apply {
        text = "SVB"
        setTextColor(Color.WHITE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        gravity = Gravity.CENTER
        val size = dp(52)
        layoutParams = LinearLayout.LayoutParams(size, size)
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor("#E0D32F2F".toColorInt())
            setStroke(dp(2), Color.WHITE)
        }
        contentDescription = "ShortVideoBlocker Inspector"

        var downX = 0f
        var downY = 0f
        var startX = 0
        var startY = 0
        var dragging = false
        setOnTouchListener { _, ev ->
            when (ev.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = ev.rawX; downY = ev.rawY
                    startX = params.x; startY = params.y
                    dragging = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = ev.rawX - downX
                    val dy = ev.rawY - downY
                    if (!dragging && (abs(dx) > touchSlop || abs(dy) > touchSlop)) dragging = true
                    if (dragging) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        root?.let { runCatching { wm.updateViewLayout(it, params) } }
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (!dragging) togglePanel()
                    true
                }
                else -> false
            }
        }
    }

    private fun togglePanel() {
        if (busy) return
        val layout = root ?: return
        panel?.let {
            layout.removeView(it)
            panel = null
            return
        }
        val pkg = currentPkg ?: return
        val counts = doneCounts()
        val list = LinearLayout(service).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(12).toFloat()
                setColor("#F0202020".toColorInt())
            }
        }
        list.addView(TextView(service).apply {
            text = "Was ist gerade zu sehen?"
            setTextColor(Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(4), 0, dp(4), dp(6))
        })
        (Scenarios.forPackage(pkg) + Scenarios.other(pkg)).forEach { scenario ->
            val done = counts[scenario.id] ?: 0
            list.addView(Button(service).apply {
                isAllCaps = false
                textAlignment = View.TEXT_ALIGNMENT_VIEW_START
                text = (if (done > 0) "✓ " else "") + scenario.label + if (done > 1) " ($done)" else ""
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                setOnClickListener {
                    layout.removeView(panel)
                    panel = null
                    onScenarioChosen(scenario)
                }
            })
        }
        list.addView(Button(service).apply {
            isAllCaps = false
            text = "Schließen"
            setOnClickListener {
                layout.removeView(panel)
                panel = null
            }
        })
        val maxHeight = (service.resources.displayMetrics.heightPixels * 0.6f).toInt()
        val scroll = object : ScrollView(service) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                super.onMeasure(
                    widthMeasureSpec,
                    View.MeasureSpec.makeMeasureSpec(maxHeight, View.MeasureSpec.AT_MOST),
                )
            }
        }.apply {
            addView(list)
            layoutParams = LinearLayout.LayoutParams(dp(260), LinearLayout.LayoutParams.WRAP_CONTENT)
        }
        layout.addView(scroll)
        panel = scroll
    }

    private fun dp(v: Int): Int = (v * service.resources.displayMetrics.density).toInt()
}
