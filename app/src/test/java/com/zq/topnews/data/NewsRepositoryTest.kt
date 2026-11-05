package com.zq.topnews.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone

/**
 * The feed now comes from NetEase's public news API, so these checks cover the
 * parts that actually break the UI: JSONP parsing, deduplication, relative
 * timestamps and article-body extraction. No network is used.
 */
class NewsRepositoryTest {

    @Test
    fun parsesFeedAndDropsIncompleteEntries() {
        val now = millisOf("2026-11-05 14:05:00")
        val items = NewsRepository.parseFeed(
            body = FEED_FIXTURE,
            channel = "BAI6JOD9wangning",
            category = "科技",
            nowMillis = now,
        )

        assertEquals(2, items.size)
        val first = items.first()
        assertEquals("DOC1", first.id)
        assertEquals("国产大模型开源生态再添新成员", first.title)
        assertEquals("网易科技", first.source)
        assertEquals("科技", first.category)
        assertEquals(42, first.comments)
        assertEquals("推理成本大幅下降", first.summary)
        assertEquals("5 分钟前", first.time)
        assertTrue(first.paragraphs.isEmpty())
        assertTrue(first.tone in 0..7)

        assertEquals("DOC2", items[1].id)
        assertEquals("网易新闻", items[1].source)
    }

    @Test(expected = IOException::class)
    fun feedParserRejectsPlainJson() {
        NewsRepository.parseFeed("""{"not":"jsonp"}""", "BAI6JOD9wangning", "科技")
    }

    @Test(expected = IOException::class)
    fun feedParserRejectsUnknownChannel() {
        NewsRepository.parseFeed("""artiList({"OTHER":[]})""", "BAI6JOD9wangning", "科技")
    }

    @Test
    fun parsesArticleBodyIntoParagraphs() {
        val paragraphs = NewsRepository.parseArticle(ARTICLE_FIXTURE)

        assertEquals(listOf("第一段正文。", "第二段 & 补充。"), paragraphs)
    }

    @Test
    fun articleParserHandlesMissingBody() {
        assertTrue(NewsRepository.parseArticle("""artiContent({"DOC1":{}})""").isEmpty())
    }

    @Test
    fun formatsRelativeTimestamps() {
        assertEquals("刚刚", NewsRepository.formatRelativeTime("2026-11-05 14:00:00", millisOf("2026-11-05 14:00:30")))
        assertEquals("5 分钟前", NewsRepository.formatRelativeTime("2026-11-05 14:00:00", millisOf("2026-11-05 14:05:00")))
        assertEquals("3 小时前", NewsRepository.formatRelativeTime("2026-11-05 10:00:00", millisOf("2026-11-05 13:00:00")))
        assertEquals("昨天", NewsRepository.formatRelativeTime("2026-11-04 10:00:00", millisOf("2026-11-05 12:00:00")))
        assertEquals("11月2日", NewsRepository.formatRelativeTime("2026-11-02 10:00:00", millisOf("2026-11-05 12:00:00")))
    }

    @Test
    fun everyTabHasAChannel() {
        assertEquals(categories.size, categories.distinct().size)
        assertTrue(categories.isNotEmpty())
    }

    @Test
    fun messageKindsAreUnique() {
        val kinds = sampleMessages.map { it.kind }
        assertEquals(kinds.size, kinds.distinct().size)
        sampleMessages.forEach { message ->
            assertTrue("${message.kind} has a blank subtitle", message.subtitle.isNotBlank())
        }
    }

    private fun millisOf(value: String): Long =
        SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.CHINA).apply {
            timeZone = TimeZone.getTimeZone("Asia/Shanghai")
        }.parse(value)!!.time

    private companion object {
        const val FEED_FIXTURE = """
            artiList({"BAI6JOD9wangning":[
              {"docid":"DOC1","title":"国产大模型开源生态再添新成员",
               "source":"网易科技","ptime":"2026-11-05 14:00:00",
               "commentCount":42,"digest":"推理成本大幅下降"},
              {"docid":"DOC1","title":"重复的条目","source":"网易科技",
               "ptime":"2026-11-05 14:00:00","commentCount":0,"digest":""},
              {"docid":"DOC2","title":"没有来源的条目","ptime":"2026-11-05 13:00:00"},
              {"docid":"DOC3","title":"  ","source":"网易新闻","ptime":"2026-11-05 13:00:00"},
              {"docid":"","title":"没有 id 的条目","source":"网易新闻","ptime":"2026-11-05 13:00:00"}
            ]})
        """

        const val ARTICLE_FIXTURE = """
            artiContent({"DOC1":{"title":"标题","source":"网易科技",
             "body":"<p id=\"a\">第一段正文。</p><p><img src=\"x.jpg\"></p><p>第二段 &amp; 补充。</p><p>责任编辑：张三</p>"}})
        """
    }
}
