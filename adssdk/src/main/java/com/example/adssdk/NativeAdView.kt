package com.example.adssdk

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

class NativeAdView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : LinearLayout(context, attrs) {

    private val TAG = "urmii"

    init {
        orientation = VERTICAL
        setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12))
        background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(context, 12).toFloat()
            setStroke(dp(context, 1), Color.parseColor("#DDDDDD"))
        }
        visibility = View.GONE
    }

    /** Loads an ad. Call this if you add NativeAdView from XML. */
    @JvmOverloads
    fun load(unitId: String = "", listener: AdListener? = null) {
        Log.d(TAG, "NativeAdView: load(unitId='$unitId') called")
        bind(unitId, listener, true)
    }

    private fun bind(unitId: String, listener: AdListener?, retry: Boolean) {
        val ad = AdRepository.random(unitId.ifBlank { null })

        if (ad == null) {
            Log.w(TAG, "NativeAdView: [unitId='$unitId'] OWN native ad not available (retry=$retry)")
            visibility = View.GONE
            if (retry) {
                AdRepository.refresh { bind(unitId, listener, false) }
            } else {
                listener?.onAdFailed()
            }
            return
        }

        Log.d(TAG, "NativeAdView: [unitId='$unitId'] OWN native loaded -> id='${ad.id}', title='${ad.title}', description='${ad.description}', iconUrl='${ad.iconUrl}', imageUrl='${ad.imageUrl}'")

        visibility = View.VISIBLE
        removeAllViews()

        val open = {
            AdSdk.openAd(context, ad)
            listener?.onAdClicked()
        }

        // header: icon + (Ad badge, title, description)
        val header = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val icon = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        header.addView(icon, LayoutParams(dp(context, 48), dp(context, 48)))
        ImageLoader.load(ad.iconUrl, icon)

        val texts = LinearLayout(context).apply {
            orientation = VERTICAL
            setPadding(dp(context, 10), 0, 0, 0)
        }
        val titleRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        titleRow.addView(TextView(context).apply {
            text = "Ad"
            setTextColor(Color.WHITE)
            textSize = 10f
            setPadding(dp(context, 5), dp(context, 1), dp(context, 5), dp(context, 1))
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#FFA000"))
                cornerRadius = dp(context, 4).toFloat()
            }
        })
        titleRow.addView(TextView(context).apply {
            text = ad.title
            setTextColor(Color.BLACK)
            textSize = 15f
            maxLines = 1
            setPadding(dp(context, 6), 0, 0, 0)
        })
        texts.addView(titleRow)
        texts.addView(TextView(context).apply {
            text = ad.description
            setTextColor(Color.GRAY)
            textSize = 12f
            maxLines = 2
        })
        header.addView(texts, LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(header, LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        // media image
        if (ad.imageUrl.isNotBlank()) {
            val image = ImageView(context).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                background = GradientDrawable().apply {
                    setColor(Color.parseColor("#EEEEEE"))
                    cornerRadius = dp(context, 8).toFloat()
                }
                clipToOutline = true
            }
            val imageParams = LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 160))
            imageParams.topMargin = dp(context, 10)
            addView(image, imageParams)
            ImageLoader.load(ad.imageUrl, image)
        }

        // button
        val button = TextView(context).apply {
            text = ad.buttonText
            setTextColor(Color.WHITE)
            textSize = 14f
            gravity = Gravity.CENTER
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E88E5"))
                cornerRadius = dp(context, 22).toFloat()
            }
            setOnClickListener { open() }
        }
        val buttonParams = LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 44))
        buttonParams.topMargin = dp(context, 10)
        addView(button, buttonParams)

        setOnClickListener { open() }
        listener?.onAdInfo(ad.title, ad.description, ad.id, ad.iconUrl)
        listener?.onAdLoaded()
    }
}