package com.example.adssdk

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class BannerAdView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        minimumHeight = dp(context, 60)
        setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6))
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(context, 10).toFloat()
            setStroke(dp(context, 1), Color.parseColor("#DDDDDD"))
        }
        visibility = View.GONE
    }

    /** Loads an ad. Call this if you add BannerAdView from XML. */
    @JvmOverloads
    fun load(unitId: String = "", listener: AdListener? = null) {
        bind(unitId, listener, true)
    }

    private fun bind(unitId: String, listener: AdListener?, retry: Boolean) {
        val ad = AdRepository.random(unitId.ifBlank { null })

        if (ad == null) {
            visibility = View.GONE
            if (retry) {
                AdRepository.refresh { bind(unitId, listener, false) }
            } else {
                listener?.onAdFailed()
            }
            return
        }

        visibility = View.VISIBLE
        removeAllViews()

        val icon = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        addView(icon, LayoutParams(dp(context, 44), dp(context, 44)))
        ImageLoader.load(ad.iconUrl, icon)

        val texts = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(context, 10), 0, dp(context, 10), 0)
        }
        texts.addView(TextView(context).apply {
            text = ad.title
            setTextColor(Color.BLACK)
            textSize = 14f
            maxLines = 1
        })
        texts.addView(TextView(context).apply {
            text = ad.description
            setTextColor(Color.GRAY)
            textSize = 12f
            maxLines = 1
        })
        addView(texts, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))

        addView(TextView(context).apply {
            text = ad.buttonText
            setTextColor(Color.WHITE)
            textSize = 13f
            gravity = Gravity.CENTER
            setPadding(dp(context, 14), dp(context, 8), dp(context, 14), dp(context, 8))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E88E5"))
                cornerRadius = dp(context, 18).toFloat()
            }
        })

        setOnClickListener {
            AdSdk.openAd(context, ad)
            listener?.onAdClicked()
        }
        listener?.onAdLoaded()
    }
}