package com.zq.topnews.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The demo ships no network or database, so the bundled fixtures are the only
 * data source. These checks catch the mistakes that actually break the UI:
 * duplicate ids (broken back-navigation), unknown categories (empty tabs) and
 * empty bodies (blank article pages).
 */
class SampleDataTest {

    @Test
    fun newsIdsAreUnique() {
        val ids = sampleNews.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }

    @Test
    fun newsCategoriesAreAllTabs() {
        val unknown = sampleNews.map { it.category }.filterNot { it in categories }
        assertTrue("unknown categories: $unknown", unknown.isEmpty())
    }

    @Test
    fun everyChannelTabHasArticles() {
        // The first tab ("推荐") is the aggregate feed and matches every article.
        val empty = categories.drop(1).filter { category ->
            sampleNews.none { it.category == category }
        }
        assertTrue("empty tabs: $empty", empty.isEmpty())
    }

    @Test
    fun articlesHaveReadableContent() {
        sampleNews.forEach { item ->
            assertTrue("${item.id} has a blank title", item.title.isNotBlank())
            assertTrue("${item.id} has a blank source", item.source.isNotBlank())
            assertTrue("${item.id} has a blank time", item.time.isNotBlank())
            assertTrue("${item.id} needs at least one paragraph", item.paragraphs.isNotEmpty())
            assertTrue(
                "${item.id} has blank paragraphs",
                item.paragraphs.none { it.isBlank() },
            )
            assertTrue("${item.id} comments must not be negative", item.comments >= 0)
            assertTrue("${item.id} tone out of range: ${item.tone}", item.tone in 0..7)
        }
    }

    @Test
    fun parsesBilibiliPopularResponse() {
        val videos = VideoRepository.parse(BILIBILI_FIXTURE)

        assertEquals(2, videos.size)
        val first = videos.first()
        assertEquals("BV1xx411c7mD", first.bvid)
        assertEquals("测试视频标题", first.title)
        assertEquals("测试UP主", first.author)
        assertEquals(1_234_567L, first.views)
        assertEquals(159, first.durationSeconds)
        assertEquals(
            "https://i0.hdslb.com/bfs/archive/cover.jpg",
            first.coverUrl,
        )
        assertEquals(
            "https://www.bilibili.com/video/BV1xx411c7mD",
            first.pageUrl,
        )
    }

    @Test
    fun parseFallsBackForMissingOwnerName() {
        val video = VideoRepository.parse(
            """{"code":0,"data":{"list":[{"bvid":"BV1","title":"t","pic":"http://x/y.jpg","duration":5}]}}""",
        ).single()

        assertEquals("哔哩哔哩用户", video.author)
        assertEquals(0L, video.views)
    }

    @Test(expected = java.io.IOException::class)
    fun parseRejectsNonZeroCode() {
        VideoRepository.parse("""{"code":-412,"message":"请求被拦截"}""")
    }

    private companion object {
        const val BILIBILI_FIXTURE = """
            {"code":0,"data":{"list":[
              {"bvid":"BV1xx411c7mD","title":"测试视频标题",
               "pic":"http://i0.hdslb.com/bfs/archive/cover.jpg","duration":159,
               "owner":{"mid":1,"name":"测试UP主"},
               "stat":{"view":1234567,"danmaku":100}},
              {"bvid":"BV1yy411c7mE","title":"第二个视频",
               "pic":"http://i1.hdslb.com/bfs/archive/cover2.jpg","duration":3725,
               "owner":{"mid":2,"name":"另一个UP主"},
               "stat":{"view":88}}
            ]}}
        """
    }

    @Test
    fun messageKindsAreUnique() {
        val kinds = sampleMessages.map { it.kind }
        assertEquals(kinds.size, kinds.distinct().size)
        sampleMessages.forEach { message ->
            assertTrue("${message.kind} has a blank subtitle", message.subtitle.isNotBlank())
        }
    }
}
