# TopNews

一个参考「今日头条」信息流布局的 Android 新闻资讯示例 App，使用 Kotlin + Jetpack Compose 实现。

- **包名**：`com.zq.topnews`
- **最低版本**：Android 7.0（API 24），编译/目标版本 API 35
- **UI**：Jetpack Compose + Material 3，接近今日头条的首页信息流、频道切换、文章详情、视频双列宫格、消息列表和个人中心

## 功能

| 模块 | 内容 |
| --- | --- |
| 首页 | 红色品牌搜索栏、推荐/热点/科技/体育/财经/娱乐频道切换、头条大图卡片 + 图文列表、评论数与发布时间 |
| 文章详情 | 沉浸式阅读页、分享/收藏操作、正文分段排版、底部评论输入栏 |
| 视频 | 双列宫格、播放按钮遮罩、时长角标、播放量 |
| 消息 | 点赞 / 评论 / 粉丝 / @我 / 系统通知 / 私信 六类消息入口 |
| 我的 | 头像与登录入口、动态/关注/粉丝统计、收藏/历史/商城/钱包快捷入口、设置与帮助菜单 |

返回键逻辑：文章详情页按返回回到信息流，不会直接退出 App。

## 截图

| 首页 | 文章详情 | 视频 |
| --- | --- | --- |
| ![首页](docs/screenshots/home.png) | ![文章详情](docs/screenshots/article.png) | ![视频](docs/screenshots/video.png) |

| 频道切换 | 消息 | 我的 |
| --- | --- | --- |
| ![科技频道](docs/screenshots/category-tech.png) | ![消息](docs/screenshots/message.png) | ![我的](docs/screenshots/profile.png) |

## 构建

需要 JDK 17 和 Android SDK（platform 35、build-tools 35.0.0）。

```bash
export JAVA_HOME=/path/to/jdk-17
export ANDROID_HOME=/path/to/android-sdk

./gradlew assembleDebug          # 产物：app/build/outputs/apk/debug/app-debug.apk
./gradlew testDebugUnitTest      # 数据样例单元测试
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.zq.topnews/.MainActivity
```

## 说明

示例数据全部内置在 `data/` 包中，图片用渐变色占位（`ui/components/Placeholder.kt`），因此**完全离线可运行**，不依赖任何后端或第三方图片服务。
