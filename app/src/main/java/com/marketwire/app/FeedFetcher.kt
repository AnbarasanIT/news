package com.marketwire.app

import android.util.Xml
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.xmlpull.v1.XmlPullParser
import java.io.StringReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit

object FeedFetcher {

    private const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36"

    private val IST = TimeZone.getTimeZone("Asia/Kolkata")

    private val FEEDS = listOf(
        "https://economictimes.indiatimes.com/markets/rssfeeds/1977021501.cms",
        "https://www.moneycontrol.com/rss/marketreports.xml",
        "https://www.livemint.com/rss/markets",
        "https://www.cnbctv18.com/commonfeeds/v1/cne/rss/market.xml",
        "https://www.cnbc.com/id/15839069/device/rss/rss.html",
        "https://news.google.com/rss/search?q=when:1d+site:reuters.com+(india+OR+sensex+OR+nifty+OR+markets)&hl=en-IN&gl=IN&ceid=IN:en"
    )

    private const val MAX_ITEMS = 30

    private val client = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    // Cache article summaries by link so repeat polls don't re-scrape the same page
    private val summaryCache = HashMap<String, String>()

    private val DATE_FORMATS = listOf(
        "EEE, dd MMM yyyy HH:mm:ss Z",
        "EEE, dd MMM yyyy HH:mm:ss zzz",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "EEE, d MMM yyyy HH:mm:ss Z"
    )

    private data class RawItem(val title: String, val link: String, val pubDate: String)

    private fun httpGet(url: String): String {
        val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
            return resp.body?.string() ?: throw Exception("empty body")
        }
    }

    private fun parseRss(xml: String): Pair<String, List<RawItem>> {
        val parser: XmlPullParser = Xml.newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false)
        parser.setInput(StringReader(xml))

        var channelTitle = ""
        var inChannelHeader = true
        val items = mutableListOf<RawItem>()

        var title = ""
        var link = ""
        var pubDate = ""
        var inItem = false
        var currentTag = ""

        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            when (event) {
                XmlPullParser.START_TAG -> {
                    currentTag = parser.name
                    if (currentTag == "item" || currentTag == "entry") {
                        inItem = true
                        inChannelHeader = false
                        title = ""; link = ""; pubDate = ""
                    }
                    if (currentTag == "link" && inItem) {
                        // Atom-style <link href="..."/>
                        val href = parser.getAttributeValue(null, "href")
                        if (href != null) link = href
                    }
                }
                XmlPullParser.TEXT -> {
                    val text = parser.text?.trim().orEmpty()
                    if (text.isNotEmpty()) {
                        if (inItem) {
                            when (currentTag) {
                                "title" -> title = text
                                "link" -> if (link.isEmpty()) link = text
                                "pubDate", "published", "updated" -> pubDate = text
                            }
                        } else if (inChannelHeader && currentTag == "title") {
                            channelTitle = text
                        }
                    }
                }
                XmlPullParser.END_TAG -> {
                    val tag = parser.name
                    if (tag == "item" || tag == "entry") {
                        if (title.isNotEmpty() && link.isNotEmpty()) {
                            items.add(RawItem(title, link, pubDate))
                        }
                        inItem = false
                    }
                }
            }
            event = parser.next()
        }
        return Pair(channelTitle, items)
    }

    private fun parseDateToMillis(raw: String): Long? {
        for (pattern in DATE_FORMATS) {
            try {
                val sdf = SimpleDateFormat(pattern, Locale.US)
                sdf.isLenient = true
                val d = sdf.parse(raw) ?: continue
                return d.time
            } catch (_: Exception) {
                // try next pattern
            }
        }
        return null
    }

    private fun istDateKey(millisUtc: Long): Int {
        val cal = Calendar.getInstance(IST)
        cal.timeInMillis = millisUtc
        return cal.get(Calendar.YEAR) * 10000 + (cal.get(Calendar.MONTH) + 1) * 100 + cal.get(Calendar.DAY_OF_MONTH)
    }

    private fun istTimeLabel(millisUtc: Long): String {
        val cal = Calendar.getInstance(IST)
        cal.timeInMillis = millisUtc
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        return String.format(Locale.US, "%02d:%02d IST", h, m)
    }

    private fun getArticleSummary(url: String): String {
        summaryCache[url]?.let { return it }
        val summary = try {
            val doc = Jsoup.connect(url)
                .userAgent(USER_AGENT)
                .timeout(8000)
                .get()
            val meta = doc.selectFirst("meta[name=description]")
                ?: doc.selectFirst("meta[property=og:description]")
            var text = meta?.attr("content")?.trim().orEmpty()
            if (text.isEmpty()) {
                val paragraphs = doc.select("p")
                    .map { it.text().trim() }
                    .filter { it.length > 40 }
                text = paragraphs.take(2).joinToString(" ")
            }
            if (text.isEmpty()) "(no summary available)" else text
        } catch (ex: Exception) {
            "(couldn't fetch article: ${ex.message})"
        }
        summaryCache[url] = summary
        return summary
    }

    private fun marketStatus(): String {
        val cal = Calendar.getInstance(IST)
        val dow = cal.get(Calendar.DAY_OF_WEEK) // SUNDAY=1 ... SATURDAY=7
        if (dow == Calendar.SUNDAY || dow == Calendar.SATURDAY) return "closed"
        val minutesNow = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val open = 9 * 60 + 15
        val close = 15 * 60 + 30
        return if (minutesNow in open..close) "open" else "closed"
    }

    /** Fetch every feed concurrently, keep only today's (IST) items, then fetch
     * article summaries. Mirrors poll_feeds()/get_article_summary() from the
     * original Python script. */
    suspend fun fetchSnapshot(): Snapshot = withContext(Dispatchers.IO) {
        val todayKey = istDateKey(System.currentTimeMillis())
        val errors = mutableListOf<String>()
        val allItems = mutableListOf<NewsItem>()

        val deferreds = FEEDS.map { url ->
            async {
                try {
                    val xml = httpGet(url)
                    val (channelTitle, rawItems) = parseRss(xml)
                    val sourceName = channelTitle.ifBlank { url }
                    val todays = mutableListOf<NewsItem>()
                    for (raw in rawItems) {
                        val millis = parseDateToMillis(raw.pubDate) ?: continue
                        if (istDateKey(millis) != todayKey) continue
                        todays.add(
                            NewsItem(
                                title = raw.title,
                                link = raw.link,
                                source = sourceName,
                                publishedIst = istTimeLabel(millis),
                                summary = "",
                                sortMillis = millis
                            )
                        )
                    }
                    todays
                } catch (ex: Exception) {
                    synchronized(errors) { errors.add("$url: ${ex.message}") }
                    emptyList()
                }
            }
        }

        deferreds.awaitAll().forEach { allItems.addAll(it) }
        allItems.sortByDescending { it.sortMillis }
        val top = allItems.take(MAX_ITEMS)

        // Fetch summaries concurrently (each result cached for next poll)
        val withSummaries = top.map { item ->
            async { item.copy(summary = getArticleSummary(item.link)) }
        }.awaitAll()

        val now = Calendar.getInstance(IST)
        val generatedAt = String.format(
            Locale.US, "%04d-%02d-%02d %02d:%02d:%02d IST",
            now.get(Calendar.YEAR), now.get(Calendar.MONTH) + 1, now.get(Calendar.DAY_OF_MONTH),
            now.get(Calendar.HOUR_OF_DAY), now.get(Calendar.MINUTE), now.get(Calendar.SECOND)
        )

        Snapshot(
            items = withSummaries,
            marketStatus = marketStatus(),
            generatedAt = generatedAt,
            error = if (errors.isEmpty()) null else errors.joinToString("; ")
        )
    }
}
