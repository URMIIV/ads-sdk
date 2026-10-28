package com.example.new_demo_sdk

import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.graphics.Color
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.example.adssdk.AdListener
import com.example.adssdk.AdSdk
import com.example.adssdk.AdSource
import com.example.adssdk.GoogleAds
import com.example.adssdk.Ads
import com.example.adssdk.BannerAdView

class MainActivity : AppCompatActivity() {

    private lateinit var tvStatus: TextView
    private lateinit var bannerXml: BannerAdView
    private lateinit var bannerContainer: FrameLayout
    private lateinit var nativeContainer: FrameLayout
    private val logLines = ArrayList<String>()
    private lateinit var adInfoPanel: LinearLayout
    private val infoRows = HashMap<String, Pair<ImageView, TextView>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvStatus = findViewById(R.id.tvStatus)
        adInfoPanel = findViewById(R.id.adInfoPanel)
        bannerXml = findViewById(R.id.bannerXml)
        bannerContainer = findViewById(R.id.bannerContainer)
        nativeContainer = findViewById(R.id.nativeContainer)

        findViewById<Button>(R.id.btnInterstitial).setOnClickListener {
            // Uses unified Ads manager (Custom ads first, fallback to Google AdMob IDs)
            log("Interstitial: source = ${Ads.sourceFor(AdIds.INTERSTITIAL_NEXT)}")
            Ads.showInterstitial(this, AdIds.INTERSTITIAL_NEXT) {
                log("Interstitial: closed -> opening next screen")
                startActivity(Intent(this, SecondActivity::class.java))
            }
        }

        findViewById<Button>(R.id.btnReload).setOnClickListener { loadAds() }

        loadAds()
    }

    private fun loadAds() {
        // XML banner = own ads view (Custom SDK)
        bannerXml.load(AdIds.BANNER_HOME, listener("Banner (XML)"))

        // Unified manager: tries Custom ads first (OWN), falls back to Google AdMob IDs if needed
        Ads.loadBanner(this, bannerContainer, AdIds.BANNER_HOME, listener("Banner (code)"))
        Ads.loadNative(this, nativeContainer, AdIds.NATIVE_HOME, listener("Native"))

        Ads.preloadInterstitial(this, AdIds.INTERSTITIAL_NEXT)

        log(Ads.describe(AdIds.BANNER_HOME, "banner"))
        log(Ads.describe(AdIds.NATIVE_HOME, "native"))
        log(Ads.describe(AdIds.INTERSTITIAL_NEXT, "interstitial"))
    }

    /** One row per placement: icon + title + subtitle + which SDK delivered it. */
    private fun showInfo(label: String, source: String, title: String, subtitle: String, iconUrl: String) {
        val row = infoRows.getOrPut(label) {
            val icon = ImageView(this).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
            val text = TextView(this).apply { textSize = 12f; setTextColor(Color.BLACK) }
            val line = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, 6, 0, 6)
            }
            val size = (40 * resources.displayMetrics.density).toInt()
            line.addView(icon, LinearLayout.LayoutParams(size, size))
            line.addView(text, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply { marginStart = 12 })
            adInfoPanel.addView(line)
            icon to text
        }
        row.first.setImageDrawable(null)
        if (iconUrl.isNotBlank()) AdSdk.loadIcon(iconUrl, row.first)
        row.second.text = "$label  [$source]\n$title\n$subtitle"
    }

    private fun listener(label: String) = object : AdListener {
        private var shown = ""
        override fun onAdSource(source: AdSource) {
            shown = if (source == AdSource.GOOGLE) "GOOGLE SDK (AdMob)" else "OWN SDK (ads.json)"
        }
        override fun onAdInfo(name: String, subtitle: String, id: String, iconUrl: String) {
            log("$label: title = '$name' | subtitle = '$subtitle' | id = $id | icon = ${iconUrl.ifBlank { "-" }}")
            showInfo(label, shown, name, subtitle, iconUrl)
        }
        override fun onAdUnitId(id: String) = log("$label: Google id = $id [${if (GoogleAds.isTest) "TEST" else "LIVE"}]")
        override fun onAdLoaded() = log("$label: loaded from $shown")
        override fun onAdFailed() = log("$label: FAILED")
        override fun onAdError(message: String) = log("$label: ERROR $message")
        override fun onAdClicked() = log("$label: clicked")
        override fun onAdClosed() = log("$label: closed")
    }

    private fun log(message: String) {
        Log.d("urmii", message)

        logLines.add(message)
        if (logLines.size > 16) logLines.removeAt(0)
        tvStatus.text = "SDK ready: ${AdSdk.isReady()}\n" + logLines.joinToString("\n")
    }

    override fun onPause() {
        Ads.onPause(this)
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        Ads.onResume(this)
    }

    override fun onDestroy() {
        Ads.onDestroy(this, destroyNative = true)
        super.onDestroy()
    }
}