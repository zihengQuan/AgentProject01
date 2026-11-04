package com.zq.topnews.data

/**
 * A single headline shown in the TopNews feed.
 *
 * The data is hard-coded for now: there is no network or backend yet.
 * [tone] selects one of the generated placeholder thumbnails.
 */
data class NewsItem(
    val id: String,
    val title: String,
    val source: String,
    val time: String,
    val category: String,
    val comments: Int,
    val paragraphs: List<String>,
    val tone: Int,
)

/** Channel tabs across the top of the home feed. */
val categories: List<String> = listOf("推荐", "热点", "科技", "体育", "财经", "娱乐")

/** Demo headlines, one set per channel. */
val sampleNews: List<NewsItem> = listOf(
    NewsItem(
        id = "n01",
        title = "国内首个千万级低空经济产业园区在深圳开园",
        source = "新华社",
        time = "12 分钟前",
        category = "热点",
        comments = 3286,
        tone = 0,
        paragraphs = listOf(
            "国内首个千万级低空经济产业园区今日在深圳正式开园。园区规划面积超过两平方公里，聚焦无人机整机制造、低空物流、城市空中交通等方向。",
            "首批入驻企业超过四十家，覆盖研发、测试、运营全链条。园区内配套建设了起降点、检测中心和飞行服务站，可为中小型无人机提供一站式适航支持。",
            "业内人士表示，低空经济正在从试点走向规模化，园区的运营经验将为其他城市提供参考。",
        ),
    ),
    NewsItem(
        id = "n02",
        title = "国产大模型开源生态再添新成员，推理成本大幅下降",
        source = "科技日报",
        time = "28 分钟前",
        category = "科技",
        comments = 1892,
        tone = 1,
        paragraphs = listOf(
            "又一家国产厂商宣布开源其最新一代大语言模型，参数规模覆盖中小型部署场景，官方称推理成本较上一代下降约六成。",
            "该模型采用稀疏激活架构，在保持通用能力的同时显著降低了显存占用，可在单张消费级显卡上运行。",
            "开源社区反馈积极，多个推理框架已在第一时间完成适配。",
        ),
    ),
    NewsItem(
        id = "n03",
        title = "新一代载人飞船完成首次高空逃逸试验",
        source = "中国航天报",
        time = "45 分钟前",
        category = "热点",
        comments = 2541,
        tone = 2,
        paragraphs = listOf(
            "新一代载人飞船今日完成首次高空逃逸飞行试验，逃逸塔按预定程序点火分离，返回舱安全着陆。",
            "本次试验验证了在最大动压条件下乘员逃逸系统的可靠性，为后续载人飞行任务奠定基础。",
            "任务团队表示，全部飞行数据符合预期，下一步将开展海上回收相关试验。",
        ),
    ),
    NewsItem(
        id = "n04",
        title = "多地优化住房公积金政策，支持刚性住房需求",
        source = "经济日报",
        time = "1 小时前",
        category = "财经",
        comments = 967,
        tone = 3,
        paragraphs = listOf(
            "近期多个城市陆续调整住房公积金政策，提高贷款额度、放宽提取条件，重点支持首套和改善性住房需求。",
            "部分城市还将灵活就业人员纳入缴存范围，进一步扩大制度覆盖面。",
            "专家认为，政策优化有助于稳定市场预期，但效果仍需观察。",
        ),
    ),
    NewsItem(
        id = "n05",
        title = "全国秋粮收购进度过半，市场运行总体平稳",
        source = "人民日报",
        time = "2 小时前",
        category = "财经",
        comments = 412,
        tone = 4,
        paragraphs = listOf(
            "截至目前，全国秋粮收购进度已过半，主产区收购价格总体稳定，市场秩序良好。",
            "有关部门表示，将持续做好仓容、资金和运力保障，方便农民售粮。",
            "同时加强质量监测，严防不合格粮食流入口粮市场。",
        ),
    ),
    NewsItem(
        id = "n06",
        title = "高校团队研发新型固态电池，能量密度提升三成",
        source = "光明日报",
        time = "3 小时前",
        category = "科技",
        comments = 1520,
        tone = 5,
        paragraphs = listOf(
            "国内高校研究团队在固态电解质材料上取得进展，实验室样品能量密度较现有产品提升约三成。",
            "团队通过界面改性降低了电极与电解质之间的接触电阻，循环寿命同步改善。",
            "相关成果已发表在国际期刊上，产业化仍需解决成本与工艺问题。",
        ),
    ),
    NewsItem(
        id = "n07",
        title = "城市马拉松赛季开启，参赛人数创历史新高",
        source = "中国体育报",
        time = "5 小时前",
        category = "体育",
        comments = 733,
        tone = 6,
        paragraphs = listOf(
            "随着天气转凉，各地马拉松赛事密集开跑，多个赛事报名人数刷新历史纪录。",
            "组委会在补给、医疗和交通接驳方面增加投入，提升参赛体验。",
            "跑步爱好者提醒，赛前应做好体检和训练计划，量力而行。",
        ),
    ),
    NewsItem(
        id = "n08",
        title = "跨境支付互联互通试点扩围，覆盖更多共建国家",
        source = "金融时报",
        time = "6 小时前",
        category = "财经",
        comments = 356,
        tone = 7,
        paragraphs = listOf(
            "跨境支付互联互通试点范围进一步扩大，新增多个共建国家参与。",
            "试点支持本地货币结算，降低企业汇兑成本，提升资金到账效率。",
            "参与机构表示，将持续完善风控和合规体系。",
        ),
    ),
    NewsItem(
        id = "n09",
        title = "深海科考船完成年度综合调查任务返航",
        source = "央视新闻",
        time = "8 小时前",
        category = "科技",
        comments = 289,
        tone = 2,
        paragraphs = listOf(
            "执行年度综合调查任务的深海科考船顺利返航，累计航程超过两万海里。",
            "本次调查获取了大量深海生物、沉积物和水体样本，为研究深海生态系统提供支撑。",
            "船上搭载的国产探测设备运行稳定，多项指标达到预期。",
        ),
    ),
    NewsItem(
        id = "n10",
        title = "数字乡村建设提速，5G 网络覆盖持续扩大",
        source = "中国信息通信研究院",
        time = "昨天",
        category = "科技",
        comments = 198,
        tone = 4,
        paragraphs = listOf(
            "最新数据显示，全国行政村 5G 网络覆盖比例持续提升，农村地区宽带接入能力明显增强。",
            "数字化应用正从政务、医疗向农业生产的各个环节延伸。",
            "专家建议，在完善基础设施的同时加强农民数字技能培训。",
        ),
    ),
    NewsItem(
        id = "n11",
        title = "国产动画电影票房口碑双丰收，工业化能力受关注",
        source = "文汇报",
        time = "昨天",
        category = "娱乐",
        comments = 1103,
        tone = 1,
        paragraphs = listOf(
            "一部国产动画电影上映两周，票房与口碑表现亮眼，多个技术团队参与制作。",
            "业内人士认为，国内动画工业化流程正在逐步成熟，但原创能力仍需积累。",
            "制片方透露，续集已进入前期开发阶段。",
        ),
    ),
    NewsItem(
        id = "n12",
        title = "全国青少年校园足球联赛落幕，基层教练队伍扩大",
        source = "中国青年报",
        time = "昨天",
        category = "体育",
        comments = 87,
        tone = 3,
        paragraphs = listOf(
            "全国青少年校园足球联赛总决赛结束，参赛队伍数量和比赛场次均创新高。",
            "赛事期间同步举办教练员培训，基层教练队伍进一步扩大。",
            "组织方表示，将继续完善竞赛体系，让更多孩子参与足球运动。",
        ),
    ),
    NewsItem(
        id = "n13",
        title = "音乐节带动周边消费，文旅融合呈现新玩法",
        source = "中国文化报",
        time = "2 天前",
        category = "娱乐",
        comments = 645,
        tone = 5,
        paragraphs = listOf(
            "多地音乐节在周末集中举办，带动住宿、餐饮和交通消费明显增长。",
            "主办方将本地非遗、市集等元素融入现场，形成差异化体验。",
            "文旅部门表示，将进一步规范演出市场，提升服务质量。",
        ),
    ),
    NewsItem(
        id = "n14",
        title = "全国快递业务量保持增长，农村寄递网络持续完善",
        source = "经济参考报",
        time = "2 天前",
        category = "热点",
        comments = 274,
        tone = 0,
        paragraphs = listOf(
            "最新统计显示，全国快递业务量继续保持增长，农村地区寄递服务能力明显提升。",
            "快递企业加大县乡村三级网络建设，共同配送模式在多地推广。",
            "分析认为，农村寄递网络完善将进一步释放消费潜力。",
        ),
    ),
)
