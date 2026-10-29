package com.example.adssdk

import android.app.Activity
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import com.google.ads.mediation.admob.AdMobAdapter
import com.google.android.gms.ads.AdError
import com.google.android.gms.ads.AdLoader
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.FullScreenContentCallback
import com.google.android.gms.ads.LoadAdError
import com.google.android.gms.ads.MobileAds
import com.google.android.gms.ads.OnUserEarnedRewardListener
import com.google.android.gms.ads.appopen.AppOpenAd
import com.google.android.gms.ads.interstitial.InterstitialAd
import com.google.android.gms.ads.interstitial.InterstitialAdLoadCallback
import com.google.android.gms.ads.nativead.MediaView
import com.google.android.gms.ads.nativead.NativeAd
import com.google.android.gms.ads.rewarded.RewardedAd
import com.google.android.gms.ads.rewarded.RewardedAdLoadCallback
import com.google.android.gms.ads.AdListener as GmsAdListener
import com.google.android.gms.ads.nativead.NativeAdView as GmsNativeAdView

/**
 * Google AdMob ads (banner, native, interstitial, rewarded, app open).
 *
 * 1) Application.onCreate():
 *      GoogleAds.init(this, GoogleAdIds(banner = "...", interstitial = "...", ...), testMode = true)
 *    testMode = true  -> Google TEST ads are shown, your live ids are NOT used.
 *    testMode = false -> your live ids are used.
 *
 * 2) Add your AdMob App ID to the app AndroidManifest (meta-data
 *    com.google.android.gms.ads.APPLICATION_ID). Without it the app crashes.
 */
object GoogleAds {

    private const val TAG = "urmii"

    /** Your ad unit ids (live). Can be changed any time before loading an ad. */
    @JvmStatic
    var ids: GoogleAdIds = GoogleAdIds()

    /** true = always use Google TEST ids. Turn off only for release builds. */
    @JvmStatic
    var testMode: Boolean = false

    /** Minimum gap between two interstitials (milliseconds). 0 = no limit. */
    @JvmStatic
    var minIntervalMs: Long = 0L

    private var interstitial: InterstitialAd? = null
    private var rewarded: RewardedAd? = null
    private var appOpen: AppOpenAd? = null
    private val nativeAds = ArrayList<NativeAd>()
    private var lastInterstitialShown = 0L

    // placement (unitId) of the ad that is loaded now, so a reload uses the same Google id
    private var interstitialUnit = ""
    private var rewardedUnit = ""
    private var appOpenUnit = ""

    // ------------------------------------------------------------------ init

    @JvmStatic
    @JvmOverloads
    fun init(
        context: Context,
        adIds: GoogleAdIds,
        testMode: Boolean = false,
        onInitialized: (() -> Unit)? = null
    ) {
        this.ids = adIds
        this.testMode = testMode
        if (testMode) {
            Log.w(TAG, "GoogleAds: TEST MODE is ON -> Google test ads, live ids are not used")
        }
        MobileAds.initialize(context.applicationContext) {
            Log.d(TAG, "GoogleAds: initialized")
            onInitialized?.invoke()
        }
    }

    /**
     * Which Google ad unit id is used (highest priority first):
     *   testMode                        -> Google TEST id (everything below is ignored)
     *   ads.json google.units[unitId]   -> one id for one placement, e.g. "banner_home"
     *   ads.json google.ids.<format>    -> one id for a format, e.g. banner
     *   app code GoogleAds.ids          -> GOOGLE_LIVE in AdIds.kt (also used if ads.json is not loaded yet)
     */
    private fun adId(unitId: String, pick: (GoogleAdIds) -> String): String {
        if (isTest) return pick(GoogleAdIds.TEST)

        val perUnit = AdRepository.googleUnits[unitId]
        if (!perUnit.isNullOrBlank()) {
            Log.d(TAG, "GoogleAds: id for '$unitId' comes from ads.json: $perUnit")
            return perUnit
        }
        val perFormat = pick(AdRepository.googleIds)
        if (perFormat.isNotBlank()) {
            Log.d(TAG, "GoogleAds: id for format comes from ads.json: $perFormat")
            return perFormat
        }
        return pick(ids)
    }

    /** Debug: the Google id that WILL be used for this unit. format = banner/native/interstitial/rewarded/appOpen. */
    @JvmStatic
    fun idFor(unitId: String, format: String): String = adId(unitId) {
        when (format) {
            "banner" -> it.banner
            "native" -> it.native
            "interstitial" -> it.interstitial
            "rewarded" -> it.rewarded
            else -> it.appOpen
        }
    }

    /** ads.json google.testMode (change online) wins over the value given in code. */
    val isTest: Boolean
        get() = AdRepository.googleTestMode ?: testMode

    private fun request() = AdRequest.Builder().build()

    private fun fail(listener: AdListener?, message: String) {
        Log.e(TAG, "GoogleAds: $message")
        listener?.onAdError(message)
        listener?.onAdFailed()
    }

    // ---------------------------------------------------------------- banner

    /** Loads a banner into [container]. Returns the AdView (call pause/resume/destroy if you want). */
    @JvmStatic
    @JvmOverloads
    fun loadBanner(
        activity: Activity,
        container: ViewGroup,
        listener: AdListener? = null,
        adSize: AdSize = AdSize.BANNER,
        unitId: String = "",
        collapsible: Boolean = false
    ): AdView? {
        val id = adId(unitId) { it.banner }
        if (id.isBlank()) {
            fail(listener, "banner id is empty")
            return null
        }
        Log.d(TAG, "GoogleAds: banner [$unitId] loading with id $id (${if (isTest) "TEST" else "LIVE"} id)")
        listener?.onAdUnitId(id)
        val adView = AdView(activity)
        // collapsible banner needs an anchored adaptive size (full width)
        val size = if (collapsible) {
            val dm = activity.resources.displayMetrics
            AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(
                activity, (dm.widthPixels / dm.density).toInt()
            )
        } else adSize
        adView.setAdSize(size)
        adView.adUnitId = id
        adView.adListener = object : GmsAdListener() {
            override fun onAdLoaded() {
                val info = adView.responseInfo
                Log.d(TAG, "GoogleAds: banner loaded, adapter=${info?.mediationAdapterClassName}")
                listener?.onAdInfo("Google banner", "rendered by ${info?.mediationAdapterClassName ?: "AdMob"}", info?.responseId.orEmpty(), "")
                listener?.onAdLoaded()
            }

            override fun onAdFailedToLoad(error: LoadAdError) {
                fail(listener, "banner failed: ${error.message}")
            }

            override fun onAdClicked() {
                listener?.onAdClicked()
            }

            override fun onAdClosed() {
                listener?.onAdClosed()
            }
        }
        container.removeAllViews()
        container.addView(adView)
        if (collapsible) {
            // "bottom" = the banner is at the bottom of the screen and expands upwards
            val extras = Bundle().apply { putString("collapsible", "bottom") }
            adView.loadAd(
                AdRequest.Builder()
                    .addNetworkExtrasBundle(AdMobAdapter::class.java, extras)
                    .build()
            )
        } else {
            adView.loadAd(request())
        }
        return adView
    }

    // ---------------------------------------------------------- interstitial

    @JvmStatic
    @JvmOverloads
    fun loadInterstitial(context: Context, listener: AdListener? = null, unitId: String = "") {
        interstitialUnit = unitId
        val id = adId(unitId) { it.interstitial }
        if (id.isBlank()) {
            fail(listener, "interstitial id is empty")
            return
        }
        Log.d(TAG, "GoogleAds: interstitial [$unitId] loading with id $id (${if (isTest) "TEST" else "LIVE"} id)")
        listener?.onAdUnitId(id)
        InterstitialAd.load(
            context.applicationContext, id, request(),
            object : InterstitialAdLoadCallback() {
                override fun onAdLoaded(ad: InterstitialAd) {
                    interstitial = ad
                    Log.d(TAG, "GoogleAds: interstitial loaded")
                    listener?.onAdLoaded()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    interstitial = null
                    fail(listener, "interstitial failed: ${error.message}")
                }
            })
    }

    @JvmStatic
    fun isInterstitialReady(): Boolean = interstitial != null

    /** onClosed is ALWAYS called (after the ad closes, or at once if no ad is ready). */
    @JvmStatic
    @JvmOverloads
    fun showInterstitial(activity: Activity, onClosed: () -> Unit = {}) {
        val ad = interstitial
        val now = System.currentTimeMillis()

        if (ad == null) {
            Log.w(TAG, "GoogleAds: interstitial not ready, loading for next time")
            loadInterstitial(activity, null, interstitialUnit)
            onClosed()
            return
        }
        if (now - lastInterstitialShown < minIntervalMs) {
            onClosed()
            return
        }

        lastInterstitialShown = now
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                interstitial = null
                loadInterstitial(activity, null, interstitialUnit)
                onClosed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e(TAG, "GoogleAds: interstitial show failed: ${error.message}")
                interstitial = null
                loadInterstitial(activity, null, interstitialUnit)
                onClosed()
            }
        }
        ad.show(activity)
    }

    // -------------------------------------------------------------- rewarded

    @JvmStatic
    @JvmOverloads
    fun loadRewarded(context: Context, listener: AdListener? = null, unitId: String = "") {
        rewardedUnit = unitId
        val id = adId(unitId) { it.rewarded }
        if (id.isBlank()) {
            fail(listener, "rewarded id is empty")
            return
        }
        RewardedAd.load(
            context.applicationContext, id, request(),
            object : RewardedAdLoadCallback() {
                override fun onAdLoaded(ad: RewardedAd) {
                    rewarded = ad
                    Log.d(TAG, "GoogleAds: rewarded loaded")
                    listener?.onAdLoaded()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    rewarded = null
                    fail(listener, "rewarded failed: ${error.message}")
                }
            })
    }

    @JvmStatic
    fun isRewardedReady(): Boolean = rewarded != null

    /**
     * onReward(amount) is called ONLY if the user earned the reward.
     * onClosed is always called (also when no ad is ready).
     */
    @JvmStatic
    @JvmOverloads
    fun showRewarded(
        activity: Activity,
        onReward: (Int) -> Unit,
        onClosed: () -> Unit = {}
    ) {
        val ad = rewarded
        if (ad == null) {
            Log.w(TAG, "GoogleAds: rewarded not ready, loading for next time")
            loadRewarded(activity, null, rewardedUnit)
            onClosed()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                rewarded = null
                loadRewarded(activity, null, rewardedUnit)
                onClosed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e(TAG, "GoogleAds: rewarded show failed: ${error.message}")
                rewarded = null
                loadRewarded(activity, null, rewardedUnit)
                onClosed()
            }
        }
        ad.show(activity, OnUserEarnedRewardListener { reward -> onReward(reward.amount) })
    }

    // -------------------------------------------------------------- app open

    @JvmStatic
    @JvmOverloads
    fun loadAppOpen(context: Context, listener: AdListener? = null, unitId: String = "") {
        appOpenUnit = unitId
        val id = adId(unitId) { it.appOpen }
        if (id.isBlank()) {
            fail(listener, "app open id is empty")
            return
        }
        AppOpenAd.load(
            context.applicationContext, id, request(),
            object : AppOpenAd.AppOpenAdLoadCallback() {
                override fun onAdLoaded(ad: AppOpenAd) {
                    appOpen = ad
                    Log.d(TAG, "GoogleAds: app open loaded")
                    listener?.onAdLoaded()
                }

                override fun onAdFailedToLoad(error: LoadAdError) {
                    appOpen = null
                    fail(listener, "app open failed: ${error.message}")
                }
            })
    }

    @JvmStatic
    fun isAppOpenReady(): Boolean = appOpen != null

    /** onClosed is ALWAYS called. */
    @JvmStatic
    @JvmOverloads
    fun showAppOpen(activity: Activity, onClosed: () -> Unit = {}) {
        val ad = appOpen
        if (ad == null) {
            Log.w(TAG, "GoogleAds: app open not ready, loading for next time")
            loadAppOpen(activity, null, appOpenUnit)
            onClosed()
            return
        }
        ad.fullScreenContentCallback = object : FullScreenContentCallback() {
            override fun onAdDismissedFullScreenContent() {
                appOpen = null
                loadAppOpen(activity, null, appOpenUnit)
                onClosed()
            }

            override fun onAdFailedToShowFullScreenContent(error: AdError) {
                Log.e(TAG, "GoogleAds: app open show failed: ${error.message}")
                appOpen = null
                loadAppOpen(activity, null, appOpenUnit)
                onClosed()
            }
        }
        ad.show(activity)
    }

    // ---------------------------------------------------------------- native

    /** Loads a native ad card (built by the SDK, no XML needed) into [container]. */
    @JvmStatic
    @JvmOverloads
    fun loadNative(
        activity: Activity,
        container: ViewGroup,
        listener: AdListener? = null,
        unitId: String = ""
    ) {
        val id = adId(unitId) { it.native }
        if (id.isBlank()) {
            fail(listener, "native id is empty")
            return
        }
        Log.d(TAG, "GoogleAds: native [$unitId] loading with id $id (${if (isTest) "TEST" else "LIVE"} id)")
        listener?.onAdUnitId(id)
        AdLoader.Builder(activity, id)
            .forNativeAd { ad ->
                if (activity.isDestroyed) {
                    ad.destroy()
                    return@forNativeAd
                }
                nativeAds.add(ad)
                container.removeAllViews()
                container.addView(buildNativeView(activity, ad))
                Log.d(TAG, "GoogleAds: native loaded '${ad.headline}'")
                listener?.onAdInfo(ad.headline.orEmpty(), ad.body.orEmpty(), ad.responseInfo?.responseId.orEmpty(), ad.icon?.uri?.toString().orEmpty())
                listener?.onAdLoaded()
            }
            .withAdListener(object : GmsAdListener() {
                override fun onAdFailedToLoad(error: LoadAdError) {
                    fail(listener, "native failed: ${error.message}")
                }

                override fun onAdClicked() {
                    listener?.onAdClicked()
                }
            })
            .build()
            .loadAd(request())
    }

    private fun buildNativeView(context: Context, ad: NativeAd): GmsNativeAdView {
        val root = GmsNativeAdView(context)
        root.setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12))
        root.background = GradientDrawable().apply {
            setColor(Color.WHITE)
            cornerRadius = dp(context, 12).toFloat()
            setStroke(dp(context, 1), Color.parseColor("#DDDDDD"))
        }

        val column = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }

        // header: icon + (Ad badge + headline, body)
        val header = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val icon = ImageView(context).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        val iconDrawable = ad.icon?.drawable
        if (iconDrawable != null) {
            icon.setImageDrawable(iconDrawable)
            header.addView(icon, LinearLayout.LayoutParams(dp(context, 48), dp(context, 48)))
        }

        val texts = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(context, 10), 0, 0, 0)
        }
        val titleRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
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
        val headline = TextView(context).apply {
            text = ad.headline
            setTextColor(Color.BLACK)
            textSize = 15f
            maxLines = 1
            setPadding(dp(context, 6), 0, 0, 0)
        }
        titleRow.addView(headline)
        texts.addView(titleRow)

        val body = TextView(context).apply {
            text = ad.body
            setTextColor(Color.GRAY)
            textSize = 12f
            maxLines = 2
            visibility = if (ad.body.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        texts.addView(body)
        header.addView(
            texts,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        )
        column.addView(
            header,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // media (image / video)
        val media = MediaView(context)
        val mediaContent = ad.mediaContent
        if (mediaContent != null) {
            media.setMediaContent(mediaContent)
            val mediaParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 180)
            )
            mediaParams.topMargin = dp(context, 10)
            column.addView(media, mediaParams)
        }

        // call to action
        val cta = Button(context).apply {
            text = ad.callToAction
            setTextColor(Color.WHITE)
            textSize = 14f
            isAllCaps = false
            background = GradientDrawable().apply {
                setColor(Color.parseColor("#1E88E5"))
                cornerRadius = dp(context, 22).toFloat()
            }
            visibility = if (ad.callToAction.isNullOrBlank()) View.GONE else View.VISIBLE
        }
        val ctaParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT, dp(context, 44)
        )
        ctaParams.topMargin = dp(context, 10)
        column.addView(cta, ctaParams)

        root.addView(
            column,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        )

        // register assets for Google
        root.headlineView = headline
        root.bodyView = body
        root.callToActionView = cta
        if (iconDrawable != null) root.iconView = icon
        if (mediaContent != null) root.mediaView = media
        root.setNativeAd(ad)
        return root
    }

    // --------------------------------------------------------------- cleanup

    /** Call in onDestroy() of the screen that showed native ads. */
    @JvmStatic
    fun destroyNativeAds() {
        nativeAds.forEach { it.destroy() }
        nativeAds.clear()
    }
}