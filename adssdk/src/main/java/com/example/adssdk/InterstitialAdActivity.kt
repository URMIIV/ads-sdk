package com.example.adssdk

import android.app.Activity
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class InterstitialAdActivity : Activity() {

    companion object {
        internal var callback: (() -> Unit)? = null
    }

    private var closed = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val ad = AdRepository.find(intent.getStringExtra("ad_id"))
        if (ad == null) {
            finish()
            return
        }

        val root = FrameLayout(this).apply {
            setBackgroundColor(Color.parseColor("#F2000000"))
        }

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(this@InterstitialAdActivity, 20), dp(this@InterstitialAdActivity, 60),
                dp(this@InterstitialAdActivity, 20), dp(this@InterstitialAdActivity, 24))
        }

        // main image
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            adjustViewBounds = true
        }
        column.addView(image, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        ImageLoader.load(ad.imageUrl, image)

        // icon + title row
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val icon = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        row.addView(icon, LinearLayout.LayoutParams(dp(this, 52), dp(this, 52)))
        ImageLoader.load(ad.iconUrl, icon)

        val title = TextView(this).apply {
            text = ad.title
            setTextColor(Color.WHITE)
            textSize = 18f
            setPadding(dp(this@InterstitialAdActivity, 12), 0, 0, 0)
            maxLines = 2
        }
        row.addView(title, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        val rowParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        rowParams.topMargin = dp(this, 16)
        column.addView(row, rowParams)

        // description
        val desc = TextView(this).apply {
            text = ad.description
            setTextColor(Color.parseColor("#CCCCCC"))
            textSize = 14f
            gravity = Gravity.CENTER
            maxLines = 3
        }
        val descParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        descParams.topMargin = dp(this, 10)
        column.addView(desc, descParams)

        // install button
        val button = TextView(this).apply {
            text = ad.buttonText
            setTextColor(Color.WHITE)
            textSize = 16f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E88E5"))
                cornerRadius = dp(this@InterstitialAdActivity, 28).toFloat()
            }
            setOnClickListener {
                AdSdk.openAd(this@InterstitialAdActivity, ad)
                finish()
            }
        }
        val buttonParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(this, 52))
        buttonParams.topMargin = dp(this, 18)
        column.addView(button, buttonParams)

        root.addView(column, FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))

        // close button
        val close = TextView(this).apply {
            text = "✕"
            setTextColor(Color.WHITE)
            textSize = 22f
            gravity = Gravity.CENTER
            setOnClickListener { finish() }
        }
        val closeParams = FrameLayout.LayoutParams(dp(this, 48), dp(this, 48))
        closeParams.gravity = Gravity.TOP or Gravity.END
        closeParams.topMargin = dp(this, 12)
        closeParams.marginEnd = dp(this, 12)
        root.addView(close, closeParams)

        setContentView(root)
    }

    override fun finish() {
        super.finish()
        if (!closed) {
            closed = true
            callback?.invoke()
            callback = null
        }
    }
}