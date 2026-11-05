import { NewsItem, SAMPLE_NEWS } from '../model/NewsItem';

/**
 * TopNews home page.
 *
 * The first HarmonyOS screen is a single demo feed: a brand header and ten
 * built-in headlines. Network loading and the remaining app screens are
 * intentionally out of scope for this project scaffold.
 */
@Entry
@Component
struct Index {
  private news: NewsItem[] = SAMPLE_NEWS;

  @Builder
  newsRow(item: NewsItem) {
    Column() {
      Text(item.title)
        .fontSize(17)
        .fontWeight(FontWeight.Medium)
        .fontColor($r('app.color.text_primary'))
        .lineHeight(24)
        .maxLines(2)
        .textOverflow({ overflow: TextOverflow.Ellipsis })
        .width('100%')
      Text(`${item.source} · ${item.time}`)
        .fontSize(12)
        .fontColor($r('app.color.text_secondary'))
        .margin({ top: 6 })
        .width('100%')
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
    .padding({ top: 14, right: 16, bottom: 14, left: 16 })
  }

  build() {
    Column() {
      Row() {
        Text($r('app.string.app_name'))
          .fontSize(22)
          .fontWeight(FontWeight.Bold)
          .fontColor(Color.White)
      }
      .width('100%')
      .height(56)
      .padding({ right: 16, left: 16 })
      .backgroundColor($r('app.color.brand'))

      List() {
        ForEach(this.news, (item: NewsItem) => {
          ListItem() {
            this.newsRow(item)
          }
        }, (item: NewsItem) => item.title)
      }
      .layoutWeight(1)
      .width('100%')
      .divider({
        strokeWidth: 0.5,
        color: $r('app.color.divider'),
        startMargin: 16,
        endMargin: 16
      })
      .scrollBar(BarState.Off)
    }
    .width('100%')
    .height('100%')
    .backgroundColor($r('app.color.background'))
  }
}
