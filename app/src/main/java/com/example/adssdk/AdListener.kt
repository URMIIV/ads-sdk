package com.example.adssdk

interface AdListener {
    /** Which source really delivered the ad: OWN (your SDK / ads.json) or GOOGLE (AdMob). */
    fun onAdSource(source: AdSource) {}

    /** The Google AdMob ad unit id used for this ad (only called for Google ads). */
    fun onAdUnitId(id: String) {}

    /** Which ad was loaded: its title, subtitle, id and icon link (icon is empty for Google banners). */
    fun onAdInfo(name: String, subtitle: String, id: String, iconUrl: String) {}

    fun onAdLoaded() {}
    fun onAdFailed() {}
    fun onAdClicked() {}
    fun onAdClosed() {}

    /** Reason text when an ad fails (mainly for Google ads). */
    fun onAdError(message: String) {}
}