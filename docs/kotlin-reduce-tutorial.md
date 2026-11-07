# Android / Kotlin 中的 reduce 详细教程

> 面向已经会写 Kotlin、正在做 Android 开发，但还没有真正吃透 `reduce` 的同学。
> 本文基于 Kotlin 2.1.x（项目使用 2.1.21）与 kotlinx-coroutines 1.9.x。
> 核心的集合与 Flow 示例都配有可直接运行的单元测试：`app/src/test/java/com/zq/topnews/tutorial/ReduceTutorialTest.kt`。

## 目录

1. 先确认：你说的 reduce 是哪一个？
2. 一句话理解 reduce
3. 函数签名与标准库源码
4. 手把手看执行过程
5. 最小可运行示例
6. reduce 与 fold 的区别（最重要的一节）
7. reduce 家族全景
8. 顺序、结合律与并行
9. Android 实战场景
10. Kotlin Flow 中的 reduce
11. RxJava 与 Java Stream 对照
12. 常见坑与修复
13. 性能与可读性
14. 什么时候不该用 reduce
15. 练习题（含答案）
16. 速查表
17. 运行配套代码

---

## 1. 先确认：你说的 reduce 是哪一个？

严格来说，**Android SDK 本身并没有一个叫 `reduce` 的核心 API**。初学者说的 “Android 的 reduce”，通常指下面几种之一：

| 场景 | 类型 | 说明 |
| --- | --- | --- |
| Kotlin 集合操作 | `Iterable<T>.reduce` | 日常开发最常见，本文重点 |
| Kotlin 协程流 | `Flow<T>.reduce` | 终端操作符，等整个流结束后得到一个值 |
| RxJava | `Observable<T>.reduce` | 老项目常见，返回 `Maybe<T>` |
| Java 8 Stream | `Stream<T>.reduce` | 偏后端/Java 代码，Android 上也可用 |
| Jetpack Compose | 没有同名 API | Compose 里常用 `remember`、`derivedStateOf` 做派生状态 |

它们的思想相同：**把一串值折叠成一个值**。但空集合行为、是否挂起、返回类型都不一样，不能混着记。

本文以 **Kotlin 集合的 `reduce`** 为主线，同时覆盖 `Flow`、RxJava、Java Stream 的差异。学完这一篇，你应该能回答：

- `reduce` 和 `fold` 到底怎么选？
- 为什么空列表调用 `reduce` 会崩溃？
- 为什么 `reduce` 里拼字符串会越来越慢？
- 为什么 `StateFlow.reduce` 永远不会返回？
- 购物车、分页合并、Compose Modifier 这些场景该怎么写？

---

## 2. 一句话理解 reduce

`reduce` 是函数式编程里的 **fold（折叠）** 操作：

> 从集合的第一个元素开始，把“当前累计值”和“下一个元素”交给一个函数，得到新的累计值；重复到没有元素为止，最后返回累计值。

生活类比：你手里有一叠账单，先拿起第一张作为“当前总额”，然后一张一张加上去，最后得到一个总金额。

```kotlin
val steps = listOf(1200, 3400, 560)

val total = steps.reduce { acc, value ->
    acc + value
}

println(total) // 5160
```

这里：

- `acc` 是 accumulator（累加器），保存“到目前为止的结果”；
- `value` 是当前正在处理的元素；
- lambda 的返回值会成为下一轮的 `acc`；
- 第一次调用时，`acc` 就是列表的第一个元素 `1200`，`value` 是第二个元素 `3400`。

注意：**`reduce` 没有初始值参数**。这是它和 `fold` 最根本的区别，也是很多崩溃的来源。

---

## 3. 函数签名与标准库源码

### 3.1 签名

Kotlin 标准库中 `Iterable` 的 `reduce` 签名是：

```kotlin
public inline fun <S, T : S> Iterable<T>.reduce(
    operation: (acc: S, T) -> S,
): S
```

拆开看：

- `Iterable<T>`：任何可迭代集合，`List`、`Set` 都行；
- `operation`：接收两个参数，`acc: S`（当前累计值）和 `T`（当前元素），返回新的 `S`；
- 返回值 `S`：最终累计值；
- `T : S`：元素类型必须是结果类型的子类型。绝大多数时候 `S = T`，所以可以简单理解成“返回和元素相同的类型”。

`inline` 意味着这个函数会在调用处内联展开，lambda 不会创建额外的函数对象，通常也不会有额外的调用开销。它还允许在 lambda 里写 `return` 从外层函数返回（非局部返回），但业务代码里不建议滥用。

### 3.2 标准库源码

`reduce` 的核心实现非常短（Kotlin stdlib `_Collections.kt`，为便于阅读做了整理）：

```kotlin
public inline fun <S, T : S> Iterable<T>.reduce(operation: (acc: S, T) -> S): S {
    val iterator = this.iterator()
    if (!iterator.hasNext()) {
        throw UnsupportedOperationException("Empty collection can't be reduced.")
    }

    var accumulator: S = iterator.next()   // 第一个元素直接当初始值
    while (iterator.hasNext()) {
        accumulator = operation(accumulator, iterator.next())
    }
    return accumulator
}
```

从源码可以读出四个关键事实：

1. **空集合直接抛异常**，lambda 一次都不会执行；
2. **第一个元素不会进入 lambda 的 `value` 参数**，它直接成为 `acc`；
3. **单元素集合直接返回该元素**，lambda 一次都不会执行；
4. 只遍历一次，时间复杂度 **O(n)**，没有中间集合分配。

这四条会反复出现在后面的坑里。

### 3.3 为什么是 `<S, T : S>` 而不是 `<T>`

因为标准库允许结果类型是元素类型的**父类型**。例如：

```kotlin
val values: List<Any> = listOf(1, "two", 3.0)

val result: Any = values.reduce { acc, value ->
    "$acc|$value"
}

println(result) // 1|two|3.0
```

这里元素类型 `T = Any`，结果类型 `S = Any`，lambda 实际返回 `String`，而 `String` 是 `Any` 的子类型，所以合法。

但反过来不行：如果元素是 `Int`，你不能让 `reduce` 直接返回 `String`，因为 `Int` 不是 `String` 的子类型，而且 `reduce` 根本没有“初始值”参数可以改变类型。需要换类型时，用 `fold`。

---

## 4. 手把手看执行过程

### 4.1 求和

```kotlin
val numbers = listOf(1, 2, 3, 4)

val sum = numbers.reduce { acc, value -> acc + value }
```

执行过程：

| 轮次 | acc（累计值） | value（当前元素） | lambda 返回 |
| --- | --- | --- | --- |
| 初始 | 1 | — | — |
| 第 1 轮 | 1 | 2 | 3 |
| 第 2 轮 | 3 | 3 | 6 |
| 第 3 轮 | 6 | 4 | 10 |
| 结束 | 10 | — | 返回 10 |

用 ASCII 图表示：

```text
[1, 2, 3, 4]
 ↓
acc = 1
  ├─ + 2 → 3
  ├─ + 3 → 6
  └─ + 4 → 10
```

### 4.2 用 runningReduce 把过程打印出来

调试 `reduce` 时，`runningReduce` 非常好用，它会保留每一步的累计值：

```kotlin
println(listOf(1, 2, 3, 4).runningReduce(Int::plus))
// [1, 3, 6, 10]
```

这比在 lambda 里 `println` 更干净，也不会影响结果。

### 4.3 第一个元素不参与 lambda

```kotlin
val visited = mutableListOf<Pair<Int, Int>>()

val result = listOf(10, 3, 2).reduce { acc, value ->
    visited += acc to value
    acc - value
}

println(result)   // 5
println(visited)  // [(10, 3), (7, 2)]
```

可以看到只有两轮：`(10, 3)` 和 `(7, 2)`。第一个元素 `10` 没有作为 `value` 出现过。

### 4.4 空集合与单元素

```kotlin
emptyList<Int>().reduce(Int::plus)
// 抛 UnsupportedOperationException: Empty collection can't be reduced.

listOf(42).reduce { acc, value -> acc + value }
// 返回 42，lambda 不会执行
```

如果你的 lambda 里有副作用（写日志、发请求、改状态），单元素集合下这些副作用不会发生。这也是“不要在 `reduce` 里做副作用”的原因之一。

---

## 5. 最小可运行示例

### 5.1 纯 Kotlin main

新建一个 `Main.kt`：

```kotlin
fun main() {
    val steps = listOf(1200, 3400, 560)

    val total = steps.reduce { acc, value -> acc + value }
    val max = steps.reduce { acc, value -> if (value > acc) value else acc }
    val text = listOf("Kotlin", "reduce", "tutorial")
        .reduce { acc, word -> "$acc $word" }

    println("总步数：$total")   // 总步数：5160
    println("最高一天：$max")   // 最高一天：3400
    println("标题：$text")      // 标题：Kotlin reduce tutorial
}
```

### 5.2 Android 项目里的单元测试

在 Android 项目里，纯 Kotlin 逻辑不需要跑模拟器，直接写 JVM 单元测试即可：

```kotlin
class ReduceBasicsTest {

    @Test
    fun `reduce 把非空列表折叠成一个值`() {
        val steps = listOf(1200, 3400, 560)

        val total = steps.reduce { acc, value -> acc + value }

        assertEquals(5160, total)
    }
}
```

运行：

```bash
./gradlew testDebugUnitTest --tests "com.zq.topnews.tutorial.ReduceTutorialTest"
```

本文配套的 `ReduceTutorialTest.kt` 覆盖了后面大部分可独立运行的核心示例；Compose / ViewModel 片段需要 Android 运行时，未放进 JVM 测试。

---

## 6. reduce 与 fold 的区别（最重要的一节）

这是初学者最容易混的一对函数。先看对比：

| 维度 | `reduce` | `fold` |
| --- | --- | --- |
| 初始值 | 没有，第一个元素当初始值 | 必须显式提供 |
| 空集合 | 抛 `UnsupportedOperationException` | 直接返回初始值 |
| 结果类型 | 必须能容纳元素类型（`T : S`） | 任意 `R`，可以和元素类型完全不同 |
| lambda 签名 | `(acc: S, T) -> S` | `(acc: R, T) -> R` |
| 第一个元素 | 直接作为 `acc`，不进入 lambda | 作为普通元素进入 lambda |
| 单元素集合 | 直接返回该元素 | 执行一次 lambda（元素与初始值） |
| 典型用途 | 非空、同类型聚合 | 通用聚合、可能为空的场景 |

### 6.1 空集合行为

```kotlin
val empty = emptyList<Int>()

empty.reduce(Int::plus)
// 崩溃：UnsupportedOperationException: Empty collection can't be reduced.

empty.fold(0, Int::plus)
// 返回 0，安全
```

**只要列表可能为空，就优先用 `fold`。** 这不是风格问题，是正确性问题。

如果业务上“空集合”应该得到 `null`，用 `reduceOrNull`：

```kotlin
val total: Int? = empty.reduceOrNull(Int::plus) // null
```

### 6.2 改变结果类型

`reduce` 的结果类型必须能容纳元素类型，`fold` 没有这个限制：

```kotlin
// 错误：reduce 没有初始值参数，编译不过
// val text = listOf(1, 2, 3).reduce("") { acc, i -> "$acc$i" }

// 正确：fold 用 String 作为初始值，结果类型是 String
val text = listOf(1, 2, 3).fold("") { acc, i -> "$acc$i" }
// "123"
```

再比如把 `List<Int>` 折叠成 `Map<String, Int>`：

```kotlin
val map = listOf("a" to 1, "b" to 2).fold(mutableMapOf<String, Int>()) { acc, (key, value) ->
    acc[key] = value
    acc
}
```

这种“结果类型和元素类型完全不同”的需求，`reduce` 做不到，必须用 `fold`。

### 6.3 用 fold 更自然的购物车

```kotlin
val subtotal = lines.fold(0L) { acc, line ->
    acc + line.unitPriceCents * line.quantity
}
```

如果硬用 `reduce`：

```kotlin
val subtotal = lines
    .map { it.unitPriceCents * it.quantity }
    .reduce(Long::plus) // 空购物车直接崩溃
```

多了一次 `map` 的中间列表分配，还要额外处理空列表，得不偿失。

### 6.4 一句话选择规则

> **能用 `fold` 解决、且列表可能为空、或者需要换结果类型时，用 `fold`；只有“确定非空 + 结果类型与元素兼容 + 用 `reduce` 更直观”时，才用 `reduce`。**

实际上在业务代码里，`fold` 的出现频率应该远高于 `reduce`。`reduce` 更适合“把一组同类型的东西合并成一个同类型的东西”，比如合并多个 `Modifier`、合并多个时间区间、合并多个分页响应。

---

## 7. reduce 家族全景

Kotlin 标准库围绕“折叠”提供了一整组函数。先把它们放在一张表里：

| API | 空集合 | 结果 | 说明 |
| --- | --- | --- | --- |
| `reduce` | 抛异常 | `S` | 无初始值，从左到右 |
| `reduceOrNull` | `null` | `S?` | 无初始值，安全版 |
| `reduceIndexed` | 抛异常 | `S` | lambda 多一个下标参数 |
| `reduceIndexedOrNull` | `null` | `S?` | 带下标的安全版 |
| `reduceRight` | 抛异常 | `S` | 从右到左折叠 |
| `runningReduce` | 空列表 | `List<S>` | 保留每一步结果，不含初始值 |
| `fold` | 返回初始值 | `R` | 有初始值，可换类型 |
| `foldIndexed` | 返回初始值 | `R` | 带下标的 fold |
| `foldRight` | 返回初始值 | `R` | 从右到左 fold |
| `runningFold` / `scan` | `[初始值]` | `List<R>` | 保留每一步结果，含初始值 |

命名规律是 `reduce` / `fold` + `Right`（方向）+ `Indexed`（下标）+ `OrNull`（空安全）。所以还有 `reduceRightOrNull`、`reduceRightIndexed`、`reduceRightIndexedOrNull`、`runningReduceIndexed`、`foldRightIndexed` 等组合，不必逐个背，用到时查一下即可。

### 7.1 reduceOrNull

```kotlin
val total = listOf(1, 2, 3).reduceOrNull(Int::plus) // 6
val empty = emptyList<Int>().reduceOrNull(Int::plus) // null
```

适合“空集合是合法输入，但我只关心结果”的场景，比如从 JSON 数组里求和：

```kotlin
val total = prices.reduceOrNull(Long::plus) ?: 0L
```

注意：`reduceOrNull` 只处理“集合为空”，不会跳过 `null` 元素。如果元素本身可空，lambda 里仍要自己处理。

### 7.2 reduceIndexed

需要下标时使用：

```kotlin
val result = listOf("a", "b", "c").reduceIndexed { index, acc, value ->
    acc + index + value
}

println(result) // a1b2c
```

第一轮 `index = 1`（因为下标 0 的元素已经作为 `acc`），`value = "b"`。

`reduceIndexedOrNull` 是它的安全版本。

### 7.3 reduceRight

从右向左折叠。注意 lambda 的参数顺序是 **`(元素, acc)`**，不是 `(acc, 元素)`：

```kotlin
val left = listOf("A", "B", "C").reduce { acc, value -> "($acc+$value)" }
val right = listOf("A", "B", "C").reduceRight { value, acc -> "($value+$acc)" }

println(left)  // ((A+B)+C)
println(right) // (A+(B+C))
```

这也是 `reduceRight` 最容易写错的地方：参数顺序和 `reduce` 相反。

`reduceRight` 只定义在 `List`（以及部分数组类型）上，因为需要从后往前遍历。

### 7.4 runningReduce

保留每一步的累计值，返回 `List`：

```kotlin
val running = listOf(1, 2, 3, 4).runningReduce(Int::plus)
println(running) // [1, 3, 6, 10]
```

空集合返回空列表，不会抛异常。它很适合调试、做“累计曲线”、展示进度。

### 7.5 fold 家族

```kotlin
val sum = listOf(1, 2, 3).fold(0, Int::plus)          // 6
val text = listOf(1, 2, 3).fold("") { acc, i -> acc + i } // "123"
val indexed = listOf("a", "b").foldIndexed("") { index, acc, value ->
    acc + index + value
} // "0a1b"
```

`foldRight` 从右往左，lambda 参数顺序是 **`(元素, acc)`**：

```kotlin
val result = listOf("A", "B", "C").foldRight("") { value, acc ->
    "($value+$acc)"
}
// (A+(B+(C+)))
```

### 7.6 runningFold 与 scan

`scan` 是 `runningFold` 的别名，会**先输出初始值**，再输出每一步结果：

```kotlin
val running = listOf(1, 2, 3).runningFold(0, Int::plus)
val scanned = listOf(1, 2, 3).scan(0, Int::plus)

println(running) // [0, 1, 3, 6]
println(scanned) // [0, 1, 3, 6]
```

对比：

```kotlin
listOf(1, 2, 3).runningReduce(Int::plus)   // [1, 3, 6]
listOf(1, 2, 3).scan(0, Int::plus)         // [0, 1, 3, 6]
```

`scan` 一定包含初始值；`runningReduce` 一定不包含初始值（因为它没有初始值）。

### 7.7 Sequence 与数组

`Sequence` 上也有 `reduce`、`reduceOrNull`、`runningReduce`、`fold`。区别是 `Sequence` 是惰性的，但 `reduce` 是**终端操作**，调用时才会真正遍历：

```kotlin
val sum = (1..1_000)
    .asSequence()
    .filter { it % 2 == 0 }
    .reduce(Int::plus)
```

数组同样支持：

```kotlin
val sum = intArrayOf(1, 2, 3).reduce(Int::plus)
```

原生数组（`IntArray`、`LongArray` 等）有专门的重载，可以避免装箱，性能更好。

### 7.8 Flow 版本

`Flow` 的 `reduce` 是挂起的终端操作符，签名和集合版类似，但 lambda 可以是 `suspend`：

```kotlin
public suspend fun <S, T : S> Flow<T>.reduce(
    operation: suspend (accumulator: S, value: T) -> S,
): S
```

```kotlin
val total = flowOf(1, 2, 3, 4).reduce(Int::plus) // 10
```

空 `Flow` 会抛 `NoSuchElementException("Empty flow can't be reduced")`：

```kotlin
emptyFlow<Int>().reduce(Int::plus)
// NoSuchElementException: Empty flow can't be reduced
```

安全写法：

```kotlin
emptyFlow<Int>().fold(0, Int::plus) // 0
```

`Flow` 还有：

```kotlin
flowOf(1, 2, 3).scan(0, Int::plus)          // 发射 0, 1, 3, 6
flowOf(1, 2, 3).runningReduce(Int::plus)    // 发射 1, 3, 6
```

`scan` 和 `runningReduce` 返回的是新的 `Flow`（中间操作符），不是最终值。

---

## 8. 顺序、结合律与并行

### 8.1 reduce 是左结合

对于有序集合，`reduce` 严格从左到右：

```kotlin
val left = listOf(10, 3, 2).reduce { acc, value -> acc - value }
// (10 - 3) - 2 = 5
```

如果操作不满足结合律，结果会和你想象的不同：

```kotlin
val right = listOf(10, 3, 2).reduceRight { value, acc -> value - acc }
// 10 - (3 - 2) = 9
```

减法、除法既不满足交换律，也不满足结合律；字符串拼接、矩阵乘法满足结合律，但不满足交换律。写 `reduce` 前先确认你的操作是否依赖顺序。

### 8.2 HashSet 的顺序不确定

`listOf` 保证插入顺序，`setOf` 通常是 `LinkedHashSet`，也保证顺序；但 `hashSetOf` 不保证：

```kotlin
val set = hashSetOf("a", "b", "c")
val text = set.reduce { acc, value -> "$acc$value" }
// 迭代顺序由哈希值决定，不保证等于插入顺序
```

如果结果依赖顺序，先 `sorted()` 或改用 `List`。

### 8.3 Kotlin 标准库不会自动并行

Kotlin 的 `reduce` 是单线程顺序遍历，不存在“自动并行”。如果你用 Java 的 `parallelStream().reduce(...)`，必须满足：

- 操作满足**结合律**；
- 不依赖元素顺序；
- lambda 无状态、不修改共享变量。

否则并行结果可能和顺序结果不一致。Android 上大多数聚合数据量不大，没必要上并行；真需要并行时，先做基准测试。

---

## 9. Android 实战场景

### 9.1 购物车结算：为什么这里应该用 fold

先定义数据模型。金额统一用 `Long` 表示“分”，不要用 `Double`：

```kotlin
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
```

结算逻辑：

```kotlin
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
```

测试：

```kotlin
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
```

为什么不用 `Double`：

```kotlin
println(0.1 + 0.2) // 0.30000000000000004
```

金额用浮点数会出现“少一分/多一分”的问题。用整数分，或者用 `BigDecimal`（更重，适合金融级计算）。

为什么不用 `reduce`：

- 购物车可能为空；
- 需要 `Long` 初始值；
- `reduce` 没有初始值参数。

如果一定要用 `reduce`，只能先 `map` 再 `reduce`，并且要处理空列表，代码更长、更慢：

```kotlin
val subtotal = lines
    .map { it.unitPriceCents * it.quantity }
    .reduceOrNull(Long::plus)
    ?: 0L
```

这行本身没错，但多了一次中间列表分配。数据量大时，`fold` 更直接。

### 9.2 合并分页响应：reduce 的合适场景

假设接口分页返回：

```kotlin
data class Article(val title: String, val readMinutes: Int)

data class FeedPage<T>(
    val items: List<T>,
    val nextCursor: String?,
)
```

把多页合并成一页：

```kotlin
fun <T> mergePages(pages: List<FeedPage<T>>): FeedPage<T> {
    require(pages.isNotEmpty()) { "pages must not be empty" }

    return pages.reduce { acc, page ->
        FeedPage(
            items = acc.items + page.items,
            nextCursor = page.nextCursor,
        )
    }
}
```

这是 `reduce` 的典型适用场景：

- `pages` 在调用前已经保证非空；
- 结果类型 `FeedPage<T>` 和元素类型相同；
- 语义就是“把一页一页合并成最终一页”。

但注意 `acc.items + page.items` 每次都会创建新列表，总复杂度是 **O(n²)**。分页通常只有几页到几十页，问题不大；如果页数很多，用 `fold` + 可变列表：

```kotlin
fun <T> mergePagesEfficiently(pages: List<FeedPage<T>>): FeedPage<T> {
    val items = pages.fold(mutableListOf<T>()) { acc, page ->
        acc.apply { addAll(page.items) }
    }
    return FeedPage(
        items = items,
        nextCursor = pages.lastOrNull()?.nextCursor,
    )
}
```

或者更简单：

```kotlin
val items = pages.flatMap { it.items }
```

**结论：`reduce` 适合“页数少、语义直观”的合并；追求性能时用 `fold` + `addAll` 或 `flatMap`。**

### 9.3 合并时间区间

把多个区间合并成一个“总区间”（包围盒）：

```kotlin
data class Span(val start: Int, val end: Int)

fun List<Span>.mergeSpans(): Span =
    reduce { acc, span ->
        Span(
            start = minOf(acc.start, span.start),
            end = maxOf(acc.end, span.end),
        )
    }
```

```kotlin
val span = listOf(Span(10, 20), Span(5, 15), Span(30, 40)).mergeSpans()
// Span(start=5, end=40)
```

这里 `reduce` 很自然：区间列表非空，结果是同类型的 `Span`。

注意：这计算的是**包围盒**，不是“把重叠区间合并成不相交区间”。后者需要先排序再扫描，逻辑更复杂。

### 9.4 Compose Modifier 链

Compose 里经常要把一组 `Modifier` 按顺序叠加（为简洁省略 Compose 相关 import）：

```kotlin
fun buildCardModifier(extra: List<Modifier>): Modifier {
    val base = Modifier
        .padding(16.dp)
        .background(Color.White)

    // 非空、同类型，用 reduce 很自然
    return (listOf(base) + extra).reduce { acc, modifier ->
        acc.then(modifier)
    }
}
```

`Modifier.then(other)` 的语义是“先应用 `this`，再应用 `other`”，顺序会影响 `padding`、`background`、`clickable` 的最终效果。`reduce` 的左到右顺序正好对应“从外到内/从先到后”的叠加。

如果 `extra` 可能为空，`listOf(base) + extra` 至少有一个元素，所以这里不会触发空集合异常。

也可以写成方法引用：

```kotlin
val merged = (listOf(base) + extra).reduce(Modifier::then)
```

### 9.5 解析 JSON 并聚合

Android 里常用 `org.json` 解析小段 JSON：

```kotlin
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

println(total) // 5970
```

这里用 `reduceOrNull` 是因为 JSON 数组可能为空；用 `map` 先取出金额，再折叠。

生产代码里更推荐用 `kotlinx.serialization` 或 Moshi，把 JSON 直接反序列化成 `List<Item>`，然后用 `sumOf` 或 `fold`：

```kotlin
val total = items.sumOf { it.priceCents }
```

`sumOf` 本质也是折叠，但语义更明确，还能避免手写 lambda。

### 9.6 ViewModel + StateFlow

在 ViewModel 里，聚合逻辑通常放在 `map` 中，再通过 `stateIn` 暴露成 UI 状态：

```kotlin
class CheckoutViewModel : ViewModel() {

    private val lines = MutableStateFlow<List<CartLine>>(emptyList())

    val summary: StateFlow<CartSummary> = lines
        .map { summarizeCart(it) }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = CartSummary(0, 0, 0, 0),
        )
}
```

注意：这里用的是 `Flow.map`，不是 `reduce`。因为 `StateFlow` 永远不会结束，调用 `reduce` 会一直等下去。

### 9.7 Flow 累计和：scan 的用武之地

如果你要展示“累计收入”“累计步数”，并且希望每来一个新值就更新 UI：

```kotlin
val runningTotal: StateFlow<Long> = orders
    .scan(0L) { acc, order -> acc + order.amountCents }
    .stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = 0L,
    )
```

`scan` 会先发射 `0`，然后每来一个订单发射一次新的累计值。UI 可以持续更新，而不是等所有订单结束。

如果不想让 UI 先看到初始值 `0`，可以 `.drop(1)`。

### 9.8 生成分享文本

把文章列表拼成分享文案：

```kotlin
fun buildShareText(articles: List<Article>): String =
    articles.fold(StringBuilder("我在 TopNews 看到这些内容：\n")) { sb, article ->
        sb.append("· ").append(article.title).append('\n')
    }.toString()
```

也可以用 `joinToString`，更简洁：

```kotlin
val text = articles.joinToString(
    prefix = "我在 TopNews 看到这些内容：\n",
    separator = "\n",
) { "· ${it.title}" }
```

**能 `joinToString` 就不要手写 `reduce` 拼字符串。**

### 9.9 Android 场景选择速查

| 需求 | 推荐写法 |
| --- | --- |
| 购物车总价 | `fold(0L)` 或 `sumOf` |
| 最大/最小 | `maxOrNull` / `minOrNull` |
| 拼接文本 | `joinToString` |
| 合并分页（少量） | `reduce` |
| 合并分页（大量） | `flatMap` / `fold` + `addAll` |
| 合并区间 | `reduce` |
| Compose Modifier | `reduce(Modifier::then)` |
| 解析 JSON 后求和 | `map` + `reduceOrNull` 或 `sumOf` |
| 累计和 UI | `Flow.scan` |
| 统计数量 | `count` |

---

## 10. Kotlin Flow 中的 reduce

### 10.1 终端操作符

`Flow.reduce` 是**终端操作符**：调用它会启动收集，直到上游完成，最后返回一个值。它和 `collect`、`toList`、`first` 是同一类操作。

```kotlin
suspend fun total(steps: Flow<Int>): Int =
    steps.reduce(Int::plus)
```

如果上游是 `flowOf(1, 2, 3)`，返回 `6`；如果上游是空流，抛 `NoSuchElementException`。

### 10.2 空流与 fold

```kotlin
emptyFlow<Int>().reduce(Int::plus)
// NoSuchElementException: Empty flow can't be reduced

emptyFlow<Int>().fold(0, Int::plus)
// 0
```

和集合版一样：**可能为空就用 `fold`**。

### 10.3 StateFlow 不能 reduce

这是 Android 里最容易踩的 Flow 坑：

```kotlin
// 错误：StateFlow 永远不会完成，reduce 永远不返回
viewModel.uiState.reduce { acc, state -> state }
```

`StateFlow` 是热流，本身永远不会完成（即使没有订阅者）。`reduce`/`fold` 都是等待流结束的终端操作符，所以会永远挂起。

正确做法：

- 只要当前值：`uiState.value` 或 `uiState.first()`；
- 持续观察：`collect` / `collectLatest`；
- 派生状态：`map` / `combine` / `stateIn`；
- 累计：`scan`。

### 10.4 scan 与 runningReduce

```kotlin
val ticks = flowOf(1, 2, 3)

ticks.scan(0, Int::plus)          // 0, 1, 3, 6
ticks.runningReduce(Int::plus)    // 1, 3, 6
```

- `scan` 一定发射初始值；
- `runningReduce` 不发射初始值；
- 两者都返回 `Flow`，是冷流，每次收集都会重新计算。

在 ViewModel 里通常配合 `stateIn`：

```kotlin
val progress: StateFlow<Int> = ticks
    .scan(0, Int::plus)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
```

### 10.5 取消与异常

`Flow.reduce` 的收集是可取消的。页面销毁、`viewModelScope` 取消时，`reduce` 会抛 `CancellationException` 并停止。

但要注意：如果 `operation` 内部是长时间 CPU 计算，取消只会在下一个发射点生效。需要及时响应取消时，在循环/计算里调用 `currentCoroutineContext().ensureActive()`。

### 10.6 什么时候用 Flow.reduce

适合：

- 上游是有限流（`flowOf`、`list.asFlow()`、`channelFlow` 完成后）；
- 你需要等所有值到齐后算一个最终结果；
- 例如：读取分页接口的所有页，最后合并；统计一次上传的所有分片大小。

不适合：

- `StateFlow`、`SharedFlow` 等热流；
- 需要边接收边更新 UI（用 `scan`）；
- 只需要第一个值（用 `first`）。

---

## 11. RxJava 与 Java Stream 对照

### 11.1 RxJava

老 Android 项目可能用 RxJava：

```kotlin
Observable.just(1, 2, 3)
    .reduce { a, b -> a + b }
    .subscribe { total -> println(total) } // 6
```

区别：

- `reduce` 返回 `Maybe<T>`，空流时 `Maybe` 直接完成，不发射值；
- `reduceWith` 提供初始值，返回 `Single<R>`：

```kotlin
Observable.just(1, 2, 3)
    .reduceWith({ 0 }) { acc, value -> acc + value }
    .subscribe { total -> println(total) } // 6
```

- `scan` 发射每一步累计值：

```kotlin
Observable.just(1, 2, 3)
    .scan { a, b -> a + b }
    .subscribe { println(it) } // 1, 3, 6
```

RxJava 的 `scan` 默认不带初始值；`scanWith` 才带初始值。

### 11.2 Java Stream

Java 的 `Stream.reduce` 有三个重载：

```java
// 1. 无初始值，返回 Optional
Optional<Integer> sum1 = list.stream().reduce(Integer::sum);

// 2. 有初始值，返回 T
int sum2 = list.stream().reduce(0, Integer::sum);

// 3. 并行版本：初始值 + 累加器 + 合并器
int sum3 = list.parallelStream().reduce(
    0,
    Integer::sum,
    Integer::sum
);
```

和 Kotlin 的对应关系：

| Java | Kotlin |
| --- | --- |
| `reduce(BinaryOperator)` → `Optional` | `reduceOrNull` |
| `reduce(identity, BinaryOperator)` | `fold(initial, operation)` |
| `reduce(identity, accumulator, combiner)` | 无直接对应，Kotlin 不自动并行 |

Java 的 `Optional` 对应 Kotlin 的 `reduceOrNull`；Java 有初始值的版本对应 `fold`；Kotlin 的 `reduce` 在空集合时直接抛异常，所以用之前要确认非空。

---

## 12. 常见坑与修复

### 坑 1：空集合调用 reduce

```kotlin
val scores = emptyList<Int>()
val total = scores.reduce(Int::plus)
// UnsupportedOperationException: Empty collection can't be reduced.
```

修复：

```kotlin
val total = scores.fold(0, Int::plus)          // 默认 0
val maybe = scores.reduceOrNull(Int::plus)     // null
```

### 坑 2：想用 reduce 换结果类型

```kotlin
// 编译错误：reduce 没有初始值参数
// val text = listOf(1, 2, 3).reduce("") { acc, i -> "$acc$i" }
```

修复：

```kotlin
val text = listOf(1, 2, 3).fold("") { acc, i -> "$acc$i" }
```

### 坑 3：字符串 += 导致 O(n²)

```kotlin
val bad = names.reduce { acc, name -> "$acc, $name" }
```

每次拼接都会创建新字符串并复制之前的内容，n 个元素总复制量是 O(n²)。元素多时明显卡顿。

修复：

```kotlin
val good = names.joinToString(", ")

// 或者需要复杂逻辑时用 StringBuilder
val sb = names.fold(StringBuilder()) { acc, name ->
    if (acc.isNotEmpty()) acc.append(", ")
    acc.append(name)
}
```

### 坑 4：用 reduce 修改第一个元素

```kotlin
val lists = listOf(
    mutableListOf(1, 2),
    mutableListOf(3, 4),
)

val merged = lists.reduce { acc, list ->
    acc.apply { addAll(list) } // 修改了 lists[0]！
}
```

`reduce` 的初始 `acc` 就是第一个元素本身。如果它是可变对象，`apply { addAll(...) }` 会直接修改输入集合，产生难以排查的副作用。

修复：

```kotlin
val merged = lists.fold(mutableListOf<Int>()) { acc, list ->
    acc.apply { addAll(list) }
}
```

`fold` 的初始值是全新的 `mutableListOf()`，不会污染输入。

### 坑 5：在 reduce 里做副作用/IO

```kotlin
// 不推荐
val total = items.reduce { acc, item ->
    analytics.log(item)      // 副作用
    Thread.sleep(10)         // 阻塞
    acc + item.price
}
```

`reduce` 是纯聚合操作，不应该承担日志、网络、数据库、UI 更新等职责。副作用会让单元素集合、异常、重试等场景的行为变得不可预测。

需要副作用时，用普通 `for` 循环，或者先 `map` 再聚合。

### 坑 6：整数溢出

```kotlin
val total = Int.MAX_VALUE + 1 // -2147483648
```

用 `Long`：

```kotlin
val total = items.sumOf { it.priceCents }        // Long 重载
val total2 = items.fold(0L) { acc, item -> acc + item.priceCents }
```

注意 `sumOf` 也有 `Int` 重载，元素是 `Int` 时仍可能溢出；需要更大范围时显式 `toLong()`。

### 坑 7：浮点金额

```kotlin
println(0.1 + 0.2) // 0.30000000000000004
```

金额统一用整数分（`Long`）或 `BigDecimal`，不要用 `Double`/`Float` 做累加。

### 坑 8：操作不满足结合律，却用了并行

```kotlin
// 并行 reduce 要求操作满足结合律
list.parallelStream().reduce("", (a, b) -> a + b, (a, b) -> a + b)
```

字符串拼接满足结合律，所以这个例子没问题；但减法、除法、某些自定义合并（比如“取平均值”）不满足结合律，并行结果可能错误。

### 坑 9：StateFlow 上调用 reduce

```kotlin
// 永远不返回
uiState.reduce { acc, state -> state }
```

修复：用 `value`、`first()`、`collect` 或 `scan`。

### 坑 10：大集合上反复 `acc + list`

```kotlin
val pageLists: List<List<Article>> = loadPages()
val all: List<Article> = pageLists.reduce { acc, page -> acc + page }
```

每次 `+` 都创建新列表，总复杂度 O(n²)。

修复：

```kotlin
val all: List<Article> = pageLists.flatMap { it }
// 或 fold + addAll
```

### 坑 11：用 reduce 做“提前退出”

```kotlin
val firstNegative = numbers.reduce { acc, n ->
    if (n < 0) return n else acc
}
```

这段代码在函数体内能编译（因为 `reduce` 是 `inline`），但语义很怪，而且 `return` 会从外层函数返回，容易让调用者困惑。

修复：

```kotlin
val firstNegative = numbers.firstOrNull { it < 0 }
```

需要“找到就停”的场景，用 `firstOrNull`、`any`、`find` 或普通循环，不要用 `reduce`。

### 坑 12：reduceRight 参数顺序写反

```kotlin
// 错误：把 acc 写在第一个
list.reduceRight { acc, value -> ... }
```

正确顺序是 `(value, acc)`：

```kotlin
list.reduceRight { value, acc -> "($value+$acc)" }
```

### 坑 13：误以为 reduceOrNull 会跳过 null 元素

```kotlin
val names = listOf<String?>("A", null, "C")
val joined = names.reduceOrNull { acc, name -> "${acc ?: ""}${name ?: ""}" }
```

`reduceOrNull` 只处理“集合为空”，不会过滤 `null` 元素。要跳过 `null`：

```kotlin
val joined = names.filterNotNull().joinToString("")
```

### 坑 14：在 Compose 组合中做重计算

```kotlin
@Composable
fun CartBadge(lines: List<CartLine>) {
    // 每次重组都会重新计算
    val count = lines.fold(0) { acc, line -> acc + line.quantity }
    Text("$count")
}
```

用 `remember` 缓存：

```kotlin
val count = remember(lines) {
    lines.fold(0) { acc, line -> acc + line.quantity }
}
```

如果 `lines` 是 `State<List<...>>`，可以用 `derivedStateOf`。但不要过度优化：小列表的 `fold` 很快，先保证正确性。

### 坑 15：把 reduce 当成 map/filter/groupBy

```kotlin
// 不推荐：手动 fold 实现分组
val grouped = items.fold(mutableMapOf<String, MutableList<Item>>()) { acc, item ->
    acc.getOrPut(item.category) { mutableListOf() }.add(item)
    acc
}
```

用 `groupBy` 更清晰：

```kotlin
val grouped = items.groupBy { it.category }
```

`reduce` / `fold` 只适合“折叠成一个值”，不适合替代集合变换操作。分组本身用 `groupBy` 就好。

### 坑 16：忽略 reduce 的异常信息

`UnsupportedOperationException: Empty collection can't be reduced.` 这个堆栈可能出现在很深的位置。给调用方一个明确的业务异常：

```kotlin
fun totalOrThrow(lines: List<CartLine>): Long {
    require(lines.isNotEmpty()) { "购物车不能为空" }
    return lines.reduce { acc, line -> acc + line.unitPriceCents * line.quantity }
}
```

或者直接用 `fold`，从根上避免这个异常。

---

## 13. 性能与可读性

### 13.1 时间复杂度

`reduce` 只遍历一次，O(n)。和手写 `for` 循环等价：

```kotlin
var acc = list.first()
for (i in 1 until list.size) {
    acc = operation(acc, list[i])
}
```

### 13.2 inline 与装箱

`reduce` 是 `inline`，lambda 不会分配对象。但泛型集合的元素可能是装箱类型：

```kotlin
val list = listOf(1, 2, 3) // List<Int>，元素是装箱的 Integer
list.reduce(Int::plus)
```

对性能敏感的数值计算，用原生数组：

```kotlin
val array = intArrayOf(1, 2, 3)
array.reduce(Int::plus) // 无装箱
```

### 13.3 避免中间集合

```kotlin
// 不好：先 map 出中间列表，再 reduce
val total = items.map { it.price * it.count }.reduce(Int::plus)

// 好：一次遍历
val total = items.fold(0) { acc, item -> acc + item.price * item.count }
```

`map + reduce` 会多分配一个列表；`fold` 一步到位。

### 13.4 字符串拼接

用 `StringBuilder` 或 `joinToString`，不要用 `+=`：

```kotlin
val text = items.joinToString(", ") { it.name }

val sb = StringBuilder()
for (item in items) {
    sb.append(item.name).append(", ")
}
```

### 13.5 Sequence 不是万能的

`Sequence` 对多次链式操作（`map`、`filter`）能避免中间集合，但有额外的迭代器开销。对很小的列表，直接用 `List` 更快。只有数据量大、链式操作多时，`Sequence` 才有优势。

### 13.6 不要过早优化

大多数 Android 列表只有几十到几百个元素，`fold`/`reduce` 的差异可以忽略。先保证正确、可读，再用 Macrobenchmark 或 JMH 证明瓶颈。

---

## 14. 什么时候不该用 reduce

| 需求 | 更好的选择 |
| --- | --- |
| 求和 | `sumOf` |
| 最大值/最小值 | `maxOrNull` / `minOrNull` |
| 拼接字符串 | `joinToString` / `buildString` |
| 展平嵌套列表 | `flatMap` |
| 分组 | `groupBy` |
| 转 Map | `associateBy` / `associate` |
| 判断存在 | `any` / `none` |
| 找第一个 | `firstOrNull` / `find` |
| 统计数量 | `count` |
| 去重 | `distinct` |
| 排序 | `sortedBy` |
| 需要提前退出 | 普通循环 / `firstOrNull` |

决策树：

```text
需要把 List 变成一个值？
├─ 只是求和/最大/拼接/分组
│   → sumOf / maxOrNull / joinToString / groupBy
├─ 列表可能为空？
│   ├─ 要默认值 → fold(initial)
│   └─ 允许 null → reduceOrNull
├─ 结果类型和元素类型不同
│   → fold
├─ 需要下标 / 从右往左
│   → reduceIndexed / reduceRight
├─ 需要每一步结果
│   → runningReduce / scan
├─ 需要提前退出
│   → firstOrNull / any / 普通循环
└─ 非空 + 同类型 + 语义就是“合并”
    → reduce
```

---

## 15. 练习题（含答案）

### 练习 1：用 reduce 求最大值

不用 `maxOrNull`，用 `reduce` 求 `listOf(3, 1, 4, 1, 5, 9)` 的最大值。

```kotlin
val numbers = listOf(3, 1, 4, 1, 5, 9)

val max = numbers.reduce { acc, value ->
    if (value > acc) value else acc
}

println(max) // 9
```

思考：如果列表可能为空，应该怎么写？

```kotlin
val max = numbers.reduceOrNull { acc, value ->
    if (value > acc) value else acc
} ?: 0
```

### 练习 2：把 Pair 列表折叠成 Map

`List<Pair<String, Int>>` 中可能有重复 key，重复时把值相加。

```kotlin
val pairs = listOf("a" to 1, "b" to 2, "a" to 3)

val map = pairs.fold(mutableMapOf<String, Int>()) { acc, (key, value) ->
    acc[key] = (acc[key] ?: 0) + value
    acc
}

println(map) // {a=4, b=2}
```

### 练习 3：安全地计算平均分

```kotlin
fun average(scores: List<Int>): Double? =
    scores.reduceOrNull(Int::plus)?.toDouble()?.div(scores.size)

println(average(listOf(80, 90, 100))) // 90.0
println(average(emptyList()))         // null
```

### 练习 4：统计评论树总数

```kotlin
data class Comment(
    val text: String,
    val replies: List<Comment> = emptyList(),
)

fun Comment.totalCount(): Int =
    1 + replies.sumOf { it.totalCount() }

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

println(comments.sumOf { it.totalCount() }) // 5
```

这里用 `sumOf` 比 `reduce` 更清晰；递归 + 聚合是 `sumOf` 的强项。

### 练习 5：Flow 累计和

从 `Flow<Int>` 计算累计和，并让 UI 先显示 0。

```kotlin
val runningTotal: Flow<Int> = ticks.scan(0, Int::plus)
```

如果要转成 `StateFlow`：

```kotlin
val state: StateFlow<Int> = ticks
    .scan(0, Int::plus)
    .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0)
```

### 练习 6：找 bug

```kotlin
fun totalPrice(lines: List<Line>): Long =
    lines.map { it.priceCents * it.quantity }.reduce(Long::plus)
```

问题：`lines` 为空时抛 `UnsupportedOperationException`；而且多了一次 `map` 分配。

修复：

```kotlin
fun totalPrice(lines: List<Line>): Long =
    lines.fold(0L) { acc, line ->
        acc + line.priceCents * line.quantity
    }
```

### 练习 7：修复字符串拼接

```kotlin
// 原代码
fun shareText(items: List<Item>): String =
    items.reduce { acc, item -> "$acc\n${item.title}" }
```

问题：

- 空列表崩溃；
- 字符串 `+=` O(n²)。

修复：

```kotlin
fun shareText(items: List<Item>): String =
    items.joinToString("\n") { it.title }
```

### 练习 8：合并多个 Modifier

```kotlin
fun combined(modifiers: List<Modifier>): Modifier =
    modifiers.reduce(Modifier::then)
```

思考：

- `modifiers` 为空会崩溃；
- 需要非空保证时，调用方先 `require(modifiers.isNotEmpty())`；
- 或者提供一个基础 `Modifier`：

```kotlin
fun combined(modifiers: List<Modifier>): Modifier =
    modifiers.fold(Modifier) { acc, modifier -> acc.then(modifier) }
```

---

## 16. 速查表

### 集合

```kotlin
// 无初始值；空集合抛异常
list.reduce { acc, value -> acc + value }

// 无初始值；空集合返回 null
list.reduceOrNull { acc, value -> acc + value }

// 带下标
list.reduceIndexed { index, acc, value -> acc + value }

// 从右往左；参数顺序是 (value, acc)
list.reduceRight { value, acc -> value + acc }

// 保留每一步；空集合返回空列表
list.runningReduce { acc, value -> acc + value }

// 有初始值；空集合返回初始值；可换类型
list.fold(initial) { acc, value -> acc + value }

// 带下标的 fold
list.foldIndexed(initial) { index, acc, value -> acc + value }

// 从右往左的 fold；(value, acc)
list.foldRight(initial) { value, acc -> value + acc }

// 保留每一步，包含初始值
list.runningFold(initial) { acc, value -> acc + value }
list.scan(initial) { acc, value -> acc + value }
```

### Flow

```kotlin
// 终端操作；空流抛 NoSuchElementException
flow.reduce { acc, value -> acc + value }

// 有初始值；空流返回初始值
flow.fold(initial) { acc, value -> acc + value }

// 中间操作；发射初始值和每一步
flow.scan(initial) { acc, value -> acc + value }

// 中间操作；不发射初始值
flow.runningReduce { acc, value -> acc + value }
```

### 一句话

- 可能为空 → `fold` / `reduceOrNull`
- 要换类型 → `fold`
- 要下标 → `reduceIndexed`
- 要从右 → `reduceRight`（注意参数顺序）
- 要看过程 → `runningReduce` / `scan`
- 热流（StateFlow）→ 不要 `reduce`
- 简单求和/最大/拼接 → 用专门函数

---

## 17. 运行配套代码

配套测试文件：

```text
app/src/test/java/com/zq/topnews/tutorial/ReduceTutorialTest.kt
```

运行：

```bash
./gradlew testDebugUnitTest --tests "com.zq.topnews.tutorial.ReduceTutorialTest"
```

预期结果：

```text
BUILD SUCCESSFUL
```

测试覆盖了：

- `reduce` 基本用法与空集合行为
- `reduceOrNull` / `reduceIndexed` / `reduceRight`
- `runningReduce` / `runningFold` / `scan`
- `reduce` 与 `fold` 的类型、初始值差异
- 购物车结算、分页合并、时间区间合并
- JSON 聚合
- Flow 的 `reduce` / `fold` / `scan` / `runningReduce`
- 练习题参考答案

建议你自己改几个断言、加几个 `println`，观察每一步的 `acc`，这比只看文章理解得更快。

---

## 附：标准库源码速览

`fold`：

```kotlin
public inline fun <T, R> Iterable<T>.fold(
    initial: R,
    operation: (acc: R, T) -> R,
): R {
    var accumulator = initial
    for (element in this) {
        accumulator = operation(accumulator, element)
    }
    return accumulator
}
```

`reduceOrNull`：

```kotlin
public inline fun <S, T : S> Iterable<T>.reduceOrNull(
    operation: (acc: S, T) -> S,
): S? {
    val iterator = this.iterator()
    if (!iterator.hasNext()) return null
    var accumulator: S = iterator.next()
    while (iterator.hasNext()) {
        accumulator = operation(accumulator, iterator.next())
    }
    return accumulator
}
```

`runningReduce`：

```kotlin
public inline fun <S, T : S> Iterable<T>.runningReduce(
    operation: (acc: S, T) -> S,
): List<S> {
    val iterator = this.iterator()
    if (!iterator.hasNext()) return emptyList()

    var accumulator: S = iterator.next()
    val result = ArrayList<S>()
    result.add(accumulator)

    while (iterator.hasNext()) {
        accumulator = operation(accumulator, iterator.next())
        result.add(accumulator)
    }
    return result
}
```

`reduceRight`：

```kotlin
public inline fun <S, T : S> List<T>.reduceRight(
    operation: (T, acc: S) -> S,
): S {
    val iterator = listIterator(size)
    if (!iterator.hasPrevious()) {
        throw UnsupportedOperationException("Empty list can't be reduced.")
    }
    var accumulator: S = iterator.previous()
    while (iterator.hasPrevious()) {
        accumulator = operation(iterator.previous(), accumulator)
    }
    return accumulator
}
```

> 源码会随 Kotlin 版本略有调整，但“第一个元素作为初始值、空集合抛异常、单次遍历”的语义始终不变。阅读源码时以你项目实际使用的 Kotlin 版本为准。

---

**最后一句总结：**

`reduce` 不是“更高级的 for 循环”，而是一个有明确适用边界的折叠操作。**默认优先用 `fold` 和专门的聚合函数（`sumOf`、`maxOrNull`、`joinToString`），只在“确定非空、类型一致、语义就是合并”时用 `reduce`。** 这样写出来的 Android 代码更安全，也更容易读。
