package com.zq.topnews.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Loads the home feed and article bodies from NetEase's public news endpoints
 * (`3g.163.com`): no API key, no login. The list endpoint answers with a JSONP
 * wrapper (`artiList({...})`) and the article endpoint with `artiContent({...})`.
 *
 * Results are cached in memory so switching tabs or reopening an article does
 * not re-hit the network; pass `forceRefresh` to bypass the cache.
 */
object NewsRepository {

    private const val ENDPOINT = "https://3g.163.com/touch"
    private const val PAGE_SIZE = 20
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    /** NetEase timestamps are Beijing time regardless of the device time zone. */
    private val BEIJING: TimeZone = TimeZone.getTimeZone("Asia/Shanghai")

    /** Public channel id behind every tab of the home feed. */
    private val CHANNELS = linkedMapOf(
        "推荐" to "BBM54PGAwangning", // 头条
        "热点" to "BD29LPUBwangning", // 国内
        "科技" to "BAI6JOD9wangning", // 数码
        "体育" to "BA8E6OEOwangning", // 体育
        "财经" to "BA8EE5GMwangning", // 财经
        "娱乐" to "BA10TA81wangning", // 娱乐
    )

    @Volatile
    private var feeds: Map<String, List<NewsItem>> = emptyMap()

    @Volatile
    private var bodies: Map<String, List<String>> = emptyMap()

    fun cached(category: String): List<NewsItem>? = feeds[category]

    /** Looks an already loaded article up by id, used to restore the detail view. */
    fun find(id: String): NewsItem? =
        feeds.values.firstNotNullOfOrNull { items -> items.firstOrNull { it.id == id } }

    suspend fun feed(
        category: String,
        forceRefresh: Boolean = false,
    ): List<NewsItem> = withContext(Dispatchers.IO) {
        if (!forceRefresh) feeds[category]?.let { return@withContext it }

        val channel = CHANNELS[category] ?: throw IOException("未知频道：$category")
        val items = parseFeed(
            body = httpGet("$ENDPOINT/reconstruct/article/list/$channel/0-$PAGE_SIZE.html"),
            channel = channel,
            category = category,
        )
        feeds = feeds + (category to items)
        items
    }

    suspend fun article(
        id: String,
        forceRefresh: Boolean = false,
    ): List<String> = withContext(Dispatchers.IO) {
        if (!forceRefresh) bodies[id]?.let { return@withContext it }

        val paragraphs = parseArticle(httpGet("$ENDPOINT/article/$id/full.html"))
        bodies = bodies + (id to paragraphs)
        paragraphs
    }

    private fun httpGet(url: String): String {
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Accept", "application/json, text/javascript, */*")
        }
        try {
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) throw IOException("HTTP $status")
            return connection.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
        } finally {
            connection.disconnect()
        }
    }

    internal fun parseFeed(
        body: String,
        channel: String,
        category: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): List<NewsItem> {
        val json = parseJsonp(body, "artiList")
        val array = json.optJSONArray(channel) ?: throw IOException("接口返回格式异常")
        val items = LinkedHashMap<String, NewsItem>()
        for (index in 0 until array.length()) {
            val entry = array.optJSONObject(index) ?: continue
            val id = entry.optText("docid")
            val title = entry.optText("title")
            if (id.isEmpty() || title.isEmpty()) continue
            items.putIfAbsent(
                id,
                NewsItem(
                    id = id,
                    title = title,
                    source = entry.optText("source").ifBlank { "网易新闻" },
                    time = formatRelativeTime(entry.optText("ptime"), nowMillis),
                    category = category,
                    comments = entry.optInt("commentCount", 0),
                    summary = entry.optText("digest"),
                    paragraphs = emptyList(),
                    tone = Math.floorMod(id.hashCode(), 8),
                    url = shareUrl(
                        id = id,
                        url = entry.optText("url"),
                        skipType = entry.optText("skipType"),
                        skipUrl = entry.optText("skipURL"),
                    ),
                ),
            )
        }
        return items.values.toList()
    }

    /** Reads a JSON string, treating a literal JSON `null` as empty like an absent field. */
    private fun JSONObject.optText(name: String): String =
        optString(name).trim().takeIf { it != "null" }.orEmpty()

    /**
     * Resolves the public page to open when an article is shared. Many entries
     * report a web link; videos, photo sets and live rooms instead carry a
     * `skipURL`, and plain articles with neither fall back to NetEase's mobile
     * article page, which redirects to the canonical one.
     */
    private fun shareUrl(id: String, url: String, skipType: String, skipUrl: String): String {
        if (url.isHttpUrl()) return url
        if (skipUrl.isHttpUrl()) {
            return if (skipType == "video") "https://m.163.com/v/video/$id.html" else skipUrl
        }
        return "https://m.163.com/dy/article/$id.html"
    }

    private fun String.isHttpUrl(): Boolean =
        startsWith("http://") || startsWith("https://")

    internal fun parseArticle(body: String): List<String> {
        val json = parseJsonp(body, "artiContent")
        val docId = json.keys().asSequence().firstOrNull() ?: return emptyList()
        val article = json.optJSONObject(docId) ?: return emptyList()
        val html = article.optString("body")
        if (html.isBlank()) return emptyList()

        return PARAGRAPH.findAll(html)
            .map { stripHtml(it.groupValues[1]) }
            .filter { it.isNotEmpty() && !it.startsWith("责任编辑") }
            .toList()
    }

    private fun parseJsonp(body: String, callback: String): JSONObject {
        val trimmed = body.trim()
        if (!trimmed.startsWith(callback)) throw IOException("接口返回格式异常")
        val start = trimmed.indexOf('(')
        val end = trimmed.lastIndexOf(')')
        if (start < 0 || end <= start) throw IOException("接口返回格式异常")
        return try {
            JSONObject(trimmed.substring(start + 1, end))
        } catch (_: JSONException) {
            throw IOException("接口返回格式异常")
        }
    }

    private fun stripHtml(fragment: String): String =
        WHITESPACE.replace(decodeEntities(TAG.replace(fragment, " ")), " ").trim()

    private fun decodeEntities(text: String): String {
        if ('&' !in text) return text
        val named = text
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", "\"")
            .replace("&apos;", "'")
        return NUMERIC_ENTITY.replace(named) { match ->
            val value = match.groupValues[1]
            val code = try {
                if (value.startsWith("x", ignoreCase = true)) {
                    value.substring(1).toInt(16)
                } else {
                    value.toInt()
                }
            } catch (_: NumberFormatException) {
                return@replace match.value
            }
            if (Character.isValidCodePoint(code)) String(Character.toChars(code)) else match.value
        }.replace("&amp;", "&")
    }

    /** Formats a NetEase `ptime` (`yyyy-MM-dd HH:mm:ss`, Beijing time) for the feed meta line. */
    internal fun formatRelativeTime(
        publishedAt: String,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        if (publishedAt.isBlank()) return ""
        val parsed = try {
            publishFormat().parse(publishedAt)
        } catch (_: ParseException) {
            null
        } ?: return publishedAt

        val diff = (nowMillis - parsed.time).coerceAtLeast(0)
        return when {
            diff < 60_000 -> "刚刚"
            diff < 3_600_000 -> "${diff / 60_000} 分钟前"
            diff < 86_400_000 -> "${diff / 3_600_000} 小时前"
            diff < 172_800_000 -> "昨天"
            else -> SimpleDateFormat("M月d日", Locale.CHINA).apply {
                timeZone = BEIJING
            }.format(Date(parsed.time))
        }
    }

    private fun publishFormat(): SimpleDateFormat =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).apply {
            timeZone = BEIJING
        }

    private val PARAGRAPH = Regex(
        "<p[^>]*>(.*?)</p>",
        setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL),
    )
    private val TAG = Regex("<[^>]*>")
    private val WHITESPACE = Regex("\\s+")
    private val NUMERIC_ENTITY = Regex("&#(x?[0-9a-fA-F]+);")
}
