/**
 * A single headline shown on the demo home page.
 *
 * The data is hard-coded for now: this HarmonyOS project has no network or
 * backend yet.
 */
export interface NewsItem {
  title: string;
  source: string;
  time: string;
}

/** Ten demo headlines used by the scaffold feed. */
export const SAMPLE_NEWS: NewsItem[] = [
  {
    title: '国内首个千万级低空经济产业园区在深圳开园',
    source: '新华社',
    time: '12 分钟前'
  },
  {
    title: '国产大模型开源生态再添新成员，推理成本大幅下降',
    source: '科技日报',
    time: '28 分钟前'
  },
  {
    title: '新一代载人飞船完成首次高空逃逸试验',
    source: '中国航天报',
    time: '45 分钟前'
  },
  {
    title: '多地优化住房公积金政策，支持刚性住房需求',
    source: '经济日报',
    time: '1 小时前'
  },
  {
    title: '全国秋粮收购进度过半，市场运行总体平稳',
    source: '人民日报',
    time: '2 小时前'
  },
  {
    title: '高校团队研发新型固态电池，能量密度提升三成',
    source: '光明日报',
    time: '3 小时前'
  },
  {
    title: '城市马拉松赛季开启，参赛人数创历史新高',
    source: '中国体育报',
    time: '5 小时前'
  },
  {
    title: '跨境支付互联互通试点扩围，覆盖更多共建国家',
    source: '金融时报',
    time: '6 小时前'
  },
  {
    title: '深海科考船完成年度综合调查任务返航',
    source: '央视新闻',
    time: '8 小时前'
  },
  {
    title: '数字乡村建设提速，5G 网络覆盖持续扩大',
    source: '中国信息通信研究院',
    time: '昨天'
  }
];
