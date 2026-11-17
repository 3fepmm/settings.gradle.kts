package com.example.afkmakro

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.Context
import android.graphics.Color
import android.graphics.Path
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import kotlin.random.Random

class MacroService : AccessibilityService() {
    private val h = Handler(Looper.getMainLooper())
    private lateinit var wm: WindowManager
    private var panel: LinearLayout? = null
    private var capture: View? = null
    private var status: TextView? = null
    private var recBtn: Button? = null
    private var steps = mutableListOf<Step>()
    private var recording = false
    private var playing = false
    private var rightSide = false
    private var idx = 0
    private var round = 0
    private var lastAfk = 0L
    private var stopAt = 0L
    private var token = 0
    private var lastEnd = 0L
    private var downWall = 0L

    override fun onServiceConnected() {
        super.onServiceConnected()
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        steps = Store.loadSteps(this)
        buildPanel()
        showPanel()
        setStatus("Hazır: ${steps.size} adım")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}

    override fun onInterrupt() {}

    override fun onDestroy() {
        token++
        h.removeCallbacksAndMessages(null)
        if (::wm.isInitialized) {
            for (v in listOf(panel, capture)) {
                if (v != null) {
                    try { wm.removeView(v) } catch (e: Exception) {}
                }
            }
        }
        super.onDestroy()
    }

    // ---------- Panel ----------

    private fun btn(t: String, onClick: () -> Unit): Button {
        val b = Button(this)
        b.text = t
        b.textSize = 11f
        b.setAllCaps(false)
        b.setPadding(8, 0, 8, 0)
        b.minWidth = 0
        b.minimumWidth = 0
        b.setOnClickListener { onClick() }
        return b
    }

    private fun buildPanel() {
        val box = LinearLayout(this)
        box.orientation = LinearLayout.VERTICAL
        box.setBackgroundColor(Color.argb(200, 30, 30, 30))
        box.setPadding(8, 8, 8, 8)

        val st = TextView(this)
        st.setTextColor(Color.WHITE)
        st.textSize = 12f
        status = st

        val row = LinearLayout(this)
        row.orientation = LinearLayout.HORIZONTAL
        val rec = btn("Kayıt") { toggleRecord() }
        recBtn = rec
        row.addView(rec)
        row.addView(btn("Oynat") { startPlay() })
        row.addView(btn("Dur") { stopPlay("Durdu") })
        row.addView(btn("Yan") {
            rightSide = !rightSide
            showPanel()
        })

        box.addView(st)
        box.addView(row)
        panel = box
    }

    private fun showPanel() {
        val p = panel ?: return
        try { wm.removeView(p) } catch (e: Exception) {}
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or (if (rightSide) Gravity.END else Gravity.START)
        lp.y = 120
        wm.addView(p, lp)
    }

    private fun setStatus(t: String) {
        status?.text = t
    }

    // ---------- Kayıt ----------

    private fun toggleRecord() {
        if (recording) {
            recording = false
            val c = capture
            if (c != null) {
                try { wm.removeView(c) } catch (e: Exception) {}
            }
            capture = null
            Store.saveSteps(this, steps)
            recBtn?.text = "Kayıt"
            setStatus("Kaydedildi: ${steps.size} adım")
        } else {
            stopPlay("Kayıt modu")
            steps = mutableListOf()
            recording = true
            lastEnd = 0L
            recBtn?.text = "Bitir"
            showCapture()
            showPanel()
            setStatus("Kayıt: dokun")
        }
    }

    private fun showCapture() {
        val v = View(this)
        v.setBackgroundColor(Color.argb(50, 0, 120, 255))
        var sx = 0f
        var sy = 0f
        v.setOnTouchListener { _, e ->
            if (e.action == MotionEvent.ACTION_DOWN) {
                sx = e.rawX
                sy = e.rawY
                downWall = System.currentTimeMillis()
            } else if (e.action == MotionEvent.ACTION_UP) {
                val dx = (e.rawX - sx).toDouble()
                val dy = (e.rawY - sy).toDouble()
                val moved = Math.hypot(dx, dy) > 40
                val delay = if (lastEnd == 0L) 500L
                else (downWall - lastEnd).coerceAtLeast(200L)
                val dur = (e.eventTime - e.downTime).coerceIn(50L, 3000L)
                val ex = if (moved) e.rawX else sx
                val ey = if (moved) e.rawY else sy
                steps.add(Step(sx, sy, ex, ey, dur, delay))
                lastEnd = System.currentTimeMillis()
                setStatus("Kayıt: ${steps.size} adım")
            }
            true
        }
        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.TRANSLUCENT
        )
        wm.addView(v, lp)
        capture = v
    }

    // ---------- Oynatma ----------

    private fun jittered(ms: Long, pct: Int): Long {
        if (pct <= 0) return ms
        val span = ms * pct / 100
        return (ms - span + Random.nextLong(0, 2 * span + 1)).coerceAtLeast(50L)
    }

    private fun startPlay() {
        if (recording) toggleRecord()
        if (steps.isEmpty()) {
            setStatus("Önce kayıt yap")
            return
        }
        stopPlay("Başlıyor")
        val p = Store.prefs(this)
        val startMs = p.getInt("startMin", 0) * 60000L
        val stopMin = p.getInt("stopMin", 0)
        playing = true
        val my = ++token
        if (startMs > 0) setStatus("${startMs / 60000} dk sonra başlar")
        h.postDelayed({
            if (playing && my == token) {
                val now = System.currentTimeMillis()
                stopAt = if (stopMin > 0) now + stopMin * 60000L else 0L
                lastAfk = now
                idx = 0
                round = 0
                next(my)
            }
        }, startMs)
    }

    private fun stopPlay(msg: String) {
        playing = false
        token++
        h.removeCallbacksAndMessages(null)
        setStatus(msg)
    }

    private fun next(my: Int) {
        if (!playing || my != token) return
        val p = Store.prefs(this)
        val now = System.currentTimeMillis()
        if (stopAt > 0 && now >= stopAt) {
            stopPlay("Süre doldu")
            return
        }
        val afkMs = p.getInt("afkMin", 10) * 60000L
        if (afkMs > 0 && now - lastAfk >= afkMs) {
            lastAfk = now
            val m = resources.displayMetrics
            val cx = m.widthPixels * 0.75f
            val cy = m.heightPixels * 0.5f
            gesture(cx, cy, cx + 80f, cy, 250L) {
                h.postDelayed({ next(my) }, 500L)
            }
            return
        }
        val s = steps[idx]
        setStatus("Tur ${round + 1}  adım ${idx + 1}/${steps.size}")
        val wait = jittered(s.delay, p.getInt("jitter", 20))
        h.postDelayed({
            if (playing && my == token) {
                gesture(s.x1, s.y1, s.x2, s.y2, s.dur) { afterStep(my) }
            }
        }, wait)
    }

    private fun afterStep(my: Int) {
        if (!playing || my != token) return
        idx++
        if (idx >= steps.size) {
            idx = 0
            round++
            val maxR = Store.prefs(this).getInt("rounds", 0)
            if (maxR > 0 && round >= maxR) {
                stopPlay("Bitti: $round tur")
                return
            }
        }
        next(my)
    }

    private fun gesture(
        x1: Float, y1: Float, x2: Float, y2: Float,
        dur: Long, done: () -> Unit
    ) {
        val path = Path()
        path.moveTo(x1, y1)
        if (x1 != x2 || y1 != y2) path.lineTo(x2, y2)
        val g = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0L, dur.coerceAtLeast(1L)))
            .build()
        val cb = object : AccessibilityService.GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                done()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                done()
            }
        }
        val ok = dispatchGesture(g, cb, null)
        if (!ok) h.postDelayed({ done() }, 1000L)
    }
}
