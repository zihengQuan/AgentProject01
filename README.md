# TopNews 鸿蒙版

TopNews 的 HarmonyOS NEXT 工程，使用 Stage 模型 + ArkTS 实现。当前是工程脚手架：一个 TopNews 首页信息流，展示 10 条内置演示新闻；暂未接入网络与其余页面。

## 工程信息

| 项 | 值 |
| --- | --- |
| 包名 | `com.zq.topnews` |
| 应用名 | TopNews |
| 模型 | Stage |
| 语言 | ArkTS |
| 兼容 / 目标 SDK | `5.0.0(12)` |
| 设备类型 | phone |

## 目录结构

```
.
├── AppScope/                 # 应用级配置、名称与图标
├── entry/                    # 入口模块
│   └── src/main/
│       ├── ets/
│       │   ├── entryability/EntryAbility.ets
│       │   ├── model/NewsItem.ets
│       │   └── pages/Index.ets
│       ├── module.json5
│       └── resources/        # 字符串、颜色、图标、页面配置
├── build-profile.json5
├── oh-package.json5
├── hvigorfile.ts
└── README.md
```

## 运行

1. 安装 DevEco Studio 5.0 或更高版本，并准备 HarmonyOS NEXT API 12 及以上 SDK。
2. 在 DevEco Studio 中选择 `File > Open`，打开本工程根目录。
3. 等待工程同步完成；如提示签名，使用自动签名或配置自己的签名证书。
4. 选择 `entry` 模块，运行到模拟器或真机。

命令行构建（使用 DevEco Studio 自带的 `hvigorw`，在本工程根目录执行）：

```bash
hvigorw assembleHap --mode module -p product=default -p buildMode=debug --no-daemon
```

构建产物位于 `entry/build/default/outputs/default/`。

## 当前范围

- 仅一个首页信息流：品牌红顶栏 + 10 条演示新闻（标题、来源、时间）。
- 数据硬编码在 `entry/src/main/ets/model/NewsItem.ets`，不请求网络。
- 颜色资源提供浅色与深色两套，跟随系统深浅色模式。
- 尚未包含视频、消息、我的、文章详情、登录注册等页面，也未包含自动化测试。

> 本分支只包含 HarmonyOS NEXT 工程，工程根目录即分支根目录，可直接用 DevEco Studio 打开。本工程尚未在自动化环境中构建；首次打开后请先完成 SDK 同步与签名配置。如需使用更高版本 SDK，可在 `build-profile.json5` 中调整 `compatibleSdkVersion` / `targetSdkVersion`。
