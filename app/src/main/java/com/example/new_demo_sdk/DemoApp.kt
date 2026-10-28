package com.example.new_demo_sdk

import android.app.Application
import com.example.adssdk.AdSdk
import com.example.adssdk.Ads
import com.example.adssdk.GoogleAds

class DemoApp : Application() {
    override fun onCreate() {
        super.onCreate()

        // gap between two full screen ads (own = AdSdk, Google = GoogleAds)
        AdSdk.minIntervalMs = 5_000L
        GoogleAds.minIntervalMs = 0L

        // Your own XML placeholders shown (with shimmer) while ads load. 0 = built-in grey shimmer.
        Ads.bannerShimmerLayout = R.layout.shimmer_banner
        Ads.nativeShimmerLayout = R.layout.shimmer_native
        Ads.shimmerMinMs = 3_000L

        // One init for BOTH: your own ads (ads.json) + Google AdMob
        Ads.init(
            context = this,
            configUrl = AdIds.CONFIG_URL,
            googleIds = AdIds.GOOGLE_LIVE,
            googleTestMode = AdIds.GOOGLE_TEST_MODE,
            defaultSource = AdIds.DEFAULT_SOURCE
        )
    }
}