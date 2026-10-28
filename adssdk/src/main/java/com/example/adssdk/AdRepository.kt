package com.example.adssdk

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

internal object AdRepository {

    private const val TAG = "urmii"
    private const val PREF = "adsdk_prefs"
    private const val KEY = "ads_json"

    @Volatile
    var ads: List<AdModel> = emptyList()
        private set

    /** unitId -> list of ad ids allowed for that unit (optional, from ads.json "units") */
    @Volatile
    var units: Map<String, List<String>> = emptyMap()
        private set

    /** unitId -> ad source (optional, from ads.json "sources") */
    @Volatile
    var sources: Map<String, AdSource> = emptyMap()
        private set

    /** Default ad source for all units (optional, from ads.json "defaultSource") */
    @Volatile
    var defaultSource: AdSource? = null
        private set

    /** Google ad unit id per FORMAT (optional, from ads.json "google" > "ids") */
    @Volatile
    var googleIds: GoogleAdIds = GoogleAdIds()
        private set

    /** google.testMode from ads.json (null = not set, app code decides) */
    @Volatile
    var googleTestMode: Boolean? = null
        private set

    /** Google ad unit id per PLACEMENT (optional, from ads.json "google" > "units") */
    @Volatile
    var googleUnits: Map<String, String> = emptyMap()
        private set

    // real AdMob ad unit id looks like: ca-app-pub-1234567890123456/1234567890
    private val googleIdRegex = Regex("^ca-app-pub-\\d{16}/\\d{10}\$")

    private var configUrl: String = ""
    private var appContext: Context? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun load(context: Context, url: String, onDone: (() -> Unit)? = null) {
        appContext = context.applicationContext
        configUrl = url
        val prefs = appContext!!.getSharedPreferences(PREF, Context.MODE_PRIVATE)

        // show cached ads immediately
        prefs.getString(KEY, null)?.let {
            if (apply(it)) Log.d(TAG, "AdSdk: using cached ads (${ads.size})")
        }

        executor.execute {
            try {
                Log.d(TAG, "AdSdk: downloading $url")
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000

                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_OK) {
                    Log.e(TAG, "AdSdk: ads.json HTTP $code (404 = file/link wrong or repo private)")
                    conn.disconnect()
                    loadFromAssets(prefs)
                } else {
                    val text = conn.inputStream.bufferedReader().use { it.readText() }
                    conn.disconnect()
                    if (apply(text)) {
                        prefs.edit().putString(KEY, text).apply()
                        Log.d(TAG, "AdSdk: ads.json loaded, ${ads.size} ads, defaultSource=$defaultSource, sources=$sources, googleIds=$googleIds, googleUnits=$googleUnits")
                    } else {
                        Log.e(TAG, "AdSdk: ads.json downloaded but invalid or has no ads")
                        loadFromAssets(prefs)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "AdSdk: ads.json load failed: ${e.javaClass.simpleName}: ${e.message}")
                loadFromAssets(prefs)
            }
            main.post { onDone?.invoke() }
        }
    }

    private fun loadFromAssets(prefs: android.content.SharedPreferences) {
        try {
            val assetManager = appContext?.assets ?: return
            val stream = try {
                assetManager.open("ads.json")
            } catch (_: Exception) {
                return
            }
            val text = stream.bufferedReader().use { it.readText() }
            if (apply(text)) {
                prefs.edit().putString(KEY, text).apply()
                Log.d(TAG, "AdSdk: loaded ads.json from local assets fallback, ${ads.size} ads")
            }
        } catch (assetEx: Exception) {
            Log.e(TAG, "AdSdk: local assets fallback failed: ${assetEx.message}")
        }
    }

    fun refresh(onDone: () -> Unit) {
        val ctx = appContext
        if (ctx == null || configUrl.isBlank()) {
            onDone()
            return
        }
        load(ctx, configUrl, onDone)
    }

    /** Picks an ad automatically. If the unit has a list in ads.json, only those ads are used. */
    fun random(unitId: String? = null): AdModel? {
        val allowed = unitId?.let { units[it] }
        val pool = if (allowed.isNullOrEmpty()) ads else ads.filter { it.id in allowed }
        return pool.randomOrNull() ?: ads.randomOrNull()
    }

    fun find(id: String?): AdModel? = ads.firstOrNull { it.id == id }

    /** Returns the id if it is a valid AdMob ad unit id, otherwise "" (ignored, app id is used). */
    private fun googleId(raw: String?, name: String): String {
        val id = raw?.trim().orEmpty()
        if (id.isEmpty()) return ""
        if (!googleIdRegex.matches(id)) {
            Log.e(TAG, "AdSdk: ads.json google id '$id' for '$name' is not a valid ad unit id -> ignored")
            return ""
        }
        return id
    }

    private fun apply(text: String): Boolean {
        try {
            val root = JSONObject(text)
            val arr = root.optJSONArray("ads")
            val list = ArrayList<AdModel>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    list.add(
                        AdModel(
                            id = o.optString("id", i.toString()),
                            title = o.optString("title"),
                            description = o.optString("description"),
                            iconUrl = o.optString("iconUrl"),
                            imageUrl = o.optString("imageUrl"),
                            clickUrl = o.optString("clickUrl"),
                            buttonText = o.optString("buttonText", "Install")
                        )
                    )
                }
            }

            val map = HashMap<String, List<String>>()
            val u = root.optJSONObject("units")
            if (u != null) {
                val keys = u.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val a = u.optJSONArray(k) ?: continue
                    val ids = ArrayList<String>()
                    for (i in 0 until a.length()) ids.add(a.optString(i))
                    map[k] = ids
                }
            }

            // which source (own / google) each unit should use
            val sourceMap = HashMap<String, AdSource>()
            val s = root.optJSONObject("sources")
            if (s != null) {
                val keys = s.keys()
                while (keys.hasNext()) {
                    val k = keys.next()
                    val parsed = AdSource.parse(s.optString(k))
                    if (parsed != null) sourceMap[k] = parsed
                    else Log.e(TAG, "AdSdk: unknown source '${s.optString(k)}' for unit '$k'")
                }
            }
            val defSource = AdSource.parse(root.optString("defaultSource", ""))

            // Google AdMob ad unit ids (optional) -> lets you change them online
            var gIds = GoogleAdIds()
            val gUnits = HashMap<String, String>()
            var gTest: Boolean? = null
            val g = root.optJSONObject("google")
            if (g != null) {
                if (g.has("testMode")) gTest = g.optBoolean("testMode")
                val i = g.optJSONObject("ids")
                if (i != null) {
                    gIds = GoogleAdIds(
                        banner = googleId(i.optString("banner"), "banner"),
                        interstitial = googleId(i.optString("interstitial"), "interstitial"),
                        native = googleId(i.optString("native"), "native"),
                        rewarded = googleId(i.optString("rewarded"), "rewarded"),
                        appOpen = googleId(i.optString("appOpen"), "appOpen")
                    )
                }
                val gu = g.optJSONObject("units")
                if (gu != null) {
                    val keys = gu.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val id = googleId(gu.optString(k), k)
                        if (id.isNotEmpty()) gUnits[k] = id
                    }
                }
            }

            // a file with no ads is still valid if it only sets the source or Google ids
            if (list.isEmpty() && sourceMap.isEmpty() && defSource == null &&
                gUnits.isEmpty() && gIds == GoogleAdIds() && gTest == null
            ) return false

            ads = list
            units = map
            sources = sourceMap
            defaultSource = defSource
            googleIds = gIds
            googleUnits = gUnits
            googleTestMode = gTest
            return true
        } catch (e: Exception) {
            Log.e(TAG, "AdSdk: ads.json parse error: ${e.message}")
            return false
        }
    }
}