package com.zq.topnews.data

/** A single video card on the video channel. */
data class VideoItem(
    val id: String,
    val title: String,
    val author: String,
    val plays: String,
    val duration: String,
    val tone: Int,
)

val sampleVideos: List<VideoItem> = listOf(
    VideoItem("v01", "低空经济园区开园现场：无人机编队表演", "深圳卫视", "12.3 万次播放", "02:41", 0),
    VideoItem("v02", "三分钟看懂国产大模型为什么越来越便宜", "科技前线", "8.7 万次播放", "03:15", 1),
    VideoItem("v03", "载人飞船逃逸试验全程回放", "中国航天", "25.1 万次播放", "05:02", 2),
    VideoItem("v04", "公积金新政解读：额度怎么算", "财经小课堂", "4.2 万次播放", "04:37", 3),
    VideoItem("v05", "秋粮收购一线：粮库里的忙碌一天", "人民视频", "1.9 万次播放", "06:18", 4),
    VideoItem("v06", "固态电池实验室探访：能量密度怎么提升的", "光明科普", "7.6 万次播放", "08:09", 5),
    VideoItem("v07", "城市马拉松赛前训练指南", "跑步研究所", "3.4 万次播放", "02:58", 6),
    VideoItem("v08", "深潜两万海里：科考船上的 60 天", "央视纪实", "15.8 万次播放", "11:24", 7),
)
