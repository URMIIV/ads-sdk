package com.example.adssdk

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
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

    /** Your own XML placeholder shown while the ad loads (0 = Ads.bannerShimmerLayout / built-in). */
    var shimmerLayout: Int = 0

    init {
        // reads app:shimmerLayout without needing the generated R.styleable
        if (attrs != null) {
            shimmerLayout = attrs.getAttributeResourceValue(
                "http://schemas.android.com/apk/res-auto", "shimmerLayout", 0
            )
        }
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

    private val ownBackground = background

    /**
     * Loads an ad. Call this if you add BannerAdView from XML.
     * Follows the same source rules as [Ads] (own / google / fallback), so this ONE view
     * can show your own ad or a Google ad. Without an Activity context it shows own ads only.
     */
    @JvmOverloads
    fun load(unitId: String = "", listener: AdListener? = null) {
        val activity = findActivity()
        if (activity == null) loadOwn(unitId, listener) else Ads.loadBannerInto(activity, this, unitId, listener)
    }

    /** Own (ads.json) ad only. */
    internal fun loadOwn(unitId: String, listener: AdListener?) {
        background = ownBackground
        setPadding(dp(context, 10), dp(context, 6), dp(context, 10), dp(context, 6))
        minimumHeight = dp(context, 60)
        bind(unitId, listener, true)
    }

    /** Prepares this view to host a Google AdView (no card look). */
    internal fun prepareForGoogle() {
        setOnClickListener(null)
        isClickable = false
        background = null
        minimumHeight = 0
        setPadding(0, 0, 0, 0)
        gravity = Gravity.CENTER
        removeAllViews()
        visibility = View.VISIBLE
    }

    private fun findActivity(): Activity? {
        var c: Context? = context
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }

    private fun bind(unitId: String, listener: AdListener?, retry: Boolean) {
        val ad = AdRepository.random(unitId.ifBlank { null })

        if (ad == null) {
            if (!retry) visibility = View.GONE
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
        val titleRow = LinearLayout(context).apply {
            orientation = HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        // same "Ad" badge as Google ads
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
            textSize = 14f
            maxLines = 1
            setPadding(dp(context, 6), 0, 0, 0)
        })
        texts.addView(titleRow)
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
        listener?.onAdInfo(ad.title, ad.description, ad.id, ad.iconUrl)
        listener?.onAdLoaded()
    }
}