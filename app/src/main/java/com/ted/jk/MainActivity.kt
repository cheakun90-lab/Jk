package com.ted.jk

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*

class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val d = resources.displayMetrics.density
        val pad = (20 * d).toInt()

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad * 2, pad, pad)
        }
        val img = ImageView(this).apply {
            setImageResource(R.drawable.jk_ted)
            layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, (150 * d).toInt())
        }
        val title = TextView(this).apply { text = "JK Ted"; textSize = 26f }
        val about = TextView(this).apply {
            text = "A harmless prank. Teds bounce around your screen and you pop them one by one. Be quick, popped Teds slowly come back. " +
                "Nothing is installed or hidden, it never starts by itself, and it ends when you pop " +
                "them all, after 3 minutes, or when you tap Stop."
            textSize = 15f
            setPadding(0, pad / 2, 0, pad / 2)
        }
        val countLabel = TextView(this).apply { text = "Teds: 10"; textSize = 16f }
        val seek = SeekBar(this).apply { max = 19; progress = 9 }       // 1 to 20
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(s: SeekBar?, p: Int, fromUser: Boolean) { countLabel.text = "Teds: ${p + 1}" }
            override fun onStartTrackingTouch(s: SeekBar?) {}
            override fun onStopTrackingTouch(s: SeekBar?) {}
        })
        val permBtn = Button(this).apply { text = "1. Allow Teds to appear over other apps" }
        val startBtn = Button(this).apply { text = "2. Start the prank" }
        val stopBtn = Button(this).apply { text = "Stop" }

        permBtn.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else toast("Already allowed")
        }
        startBtn.setOnClickListener {
            if (!Settings.canDrawOverlays(this)) { toast("Do step 1 first"); return@setOnClickListener }
            if (Build.VERSION.SDK_INT >= 33) requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
            startForegroundService(Intent(this, PrankService::class.java).putExtra("count", seek.progress + 1))
        }
        stopBtn.setOnClickListener { stopService(Intent(this, PrankService::class.java)) }

        listOf(img, title, about, countLabel, seek, permBtn, startBtn, stopBtn).forEach { root.addView(it) }
        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun toast(s: String) = Toast.makeText(this, s, Toast.LENGTH_SHORT).show()
}
