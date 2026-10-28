package com.example.adssdk

object AdIds {
    // ---------- your own ads (ads.json) ----------
    const val CONFIG_URL = "https://raw.githubusercontent.com/URMIIV/ads-sdk/main/ads.json"

    const val BANNER_HOME = "banner_home"
    const val NATIVE_HOME = "native_home"
    const val INTERSTITIAL_NEXT = "interstitial_next"

    // ---------- which SDK shows the ads ----------
    // OWN             = only your ads.json ads
    // GOOGLE          = only Google AdMob
    // OWN_THEN_GOOGLE = your ad first, Google if you have none
    // GOOGLE_THEN_OWN = Google first, your ad if Google fails
    // This is only the DEFAULT. ads.json ("defaultSource" / "sources") can override it online.
    val DEFAULT_SOURCE = AdSource.GOOGLE_THEN_OWN

    // ---------- Google AdMob ----------
    // Paste your LIVE AdMob ad unit ids here (AdMob > Apps > Ad units).
    // While GOOGLE_TEST_MODE = true the SDK ignores these and shows Google TEST ads.
    val GOOGLE_LIVE = GoogleAdIds(
        banner = "ca-app-pub-0000000000000000/0000000001",
        interstitial = "ca-app-pub-0000000000000000/0000000002",
        native = "ca-app-pub-0000000000000000/0000000003",
        rewarded = "ca-app-pub-0000000000000000/0000000004",
        appOpen = "ca-app-pub-0000000000000000/0000000005"
    )

    // true  = Google test ads (use while developing)
    // false = your live ids above (release)
    const val GOOGLE_TEST_MODE = true
}
