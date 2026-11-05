package com.zq.topnews.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** A video entry fetched from Bilibili's public popular feed. */
data class FeedVideo(
    val bvid: String,
    val title: String,
    val author: String,
    val coverUrl: String,
    val views: Long,
    val durationSeconds: Int,
) {
    val pageUrl: String get() = "https://www.bilibili.com/video/$bvid"
}

/**
 * Loads the public Bilibili "popular" list — no API key, no login.
 *
 * Results are cached in memory so switching tabs does not re-hit the network;
 * pass `forceRefresh` to bypass the cache when the user taps refresh.
 */
object VideoRepository {

    private const val ENDPOINT = "https://api.bilibili.com/x/web-interface/popular"
    private const val USER_AGENT =
        "Mozilla/5.0 (Linux; Android 15) AppleWebKit/537.36 " +
            "(KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

    @Volatile
    private var cache: List<FeedVideo>? = null

    fun cached(): List<FeedVideo>? = cache

    suspend fun popular(
        page: Int = 1,
        pageSize: Int = 20,
        forceRefresh: Boolean = false,
    ): List<FeedVideo> = withContext(Dispatchers.IO) {
        if (!forceRefresh) cache?.let { return@withContext it }

        val connection = (URL("$ENDPOINT?ps=$pageSize&pn=$page").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 15_000
            readTimeout = 15_000
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Referer", "https://www.bilibili.com/")
            setRequestProperty("Accept", "application/json")
        }
        try {
            val status = connection.responseCode
            if (status != HttpURLConnection.HTTP_OK) throw IOException("HTTP $status")
            val body = connection.inputStream.bufferedReader().use { it.readText() }
            parse(body).also { cache = it }
        } finally {
            connection.disconnect()
        }
    }

    internal fun parse(body: String): List<FeedVideo> {
        val json = JSONObject(body)
        val code = json.optInt("code", -1)
        if (code != 0) {
            throw IOException(json.optString("message").ifBlank { "接口返回错误（$code）" })
        }
        val list = json.getJSONObject("data").getJSONArray("list")
        return buildList {
            for (index in 0 until list.length()) {
                val item = list.getJSONObject(index)
                val bvid = item.optString("bvid")
                if (bvid.isBlank()) continue
                add(
                    FeedVideo(
                        bvid = bvid,
                        title = item.optString("title"),
                        author = item.optJSONObject("owner")
                            ?.optString("name")
                            .orEmpty()
                            .ifBlank { "哔哩哔哩用户" },
                        coverUrl = item.optString("pic").replaceFirst("http://", "https://"),
                        views = item.optJSONObject("stat")?.optLong("view", 0L) ?: 0L,
                        durationSeconds = item.optInt("duration", 0),
                    ),
                )
            }
        }
    }
}
