package com.example.adssdk

/**
 * Your AdMob ad unit ids. Leave an id empty ("") if you do not use that format.
 *
 * Example (LIVE ids from your AdMob account):
 *   GoogleAdIds(
 *       banner = "ca-app-pub-1234567890123456/1111111111",
 *       interstitial = "ca-app-pub-1234567890123456/2222222222",
 *       native = "ca-app-pub-1234567890123456/3333333333",
 *       rewarded = "ca-app-pub-1234567890123456/4444444444",
 *       appOpen = "ca-app-pub-1234567890123456/5555555555"
 *   )
 */
data class GoogleAdIds @JvmOverloads constructor(
    val banner: String = "",
    val interstitial: String = "",
    val native: String = "",
    val rewarded: String = "",
    val appOpen: String = ""
) {
    companion object {
        /** Google official TEST ad unit ids (safe to click while developing). */
        @JvmField
        val TEST = GoogleAdIds(
            banner = "ca-app-pub-3940256099942544/6300978111",
            interstitial = "ca-app-pub-3940256099942544/1033173712",
            native = "ca-app-pub-3940256099942544/2247696110",
            rewarded = "ca-app-pub-3940256099942544/5224354917",
            appOpen = "ca-app-pub-3940256099942544/9257395921"
        )

        /** Google TEST AdMob App ID (goes in AndroidManifest meta-data). */
        const val TEST_APP_ID = "ca-app-pub-3940256099942544~3347511713"
    }
}
