package ir.hamed.pricewidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import kotlin.math.abs
import kotlin.math.roundToInt

class PriceWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, mgr: AppWidgetManager, ids: IntArray) {
        ids.forEach { mgr.updateAppWidget(it, build(context)) }
        schedule(context)
    }

    override fun onEnabled(context: Context) {
        schedule(context)
        refreshNow(context)
    }

    override fun onReceive(context: Context, intent: Intent) {
        super.onReceive(context, intent)
        when (intent.action) {
            ACTION_REFRESH -> refreshNow(context)
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED -> updateAll(context)
        }
    }

    companion object {
        const val ACTION_REFRESH = "ir.hamed.pricewidget.REFRESH"

        private val net = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        fun schedule(context: Context) {
            val req = PeriodicWorkRequestBuilder<UpdateWorker>(15, TimeUnit.MINUTES)
                .setConstraints(net)
                .build()
            WorkManager.getInstance(context)
                .enqueueUniquePeriodicWork("periodic", ExistingPeriodicWorkPolicy.KEEP, req)
        }

        fun refreshNow(context: Context) {
            val req = OneTimeWorkRequestBuilder<UpdateWorker>().setConstraints(net).build()
            WorkManager.getInstance(context)
                .enqueueUniqueWork("now", ExistingWorkPolicy.REPLACE, req)
        }

        fun updateAll(context: Context) {
            val mgr = AppWidgetManager.getInstance(context)
            val ids = mgr.getAppWidgetIds(ComponentName(context, PriceWidgetProvider::class.java))
            ids.forEach { mgr.updateAppWidget(it, build(context)) }
        }

        fun build(context: Context): RemoteViews {
            val prefs = context.getSharedPreferences("data", Context.MODE_PRIVATE)
            val rv = RemoteViews(context.packageName, R.layout.widget)

            // تاریخ‌ها
            val cal = Calendar.getInstance()
            val (jy, jm, jd) = Fa.toJalali(
                cal.get(Calendar.YEAR), cal.get(Calendar.MONTH) + 1, cal.get(Calendar.DAY_OF_MONTH)
            )
            val month = Fa.MONTHS[jm - 1]
            val dow = Fa.weekday(cal.get(Calendar.DAY_OF_WEEK))
            rv.setTextViewText(R.id.date_shamsi, Fa.d("$dow $jd $month $jy"))
            rv.setTextViewText(R.id.date_imperial, Fa.d("$jd $month ${jy + 1180} شاهنشاهی"))

            // قیمت‌ها
            val p = runCatching { JSONObject(prefs.getString("prices", "") ?: "") }.getOrNull()
            val approx = HashSet<String>()
            p?.optJSONArray("approx")?.let { a ->
                for (i in 0 until a.length()) approx.add(a.optString(i))
            }
            fun price(key: String): String {
                val v = p?.optLong(key, 0L) ?: 0L
                if (v <= 0) return "—"
                return (if (approx.contains(key)) "~" else "") + Fa.money(v)
            }
            rv.setTextViewText(R.id.usd, price("usd"))
            rv.setTextViewText(R.id.usdt, price("usdt"))
            rv.setTextViewText(R.id.gold, price("gold18"))

            // آب‌وهوا
            val w = runCatching { JSONArray(prefs.getString("weather", "") ?: "") }.getOrNull()
            val names = intArrayOf(R.id.c0_name, R.id.c1_name, R.id.c2_name, R.id.c3_name)
            val temps = intArrayOf(R.id.c0_temp, R.id.c1_temp, R.id.c2_temp, R.id.c3_temp)
            Config.CITIES.forEachIndexed { i, city ->
                rv.setTextViewText(names[i], city.name)
                val cur = w?.optJSONObject(i)?.optJSONObject("current")
                val t = cur?.optDouble("temperature_2m", Double.NaN) ?: Double.NaN
                val text = if (t.isNaN()) "—" else {
                    val r = t.roundToInt()
                    val sign = if (r < 0) "\u200E−" else ""
                    val emoji = Fa.weatherEmoji(cur?.optInt("weather_code", -1) ?: -1)
                    "$emoji $sign${Fa.d(abs(r).toString())}°"
                }
                rv.setTextViewText(temps[i], text)
            }

            // پاورقی
            val ts = prefs.getLong("ts", 0L)
            val footer = if (ts > 0) {
                val hm = SimpleDateFormat("HH:mm", Locale.US).format(Date(ts))
                "قیمت‌ها به تومان · بروزرسانی ${Fa.d(hm)}"
            } else "قیمت‌ها به تومان"
            rv.setTextViewText(R.id.footer, footer)

            // لمس ویجت = بروزرسانی فوری
            val intent = Intent(context, PriceWidgetProvider::class.java).setAction(ACTION_REFRESH)
            val pi = PendingIntent.getBroadcast(
                context, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )
            rv.setOnClickPendingIntent(R.id.root, pi)
            return rv
        }
    }
}
