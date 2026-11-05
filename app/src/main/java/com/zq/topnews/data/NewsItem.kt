package com.zq.topnews.data

/**
 * A single headline shown in the TopNews feed.
 *
 * Items are loaded from NetEase's public news feed; the app ships no bundled
 * articles. [tone] selects one of the generated placeholder thumbnails,
 * [summary] is the short lead shown until [paragraphs] have been fetched for
 * the full article view.
 */
data class NewsItem(
    val id: String,
    val title: String,
    val source: String,
    val time: String,
    val category: String,
    val comments: Int,
    val summary: String,
    val paragraphs: List<String>,
    val tone: Int,
)

/** Channel tabs across the top of the home feed. */
val categories: List<String> = listOf("推荐", "热点", "科技", "体育", "财经", "娱乐")
