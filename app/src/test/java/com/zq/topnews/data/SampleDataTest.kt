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
    fun videoIdsAreUniqueAndFieldsFilled() {
        val ids = sampleVideos.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
        sampleVideos.forEach { video ->
            assertTrue("${video.id} has a blank title", video.title.isNotBlank())
            assertTrue("${video.id} has a blank author", video.author.isNotBlank())
            assertTrue("${video.id} has a blank duration", video.duration.isNotBlank())
            assertTrue("${video.id} tone out of range: ${video.tone}", video.tone in 0..7)
        }
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
