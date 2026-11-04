package com.zq.topnews.data

/** The kind of notification shown on the message channel. */
enum class MessageKind(val label: String, val description: String) {
    LIKE("点赞", "有人赞了你的评论"),
    COMMENT("评论", "有人回复了你的评论"),
    FANS("粉丝", "有新的粉丝关注了你"),
    MENTION("@我", "有人在评论里提到了你"),
    SYSTEM("系统通知", "账号与安全提醒"),
    PRIVATE("私信", "你还没有新的私信"),
}

/** A row on the message channel. */
data class MessageItem(
    val kind: MessageKind,
    val subtitle: String,
    val time: String,
)

val sampleMessages: List<MessageItem> = listOf(
    MessageItem(MessageKind.LIKE, "你的评论「说得很有道理」获得 12 个赞", "10 分钟前"),
    MessageItem(MessageKind.COMMENT, "用户 3****6 回复了你：同感", "25 分钟前"),
    MessageItem(MessageKind.FANS, "用户 8****2 关注了你", "1 小时前"),
    MessageItem(MessageKind.MENTION, "有人在「国产大模型」话题下提到了你", "3 小时前"),
    MessageItem(MessageKind.SYSTEM, "你的账号本周登录 2 次，一切正常", "昨天"),
    MessageItem(MessageKind.PRIVATE, "暂无新的私信", "2 天前"),
)
