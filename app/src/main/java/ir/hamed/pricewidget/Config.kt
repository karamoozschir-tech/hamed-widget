package ir.hamed.pricewidget

object Config {
    // منبع اول: tgju.org (اگر یکی باز نشد، به ترتیب بعدی امتحان می‌شود)
    val TGJU_URLS = listOf(
        "https://call.tgju.org/ajax.json",
        "https://call1.tgju.org/ajax.json",
        "https://call4.tgju.org/ajax.json",
        "https://call5.tgju.org/ajax.json",
    )

    // کلیدهای tgju (همه به ریال)
    const val KEY_USD = "price_dollar_rl"
    const val KEY_USDT = "crypto-tether-irr"
    const val KEY_GOLD18 = "geram18"

    // منبع دوم: والکس (قیمت‌ها به تومان). فقط وقتی tgju جواب ندهد استفاده می‌شود
    const val WALLEX_URL = "https://api.wallex.ir/v1/markets"
    const val WALLEX_USDT = "USDTTMN"   // تتر به تومان
    const val WALLEX_XAUT = "XAUTTMN"   // یک اونس طلای دیجیتال به تومان

    data class City(val name: String, val lat: Double, val lon: Double)

    // مختصات تقریبی است؛ اگر لازم بود دقیق‌ترش کن
    val CITIES = listOf(
        City("شاهدشهر", 35.5589, 51.0792),
        City("هادی‌شهر", 38.8500, 45.6600),
        City("پونک", 35.7620, 51.3300),
        City("هشجین", 37.3600, 48.5900),
    )
}
