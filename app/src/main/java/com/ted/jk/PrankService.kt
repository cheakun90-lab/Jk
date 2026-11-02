package com.ted.jk

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.drawable.Icon
import android.media.MediaPlayer
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.WindowManager.LayoutParams as LP
import android.widget.ImageView
import android.widget.Toast
import kotlin.math.abs

// A harmless prank: Teds bounce around the screen and you pop them by tapping.
// It never starts on its own, never hides, and always stops itself.
class PrankService : Service() {

    private class Ted(
        val view: ImageView, val lp: LP,
        var x: Float, var y: Float, var vx: Float, var vy: Float
    ) {
        var hp = 1
        var dying = false
    }

    private lateinit var wm: WindowManager
    private val ui = Handler(Looper.getMainLooper())
    private val teds = mutableListOf<Ted>()
    private val allViews = mutableListOf<View>()
    private var running = false
    private var total = 10
    private var startTime = 0L
    private var popped = 0
    private var bossPhase = false
    private var music: MediaPlayer? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == "STOP") { stopSelf(); return START_NOT_STICKY }
        if (!running) begin(intent?.getIntExtra("count", 10) ?: 10)
        return START_NOT_STICKY
    }

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun begin(count: Int) {
        running = true
        total = count.coerceIn(1, 20)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager
        startForeground(1, note("$total to pop"))
        startTime = SystemClock.elapsedRealtime()
        repeat(total) { spawn() }
        ui.post(tick)
        ui.postDelayed(autoStop, 180_000)
        ui.postDelayed(respawn, 20_000)
        startMusic()
    }

    private fun spawn() {
        val m = resources.displayMetrics
        val size = dp(90)
        val px = (0..(m.widthPixels - size).coerceAtLeast(1)).random().toFloat()
        val py = (0..(m.heightPixels - size).coerceAtLeast(1)).random().toFloat()
        fun speed(): Float = ((dp(2) + (0..dp(3)).random()) * (if ((0..1).random() == 0) 1 else -1)).toFloat()

        val iv = ImageView(this).apply {
            setImageResource(R.drawable.jk_ted)
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        val lp = LP(
            size, size, LP.TYPE_APPLICATION_OVERLAY,
            LP.FLAG_NOT_FOCUSABLE or LP.FLAG_LAYOUT_NO_LIMITS, PixelFormat.TRANSLUCENT
        ).apply { gravity = Gravity.TOP or Gravity.START; this.x = px.toInt(); this.y = py.toInt() }

        val t = Ted(iv, lp, px, py, speed(), speed())
        iv.setOnTouchListener { _, e -> if (e.action == MotionEvent.ACTION_DOWN) hit(t); true }
        wm.addView(iv, lp)
        allViews.add(iv)
        teds.add(t)
    }

    // one frame of bouncing, about 60 times a second
    private val tick = object : Runnable {
        override fun run() {
            if (!running) return
            val m = resources.displayMetrics
            for (t in teds) {
                t.x += t.vx; t.y += t.vy
                val maxX = (m.widthPixels - t.lp.width).toFloat()
                val maxY = (m.heightPixels - t.lp.height).toFloat()
                if (t.x < 0f) { t.x = 0f; t.vx = abs(t.vx) } else if (t.x > maxX) { t.x = maxX; t.vx = -abs(t.vx) }
                if (t.y < 0f) { t.y = 0f; t.vy = abs(t.vy) } else if (t.y > maxY) { t.y = maxY; t.vy = -abs(t.vy) }
                t.lp.x = t.x.toInt(); t.lp.y = t.y.toInt()
                try { wm.updateViewLayout(t.view, t.lp) } catch (e: Exception) { }
            }
            ui.postDelayed(this, 16)
        }
    }

    private fun hit(t: Ted) {
        if (t.dying) return
        t.hp--
        if (t.hp > 0) {                                   // the last one takes a few hits
            t.vx *= 1.25f; t.vy *= 1.25f
            t.view.animate().scaleX(0.75f).scaleY(0.75f).setDuration(80).withEndAction {
                t.view.animate().scaleX(1f).scaleY(1f).setDuration(80).start()
            }.start()
            playNamed("jk_pop")
            return
        }
        t.dying = true
        teds.remove(t)
        popped++
        playNamed("jk_pop")
        t.view.animate().scaleX(0f).scaleY(0f).alpha(0f).rotation(360f).setDuration(220).withEndAction {
            try { wm.removeView(t.view) } catch (e: Exception) { }
            allViews.remove(t.view)
        }.start()
        for (o in teds) { o.vx *= 1.07f; o.vy *= 1.07f }  // the rest get a little faster
        if (teds.size == 1) makeBoss(teds[0])
        notify1("${teds.size} left")
        if (teds.isEmpty()) win()
    }

    private fun makeBoss(t: Ted) {
        bossPhase = true
        t.hp = 3
        t.lp.width = dp(130); t.lp.height = dp(130)
        t.vx *= 1.4f; t.vy *= 1.4f
        Toast.makeText(this, "Last one. It takes 3 hits.", Toast.LENGTH_SHORT).show()
    }

    private fun win() {
        val secs = (SystemClock.elapsedRealtime() - startTime) / 1000.0
        playNamed("jk_win")
        Toast.makeText(this, "JK. No virus. You popped $popped in ${"%.1f".format(secs)}s.", Toast.LENGTH_LONG).show()
        ui.postDelayed({ stopSelf() }, 2500)
    }

    private val autoStop = Runnable {
        Toast.makeText(this, "Time's up. JK.", Toast.LENGTH_LONG).show()
        stopSelf()
    }

    // Sounds are optional. Drop jk_music / jk_pop / jk_win into res/raw and they just work.
    private fun rawId(name: String) = resources.getIdentifier(name, "raw", packageName)

    private fun playNamed(name: String) {
        val id = rawId(name)
        if (id == 0) return
        try {
            MediaPlayer.create(this, id)?.apply {
                setOnCompletionListener { it.release() }
                start()
            }
        } catch (e: Exception) { }
    }

    private fun startMusic() {
        val id = rawId("jk_music")
        if (id == 0) return
        try {
            music = MediaPlayer.create(this, id)?.apply { isLooping = true; start() }
        } catch (e: Exception) { }
    }

    // if you are slow, a popped Ted comes back every 20 seconds (never during the last one)
    private val respawn = object : Runnable {
        override fun run() {
            if (!running) return
            if (!bossPhase && popped > 0 && teds.size < total) {
                spawn()
                notify1("${teds.size} left")
            }
            ui.postDelayed(this, 20_000)
        }
    }

    private fun note(text: String): Notification {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(NotificationChannel("jk", "JK Ted", NotificationManager.IMPORTANCE_LOW))
        val stop = PendingIntent.getService(
            this, 0, Intent(this, PrankService::class.java).setAction("STOP"),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        return Notification.Builder(this, "jk")
            .setContentTitle("JK Ted prank")
            .setContentText(text)
            .setSmallIcon(R.drawable.jk_ted)
            .addAction(Notification.Action.Builder(Icon.createWithResource(this, R.drawable.jk_ted), "Stop", stop).build())
            .build()
    }

    private fun notify1(text: String) {
        (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).notify(1, note(text))
    }

    override fun onDestroy() {
        running = false
        music?.release(); music = null
        ui.removeCallbacksAndMessages(null)
        for (v in allViews) { try { wm.removeView(v) } catch (e: Exception) { } }
        allViews.clear(); teds.clear()
        super.onDestroy()
    }
}
