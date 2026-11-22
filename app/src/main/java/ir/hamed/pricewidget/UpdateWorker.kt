package ir.hamed.pricewidget

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class UpdateWorker(ctx: Context, params: WorkerParameters) : CoroutineWorker(ctx, params) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        val prefs = applicationContext.getSharedPreferences("data", Context.MODE_PRIVATE)
        val weatherOk = updateWeather(prefs)
        val pricesOk = updatePrices(prefs)
        PriceWidgetProvider.updateAll(applicationContext)
        if (pricesOk || weatherOk) Result.success() else Result.retry()
    }

    // ------------------------------------------------------------------ قیمت‌ها
    private fun updatePrices(prefs: android.content.SharedPreferences): Boolean {
        val keys = listOf("usd", "usdt", "gold18")
        val value = HashMap<String, Long>()      // مقدار تازه (تومان)
        val approx = HashSet<String>()            // مقدارهایی که تقریبی‌اند

        // ۱) tgju
        for (u in Config.TGJU_URLS) {
            try {
                val root = JSONObject(httpGet(u))
                for ((k, tk) in listOf(
                    "usd" to Config.KEY_USD,
                    "usdt" to Config.KEY_USDT,
                    "gold18" to Config.KEY_GOLD18,
                )) {
                    val t = rialToToman(findKey(root, tk))
                    if (t > 0) value[k] = t
                }
                if (value.isNotEmpty()) break
            } catch (_: Exception) {
            }
        }

        // ۲) والکس، فقط برای چیزهایی که tgju نداد
        if (value.size < keys.size) {
            try {
                val root = JSONObject(httpGet(Config.WALLEX_URL))
                val usdt = walletToman(root, Config.WALLEX_USDT)
                val xaut = walletToman(root, Config.WALLEX_XAUT)
                if (usdt > 0) {
                    if (!value.containsKey("usdt")) value["usdt"] = usdt
                    if (!value.containsKey("usd")) { value["usd"] = usdt; approx.add("usd") }
                }
                if (xaut > 0 && !value.containsKey("gold18")) {
                    // اونس → گرم ۱۸ عیار (تقریبی)
                    value["gold18"] = Math.round(xaut / 31.1035 * 0.75)
                    approx.add("gold18")
                }
            } catch (_: Exception) {
            }
        }

        if (value.isEmpty()) return false

        // ۳) برای مقدارهای نیامده، آخرین مقدار قبلی بماند
        val old = runCatching { JSONObject(prefs.getString("prices", "") ?: "") }
            .getOrDefault(JSONObject())
        val oldApprox = old.optJSONArray("approx")
        val out = JSONObject()
        val outApprox = JSONArray()
        for (k in keys) {
            if (value.containsKey(k)) {
                out.put(k, value[k])
                if (approx.contains(k)) outApprox.put(k)
            } else {
                out.put(k, old.optLong(k, 0L))
                if (oldApprox != null) for (i in 0 until oldApprox.length())
                    if (oldApprox.optString(i) == k) outApprox.put(k)
            }
        }
        out.put("approx", outApprox)
        prefs.edit()
            .putString("prices", out.toString())
            .putLong("ts", System.currentTimeMillis())
            .apply()
        return true
    }

    // ------------------------------------------------------------------ آب‌وهوا
    private fun updateWeather(prefs: android.content.SharedPreferences): Boolean {
        return try {
            val lat = Config.CITIES.joinToString(",") { it.lat.toString() }
            val lon = Config.CITIES.joinToString(",") { it.lon.toString() }
            val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
                    "&current=temperature_2m,weather_code&timezone=auto"
            val s = httpGet(url)
            JSONArray(s)
            prefs.edit().putString("weather", s).apply()
            true
        } catch (_: Exception) {
            false
        }
    }

    // ------------------------------------------------------------------ ابزارها
    /** جست‌وجوی بازگشتی یک کلید در هر جای JSON */
    private fun findKey(o: Any?, key: String): JSONObject? {
        when (o) {
            is JSONObject -> {
                o.optJSONObject(key)?.let { return it }
                val it = o.keys()
                while (it.hasNext()) {
                    findKey(o.opt(it.next()), key)?.let { r -> return r }
                }
            }
            is JSONArray -> for (i in 0 until o.length()) {
                findKey(o.opt(i), key)?.let { r -> return r }
            }
        }
        return null
    }

    /** فیلد p در tgju (ریال، مثل "1,864,000") → تومان */
    private fun rialToToman(item: JSONObject?): Long {
        val p = item?.opt("p")?.toString()?.replace(",", "")?.trim()?.toDoubleOrNull() ?: return 0L
        return Math.round(p / 10.0)
    }

    /** آخرین قیمت یک بازار والکس (تومان) */
    private fun walletToman(root: JSONObject, symbol: String): Long {
        val p = findKey(root, symbol)?.optJSONObject("stats")?.opt("lastPrice")
            ?.toString()?.trim()?.toDoubleOrNull() ?: return 0L
        return Math.round(p)
    }

    private fun httpGet(u: String): String {
        val c = URL(u).openConnection() as HttpURLConnection
        c.connectTimeout = 12000
        c.readTimeout = 12000
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Android) PriceWidget/1.0")
        try {
            if (c.responseCode != 200) throw RuntimeException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
