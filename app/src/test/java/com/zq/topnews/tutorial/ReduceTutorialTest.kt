package com.zq.topnews.tutorial

import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.fold
import kotlinx.coroutines.flow.reduce
import kotlinx.coroutines.flow.runningReduce
import kotlinx.coroutines.flow.scan
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 《Android / Kotlin 中的 reduce 详细教程》配套可运行示例。
 *
 * 运行方式：
 * ./gradlew testDebugUnitTest --tests "com.zq.topnews.tutorial.ReduceTutorialTest"
 *
 * 所有示例都只依赖 Kotlin 标准库、协程与 org.json，不依赖 Android 运行时，
 * 因此可以直接在本地 JVM 单元测试里验证教程中的输出。
 */
class ReduceTutorialTest {

    // ---------------------------------------------------------------------
    // 1. 最小示例
    // ---------------------------------------------------------------------

    @Test
    fun `reduce 把非空列表折叠成一个值`() {
        val steps = listOf(1200, 3400, 560)
        val total = steps.reduce { acc, value -> acc + value }

        assertEquals(5160, total)
    }

    @Test
    fun `reduce 的累加器从第一个元素开始`() {
        // 第一个元素 10 直接成为累加器的初始值，因此只执行两次 lambda。
        val visited = mutableListOf<Pair<Int, Int>>()
        val result = listOf(10, 3, 2).reduce { acc, value ->
            visited += acc to value
            acc - value
        }

        assertEquals(5, result)
        assertEquals(listOf(10 to 3, 7 to 2), visited)
    }

    @Test
    fun `空集合调用 reduce 会抛异常`() {
        val error = assertThrows(UnsupportedOperationException::class.java) {
            emptyList<Int>().reduce(Int::plus)
        }

        assertEquals("Empty collection can't be reduced.", error.message)
    }

    @Test
    fun `reduceOrNull 在空集合上返回 null`() {
        val total = emptyList<Int>().reduceOrNull(Int::plus)

        assertNull(total)
    }

    // ---------------------------------------------------------------------
    // 2. reduce vs fold
    // ---------------------------------------------------------------------

    @Test
    fun `fold 有初始值 可以改变结果类型 也能处理空集合`() {
        val words = listOf("Kotlin", "reduce", "tutorial")

        val sentence = words.fold(StringBuilder()) { acc, word ->
            if (acc.isNotEmpty()) acc.append(' ')
            acc.append(word)
        }.toString()

        val empty = emptyList<String>().fold(0) { acc, _ -> acc + 1 }

        assertEquals("Kotlin reduce tutorial", sentence)
        assertEquals(0, empty)
    }

    @Test
    fun `reduce 的结果类型必须能容纳元素类型`() {
        // S 是结果类型，T 是元素类型，约束是 T : S，所以 S 可以是 T 的父类型。
        val values: List<Any> = listOf(1, "two", 3.0)

        // 这里 S 被推断为 Any，accumulator 最终也是 Any。
        val result: Any = values.reduce { acc, value -> "$acc|$value" }

        assertEquals("1|two|3.0", result)
    }

    // ---------------------------------------------------------------------
    // 3. reduce 家族
    // ---------------------------------------------------------------------

    @Test
    fun `reduceIndexed 可以拿到下标`() {
        val result = listOf("a", "b", "c").reduceIndexed { index, acc, value ->
            acc + index + value
        }

        assertEquals("a1b2c", result)
    }

    @Test
    fun `reduceRight 从右向左折叠`() {
        val left = listOf("A", "B", "C").reduce { acc, value -> "($acc+$value)" }
        val right = listOf("A", "B", "C").reduceRight { value, acc -> "($value+$acc)" }

        assertEquals("((A+B)+C)", left)
        assertEquals("(A+(B+C))", right)
    }

    @Test
    fun `减法不满足结合律 左右折叠结果不同`() {
        val left = listOf(10, 3, 2).reduce { acc, value -> acc - value }
        val right = listOf(10, 3, 2).reduceRight { value, acc -> value - acc }

        assertEquals(5, left)   // (10 - 3) - 2
        assertEquals(9, right)  // 10 - (3 - 2)
    }

    @Test
    fun `foldIndexed 与 foldRight 的参数顺序`() {
        val indexed = listOf("a", "b").foldIndexed("") { index, acc, value ->
            acc + index + value
        }
        val right = listOf("A", "B", "C").foldRight("") { value, acc ->
            "($value+$acc)"
        }

        assertEquals("0a1b", indexed)
        assertEquals("(A+(B+(C+)))", right)
    }

    @Test
    fun `runningReduce 会保留每一步的中间结果`() {
        val running = listOf(1, 2, 3, 4).runningReduce(Int::plus)

        assertEquals(listOf(1, 3, 6, 10), running)
    }

    @Test
    fun `runningReduce 在空集合上返回空列表`() {
        val running = emptyList<Int>().runningReduce(Int::plus)

        assertTrue(running.isEmpty())
    }

    @Test
    fun `runningFold 和 scan 会先输出初始值`() {
        val running = listOf(1, 2, 3).runningFold(0, Int::plus)
        val scanned = listOf(1, 2, 3).scan(0, Int::plus)

        assertEquals(listOf(0, 1, 3, 6), running)
        assertEquals(listOf(0, 1, 3, 6), scanned)
    }

    // ---------------------------------------------------------------------
    // 4. 字符串拼接：为什么不要在 reduce 里用 +=
    // ---------------------------------------------------------------------

    @Test
    fun `StringBuilder 是拼接大量字符串的正确累加器`() {
        val names = (1..5).map { "item-$it" }

        val text = names.fold(StringBuilder()) { acc, name ->
            if (acc.isNotEmpty()) acc.append(", ")
            acc.append(name)
        }.toString()

        assertEquals("item-1, item-2, item-3, item-4, item-5", text)
    }

    // ---------------------------------------------------------------------
    // 5. Android 场景：购物车结算
    // ---------------------------------------------------------------------

    data class CartLine(
        val name: String,
        val unitPriceCents: Long,
        val quantity: Int,
    )

    data class CartSummary(
        val subtotalCents: Long,
        val discountCents: Long,
        val shippingCents: Long,
        val totalCents: Long,
    )

    /** 满 199 元减 30 元；不满 99 元收 8 元运费（空购物车除外）。金额全部用“分”做整数运算。 */
    fun summarizeCart(lines: List<CartLine>): CartSummary {
        // 购物车允许为空，所以用 fold 提供初始值，而不是 reduce。
        val subtotal = lines.fold(0L) { acc, line ->
            acc + line.unitPriceCents * line.quantity
        }
        val discount = if (subtotal >= 19_900L) 3_000L else 0L
        val shipping = when {
            lines.isEmpty() -> 0L
            subtotal >= 9_900L -> 0L
            else -> 800L
        }

        return CartSummary(
            subtotalCents = subtotal,
            discountCents = discount,
            shippingCents = shipping,
            totalCents = subtotal - discount + shipping,
        )
    }

    @Test
    fun `购物车结算使用整数分避免浮点误差`() {
        val summary = summarizeCart(
            listOf(
                CartLine("机械键盘", 49_900, 1),
                CartLine("鼠标垫", 3_990, 2),
            ),
        )

        assertEquals(57_880L, summary.subtotalCents)
        assertEquals(3_000L, summary.discountCents)
        assertEquals(0L, summary.shippingCents)
        assertEquals(54_880L, summary.totalCents)
    }

    @Test
    fun `空购物车也能算出零元账单`() {
        val summary = summarizeCart(emptyList())

        assertEquals(0L, summary.subtotalCents)
        assertEquals(0L, summary.shippingCents)
        assertEquals(0L, summary.totalCents)
    }

    // ---------------------------------------------------------------------
    // 6. Android 场景：合并分页数据与时间区间
    // ---------------------------------------------------------------------

    data class Article(val title: String, val readMinutes: Int)

    data class FeedChunk(val articles: List<Article>, val nextCursor: String?)

    /** 只在 chunks 保证非空时调用；结果类型与元素类型相同，正是 reduce 的适用场景。 */
    fun mergeChunks(chunks: List<FeedChunk>): FeedChunk =
        chunks.reduce { acc, chunk ->
            FeedChunk(
                articles = acc.articles + chunk.articles,
                nextCursor = chunk.nextCursor,
            )
        }

    @Test
    fun `合并分页响应时使用最后一页的 cursor`() {
        val merged = mergeChunks(
            listOf(
                FeedChunk(listOf(Article("A", 3)), "cursor-1"),
                FeedChunk(listOf(Article("B", 5)), "cursor-2"),
                FeedChunk(listOf(Article("C", 2)), null),
            ),
        )

        assertEquals(listOf("A", "B", "C"), merged.articles.map { it.title })
        assertNull(merged.nextCursor)
    }

    data class Span(val start: Int, val end: Int)

    fun List<Span>.mergeSpans(): Span =
        reduce { acc, span -> Span(minOf(acc.start, span.start), maxOf(acc.end, span.end)) }

    @Test
    fun `把多个时间区间合并成一个总区间`() {
        val span = listOf(Span(10, 20), Span(5, 15), Span(30, 40)).mergeSpans()

        assertEquals(Span(5, 40), span)
    }

    // ---------------------------------------------------------------------
    // 7. Android 场景：解析 JSON 并聚合
    // ---------------------------------------------------------------------

    @Test
    fun `从 JSON 数组聚合总价`() {
        val json = JSONArray(
            """
            [
              {"name": "A", "priceCents": 1990},
              {"name": "B", "priceCents": 2990},
              {"name": "C", "priceCents": 990}
            ]
            """.trimIndent(),
        )

        val total = (0 until json.length())
            .map { index -> json.getJSONObject(index).optLong("priceCents") }
            .reduceOrNull(Long::plus)
            ?: 0L

        assertEquals(5970L, total)
    }

    // ---------------------------------------------------------------------
    // 8. Kotlin Flow：reduce / fold / scan 是流式聚合
    // ---------------------------------------------------------------------

    @Test
    fun `Flow 的 reduce 会收集整个流并返回一个值`() = runBlocking {
        val total = flowOf(1, 2, 3, 4).reduce(Int::plus)

        assertEquals(10, total)
    }

    @Test
    fun `空 Flow 调用 reduce 会抛异常`() {
        val error = assertThrows(NoSuchElementException::class.java) {
            runBlocking { emptyFlow<Int>().reduce(Int::plus) }
        }

        assertTrue(error.message.orEmpty().contains("Empty flow"))
    }

    @Test
    fun `Flow 的 fold 允许空流并返回初始值`() = runBlocking {
        val total = emptyFlow<Int>().fold(100, Int::plus)

        assertEquals(100, total)
    }

    @Test
    fun `Flow 的 scan 会把每一步结果都发射出来`() = runBlocking {
        val emissions = flowOf(1, 2, 3).scan(0, Int::plus).toList()

        assertEquals(listOf(0, 1, 3, 6), emissions)
    }

    @Test
    fun `Flow 的 runningReduce 不发射初始值`() = runBlocking {
        val emissions = flowOf(1, 2, 3).runningReduce(Int::plus).toList()

        assertEquals(listOf(1, 3, 6), emissions)
    }

    // ---------------------------------------------------------------------
    // 9. 练习题参考答案
    // ---------------------------------------------------------------------

    @Test
    fun `练习一 统计单词频率`() {
        val words = listOf("kotlin", "flow", "kotlin", "reduce", "kotlin")

        val counts = words.fold(mutableMapOf<String, Int>()) { acc, word ->
            acc[word] = (acc[word] ?: 0) + 1
            acc
        }

        assertEquals(mapOf("kotlin" to 3, "flow" to 1, "reduce" to 1), counts)
    }

    @Test
    fun `练习二 解析 query string`() {
        val query = "channel=tech&page=2&keyword=kotlin"

        val params = query.split("&").fold(mutableMapOf<String, String>()) { acc, pair ->
            val (key, value) = pair.split("=", limit = 2)
            acc[key] = value
            acc
        }

        assertEquals("2", params["page"])
        assertEquals("kotlin", params["keyword"])
    }

    @Test
    fun `练习三 安全地计算平均分`() {
        val scores = listOf(80, 90, 100)

        val average = scores.reduceOrNull(Int::plus)?.toDouble()?.div(scores.size)

        assertEquals(90.0, average!!, 0.0001)
        assertNull(emptyList<Int>().reduceOrNull(Int::plus))
    }

    data class Comment(val text: String, val replies: List<Comment> = emptyList())

    fun Comment.totalCount(): Int = 1 + replies.sumOf { it.totalCount() }

    @Test
    fun `练习四 递归统计评论总数`() {
        val comments = listOf(
            Comment(
                "根评论",
                listOf(
                    Comment("回复 1", listOf(Comment("回复 1-1"))),
                    Comment("回复 2"),
                ),
            ),
            Comment("另一条根评论"),
        )

        val total = comments.sumOf { it.totalCount() }

        assertEquals(5, total)
    }
}
