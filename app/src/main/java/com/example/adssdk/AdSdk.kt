package com.example.adssdk

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.view.ViewGroup

/**
 * YOUR OWN ads (ads.json). To mix with Google ads use the [Ads] class instead.
 */
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

    /** Loads an ad icon (http link) into an ImageView, e.g. to show which ad was loaded. */
    @JvmStatic
    fun loadIcon(url: String, view: android.widget.ImageView) = ImageLoader.load(url, view)

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
        banner.loadOwn(unitId, listener)
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
        if (!tryShowInterstitial(activity, unitId, onClosed)) onClosed()
    }

    /**
     * Same as showInterstitial, but tells you if there was an ad.
     *  true  = handled: the ad screen was opened, or it was skipped because of minIntervalMs
     *          (onClosed is called for you in that case).
     *  false = there is NO ad. onClosed is NOT called, so you can try another source.
     */
    @JvmStatic
    @JvmOverloads
    fun tryShowInterstitial(
        activity: Activity,
        unitId: String = "",
        onClosed: () -> Unit = {}
    ): Boolean {
        val ad = AdRepository.random(unitId.ifBlank { null }) ?: return false
        val now = System.currentTimeMillis()

        if (now - lastShown < minIntervalMs) {
            onClosed()
            return true
        }

        lastShown = now
        InterstitialAdActivity.callback = onClosed
        activity.startActivity(
            Intent(activity, InterstitialAdActivity::class.java).putExtra("ad_id", ad.id)
        )
        return true
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