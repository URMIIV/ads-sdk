package com.example.adssdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup

object AdSdk {

    /** Minimum gap between two full screen ads (milliseconds). */
    @JvmStatic
    var minIntervalMs: Long = 30_000L

    private var lastShown = 0L

    /**
     * Call once in Application.onCreate().
     * configUrl = link of your ads.json file.
     */
    @JvmStatic
    fun init(context: Context, configUrl: String) {
        AdRepository.load(context, configUrl)
    }

    @JvmStatic
    fun isReady(): Boolean = AdRepository.ads.isNotEmpty()

    /**
     * BANNER (small bar). Put it in any layout container.
     * Example: AdSdk.loadBanner(binding.adContainer, AdIds.BANNER_HOME)
     */
    @JvmStatic
    @JvmOverloads
    fun loadBanner(container: ViewGroup, unitId: String = "", listener: AdListener? = null) {
        container.removeAllViews()
        val banner = BannerAdView(container.context)
        container.addView(
            banner,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        banner.load(unitId, listener)
    }

    /**
     * NATIVE (big card with image). Put it in any layout container.
     * Example: AdSdk.loadNative(binding.nativeContainer, AdIds.NATIVE_LIST)
     */
    @JvmStatic
    @JvmOverloads
    fun loadNative(container: ViewGroup, unitId: String = "", listener: AdListener? = null) {
        container.removeAllViews()
        val native = NativeAdView(container.context)
        container.addView(
            native,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )
        native.load(unitId, listener)
    }

    /**
     * FULL SCREEN (interstitial). onClosed is always called (even if no ad is available).
     * Example: AdSdk.showInterstitial(this, AdIds.INTERSTITIAL_APPLY) { openNext() }
     */
    @JvmStatic
    @JvmOverloads
    fun showInterstitial(activity: Activity, unitId: String = "", onClosed: () -> Unit = {}) {
        val ad = AdRepository.random()
        val now = System.currentTimeMillis()

        if (ad == null || now - lastShown < minIntervalMs) {
            onClosed()
            return
        }

        lastShown = now
        InterstitialAdActivity.callback = onClosed
        activity.startActivity(
            Intent(activity, InterstitialAdActivity::class.java).putExtra("ad_id", ad.id)
        )
    }

    internal fun openAd(context: Context, ad: AdModel) {
        val link = ad.clickUrl
        if (link.isBlank()) return

        val uri = if (link.contains("://")) Uri.parse(link)
        else Uri.parse("market://details?id=$link")

        try {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        } catch (e: Exception) {
            if (!link.contains("://")) {
                try {
                    context.startActivity(
                        Intent(
                            Intent.ACTION_VIEW,
                            Uri.parse("https://play.google.com/store/apps/details?id=$link")
                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    )
                } catch (ignored: Exception) {
                }
            }
        }
    }
}

internal fun dp(context: Context, value: Int): Int =
    (value * context.resources.displayMetrics.density).toInt()


