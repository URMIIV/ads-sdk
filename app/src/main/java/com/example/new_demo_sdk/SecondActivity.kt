package com.example.new_demo_sdk

import android.os.Bundle
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.adssdk.Ads

class SecondActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(32, 160, 32, 32)
        }
        root.addView(TextView(this).apply {
            text = "Second screen\n(opened after the interstitial)"
            textSize = 20f
            gravity = Gravity.CENTER
        })

        val bannerSlot = FrameLayout(this)
        root.addView(bannerSlot)
        setContentView(root)

        Ads.loadBanner(this, bannerSlot, AdIds.BANNER_HOME)
    }

    override fun onPause() {
        Ads.onPause(this)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        Ads.onResume(this)
    }

    override fun onDestroy() {
        Ads.onDestroy(this)
        super.onDestroy()
    }
}