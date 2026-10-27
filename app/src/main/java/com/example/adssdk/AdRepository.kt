package com.example.adssdk

import android.content.Context
import android.os.Handler
import android.os.Looper
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

internal object AdRepository {

    private const val PREF = "adsdk_prefs"
    private const val KEY = "ads_json"

    @Volatile
    var ads: List<AdModel> = emptyList()
        private set

    /** unitId -> list of ad ids allowed for that unit (optional, from ads.json "units") */
    @Volatile
    var units: Map<String, List<String>> = emptyMap()
        private set

    private var configUrl: String = ""
    private var appContext: Context? = null
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun load(context: Context, url: String, onDone: (() -> Unit)? = null) {
        appContext = context.applicationContext
        configUrl = url
        val prefs = appContext!!.getSharedPreferences(PREF, Context.MODE_PRIVATE)

        // show cached ads immediately
        prefs.getString(KEY, null)?.let { apply(it) }

        executor.execute {
            try {
                val conn = URL(url).openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                val text = conn.inputStream.bufferedReader().use { it.readText() }
                conn.disconnect()
                if (apply(text)) {
                    prefs.edit().putString(KEY, text).apply()
                }
            } catch (e: Exception) {
                // ignore, cached ads (if any) stay in use
            }
            main.post { onDone?.invoke() }
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

    private fun apply(text: String): Boolean {
        try {
            val root = JSONObject(text)
            val arr = root.getJSONArray("ads")
            val list = ArrayList<AdModel>()
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
            if (list.isEmpty()) return false

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

            ads = list
            units = map
            return true
        } catch (e: Exception) {
            return false
        }
    }
}