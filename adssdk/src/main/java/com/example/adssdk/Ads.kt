package com.example.adssdk

import android.app.Activity
import android.content.Context
import android.util.Log
import android.view.ViewGroup
import com.google.android.gms.ads.AdView
import java.util.WeakHashMap

/**
 * ONE entry point for ads. It decides if the ad comes from YOUR OWN SDK (ads.json)
 * or from GOOGLE AdMob, and can fall back from one to the other.
 *
 * Which source is used for an ad unit (highest priority first):
 *   1) ads.json  ->  "sources": { "banner_home": "own" }        (change online, no app update)
 *   2) ads.json  ->  "defaultSource": "google_then_own"
 *   3) app code  ->  Ads.defaultSource / Ads.init(..., defaultSource = ...)
 *
 * Setup (Application.onCreate):
 *   Ads.init(this, CONFIG_URL, googleIds, googleTestMode, AdSource.GOOGLE_THEN_OWN)
 *
 * Use:
 *   Ads.loadBanner(this, container, "banner_home")
 *   Ads.loadNative(this, container, "native_home")
 *   Ads.preloadInterstitial(this, "interstitial_next")
 *   Ads.showInterstitial(this, "interstitial_next") { openNext() }
 *
 * Google ad unit ids can ALSO come from ads.json (change online):
 *   "google": { "ids": { "banner": "ca-app-pub-.../..." },        // one id per format
 *               "units": { "banner_home": "ca-app-pub-.../..." } } // one id per placement
 * Priority: google.units[unit] > google.ids[format] > ids given in Ads.init (app code).
 * Test mode (googleTestMode = true) ignores all of them and shows Google TEST ads.
 *
 * Rewarded and App Open ads exist only in Google AdMob -> keep using GoogleAds for those.
 */
object Ads {

    private const val TAG = "urmii"

    /** Used when ads.json does not say anything about the unit. */
    @JvmStatic
    var defaultSource: AdSource = AdSource.GOOGLE_THEN_OWN

    /** How long the loading shimmer is shown at least (milliseconds). Set 0 to show the ad at once. */
    @JvmStatic
    var shimmerMinMs: Long
        get() = Shimmer.minDurationMs
        set(value) { Shimmer.minDurationMs = value }

    /** Your own XML placeholder shown while a banner loads (0 = built-in shimmer). */
    @JvmStatic
    var bannerShimmerLayout: Int = 0

    /** Your own XML placeholder shown while a native ad loads (0 = built-in shimmer). */
    @JvmStatic
    var nativeShimmerLayout: Int = 0

    private val banners = WeakHashMap<ViewGroup, AdView>()

    // ------------------------------------------------------------------ init

    @JvmStatic
    @JvmOverloads
    fun init(
        context: Context,
        configUrl: String,
        googleIds: GoogleAdIds,
        googleTestMode: Boolean = false,
        defaultSource: AdSource = AdSource.GOOGLE_THEN_OWN
    ) {
        Log.d(TAG, "Ads: init called with configUrl=$configUrl, defaultSource=$defaultSource, googleTestMode=$googleTestMode")
        this.defaultSource = defaultSource
        AdSdk.init(context, configUrl)
        GoogleAds.init(context = context, adIds = googleIds, testMode = googleTestMode)
    }

    /** The source that will really be used for this unit right now. */
    @JvmStatic
    fun sourceFor(unitId: String): AdSource =
        AdRepository.sources[unitId] ?: AdRepository.defaultSource ?: defaultSource

    /** Debug text: which source + which Google id this unit will use, e.g. for the on-screen log. */
    @JvmStatic
    fun describe(unitId: String, format: String): String =
        "$unitId -> ${sourceFor(unitId)} (${sourceFor(unitId).chain().joinToString(" > ")}), " +
                "google id: ${GoogleAds.idFor(unitId, format).ifBlank { "none" }} [${if (GoogleAds.isTest) "TEST" else "LIVE"}]"

    // ---------------------------------------------------------------- banner

    @JvmStatic
    @JvmOverloads
    fun loadBanner(
        activity: Activity,
        container: ViewGroup,
        unitId: String = "",
        listener: AdListener? = null,
        shimmerLayout: Int = 0
    ) {
        Log.d(TAG, "Ads: loadBanner called for unitId='$unitId', sourceChain=${sourceFor(unitId).chain()}")
        banners.remove(container)?.destroy()
        Shimmer.start(container, 60, if (shimmerLayout != 0) shimmerLayout else bannerShimmerLayout)

        runChain(
            label = "banner:$unitId",
            chain = sourceFor(unitId).chain(),
            index = 0,
            listener = stopShimmerOn(container, listener),
            onAllFailed = { container.removeAllViews() }
        ) { source, l ->
            if (source == AdSource.GOOGLE) {
                GoogleAds.loadBanner(activity, container, l, unitId = unitId)?.let { banners[container] = it }
            } else {
                AdSdk.loadBanner(container, unitId, l)
            }
        }
    }

    /** Used by the XML BannerAdView: the view itself hosts the own ad OR the Google banner. */
    internal fun loadBannerInto(
        activity: Activity,
        view: BannerAdView,
        unitId: String,
        listener: AdListener?
    ) {
        Log.d(TAG, "Ads: loadBannerInto view for unitId='$unitId', sourceChain=${sourceFor(unitId).chain()}")
        banners.remove(view)?.destroy()
        view.visibility = android.view.View.VISIBLE
        Shimmer.start(view, 60, if (view.shimmerLayout != 0) view.shimmerLayout else bannerShimmerLayout)

        runChain(
            label = "banner:$unitId",
            chain = sourceFor(unitId).chain(),
            index = 0,
            listener = stopShimmerOn(view, listener),
            onAllFailed = { view.removeAllViews(); view.visibility = android.view.View.GONE }
        ) { source, l ->
            if (source == AdSource.GOOGLE) {
                view.prepareForGoogle()
                val adView = GoogleAds.loadBanner(activity, view, l, unitId = unitId)
                if (adView != null) banners[view] = adView
                // no id -> GoogleAds already reported onAdFailed -> chain moves to own
            } else {
                view.loadOwn(unitId, l)
            }
        }
    }

    // ---------------------------------------------------------------- native

    @JvmStatic
    @JvmOverloads
    fun loadNative(
        activity: Activity,
        container: ViewGroup,
        unitId: String = "",
        listener: AdListener? = null,
        shimmerLayout: Int = 0
    ) {
        Log.d(TAG, "Ads: loadNative called for unitId='$unitId', sourceChain=${sourceFor(unitId).chain()}")
        Shimmer.start(container, 260, if (shimmerLayout != 0) shimmerLayout else nativeShimmerLayout)

        runChain(
            label = "native:$unitId",
            chain = sourceFor(unitId).chain(),
            index = 0,
            listener = stopShimmerOn(container, listener),
            onAllFailed = { }
        ) { source, l ->
            if (source == AdSource.GOOGLE) {
                GoogleAds.loadNative(activity, container, l, unitId)
            } else {
                AdSdk.loadNative(container, unitId, l)
            }
        }
    }

    // ---------------------------------------------------------- interstitial

    /** Call early (onCreate) so a Google interstitial is ready when you need it. */
    @JvmStatic
    @JvmOverloads
    fun preloadInterstitial(context: Context, unitId: String = "", listener: AdListener? = null) {
        Log.d(TAG, "Ads: preloadInterstitial called for unitId='$unitId', sourceChain=${sourceFor(unitId).chain()}")
        if (AdSource.GOOGLE !in sourceFor(unitId).chain()) return
        if (GoogleAds.isInterstitialReady()) {
            Log.d(TAG, "Ads: preloadInterstitial - Google interstitial already ready")
            listener?.onAdLoaded()
            return
        }
        GoogleAds.loadInterstitial(context, listener, unitId)
    }

    /**
     * Shows the first source that has an ad ready. onClosed is ALWAYS called
     * (also when no source has an ad).
     */
    @JvmStatic
    @JvmOverloads
    fun showInterstitial(activity: Activity, unitId: String = "", onClosed: () -> Unit = {}) {
        val chain = sourceFor(unitId).chain()
        Log.d(TAG, "Ads: showInterstitial called for unitId='$unitId', sourceChain=$chain")

        for (source in chain) {
            if (source == AdSource.GOOGLE) {
                if (GoogleAds.isInterstitialReady()) {
                    Log.d(TAG, "Ads: [interstitial:$unitId] SHOWING from GOOGLE (Google AdMob)")
                    GoogleAds.showInterstitial(activity, onClosed)
                    return
                }
            } else {
                if (AdSdk.tryShowInterstitial(activity, unitId, onClosed)) {
                    Log.d(TAG, "Ads: [interstitial:$unitId] SHOWING from OWN (own SDK / ads.json)")
                    return
                }
            }
            Log.w(TAG, "Ads: interstitial from $source not available")
        }

        Log.w(TAG, "Ads: [interstitial:$unitId] nothing to show (chain=$chain)")
        // nothing to show: get Google ready for next time and continue
        if (AdSource.GOOGLE in chain) GoogleAds.loadInterstitial(activity, null, unitId)
        onClosed()
    }

    // ------------------------------------------------------------- lifecycle
    // Only needed for Google banners. Call from the Activity that loaded the banner.

    @JvmStatic
    fun onPause(activity: Activity) {
        banners.values.filter { it.context === activity }.forEach { it.pause() }
    }

    @JvmStatic
    fun onResume(activity: Activity) {
        banners.values.filter { it.context === activity }.forEach { it.resume() }
    }

    /**
     * Call in onDestroy(). Set destroyNative = true in the screen that loaded a native ad.
     */
    @JvmStatic
    @JvmOverloads
    fun onDestroy(activity: Activity, destroyNative: Boolean = false) {
        val iterator = banners.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            if (entry.value.context === activity) {
                entry.value.destroy()
                iterator.remove()
            }
        }
        if (destroyNative) GoogleAds.destroyNativeAds()
    }

    // --------------------------------------------------------------- helpers

    /** Removes the shimmer placeholder when the ad is loaded or when every source failed. */
    private fun stopShimmerOn(view: android.view.View, listener: AdListener?) = object : AdListener {
        override fun onAdSource(source: AdSource) { listener?.onAdSource(source) }
        override fun onAdUnitId(id: String) { listener?.onAdUnitId(id) }
        override fun onAdInfo(name: String, subtitle: String, id: String, iconUrl: String) { listener?.onAdInfo(name, subtitle, id, iconUrl) }
        override fun onAdLoaded() { Shimmer.stopAfterMin(view); listener?.onAdLoaded() }
        override fun onAdFailed() { Shimmer.stopAfterMin(view); listener?.onAdFailed() }
        override fun onAdClicked() { listener?.onAdClicked() }
        override fun onAdClosed() { listener?.onAdClosed() }
        override fun onAdError(message: String) { listener?.onAdError(message) }
    }

    /** Tries chain[index]; if it fails, tries chain[index + 1] ... ; at the end tells the listener. */
    private fun runChain(
        label: String,
        chain: List<AdSource>,
        index: Int,
        listener: AdListener?,
        onAllFailed: () -> Unit,
        load: (AdSource, AdListener) -> Unit
    ) {
        var handled = false
        load(chain[index], object : AdListener {
            override fun onAdUnitId(id: String) {
                listener?.onAdUnitId(id)
            }

            override fun onAdInfo(name: String, subtitle: String, id: String, iconUrl: String) {
                Log.d(TAG, "Ads: [$label] ad title='$name' subtitle='$subtitle' id=$id icon=$iconUrl")
                listener?.onAdInfo(name, subtitle, id, iconUrl)
            }

            override fun onAdLoaded() {
                Log.d(TAG, "Ads: [$label] LOADED from ${chain[index]} (${if (chain[index] == AdSource.GOOGLE) "Google AdMob" else "own SDK / ads.json"})")
                listener?.onAdSource(chain[index])
                listener?.onAdLoaded()
            }

            override fun onAdClicked() {
                listener?.onAdClicked()
            }

            override fun onAdClosed() {
                listener?.onAdClosed()
            }

            override fun onAdError(message: String) {
                listener?.onAdError(message)
            }

            override fun onAdFailed() {
                if (handled) return
                handled = true
                if (index < chain.lastIndex) {
                    Log.w(TAG, "Ads: ${chain[index]} failed -> trying ${chain[index + 1]}")
                    runChain(label, chain, index + 1, listener, onAllFailed, load)
                } else {
                    Log.e(TAG, "Ads: [$label] FAILED, no source had an ad (chain=$chain)")
                    onAllFailed()
                    listener?.onAdFailed()
                }
            }
        })
    }
}