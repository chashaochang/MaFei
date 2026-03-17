import { HMRouter } from '@hadss/hmrouter';
import { RouterConsts } from '../../common/RouterConsts';
import { ActionBar } from '../../component/ActionBar';
import { AppPreference } from '../../data/AppPreference';
import { PlayerEngineType } from '../../entity/PlayerEngineType';

@HMRouter({
  pageUrl: RouterConsts.PlayerEnginePage
})
@ComponentV2
export struct PlayerEnginePage {
  @Local selectIndex: number = 0
  list: Array<PlayerEngineItem> = [
    new PlayerEngineItem(
      PlayerEngineType.IJK,
      $r('app.media.movie'),
      $r('app.string.player_engine_ijk'),
      '兼容性好，支持更多格式'
    ),
    new PlayerEngineItem(
      PlayerEngineType.AV,
      $r('app.media.tv'),
      $r('app.string.player_engine_av'),
      '鸿蒙原生播放器'
    )
  ]

  aboutToAppear(): void {
    const current = AppPreference.getInstance().getPlayerEngineType()
    if (current === PlayerEngineType.IJK) {
      this.selectIndex = 0
    } else {
      this.selectIndex = 1
    }
  }

  build() {
    Column() {
      ActionBar({
        title: getContext().resourceManager.getStringSync($r('app.string.player_engine_title'))
      })
      Stack() {
        Grid() {
          ForEach(this.list, (item: PlayerEngineItem, index: number) => {
            GridItem() {
              Column() {
                Image(item.imageSrc)
                  .width(74)
                  .objectFit(ImageFit.Contain)
                  .borderRadius(8)
                  .borderWidth(0.5)
                  .borderColor($r('app.color.border_input'))
                Blank().height(6)
                Text(item.title).fontSize(14).fontColor($r('app.color.text_primary'))
                Blank().height(6)
                Radio({ value: item.type, group: 'playerEngineGroup' }).checked(this.selectIndex == index)
                  .onChange(() => {
                    this.selectIndex = index
                    AppPreference.getInstance().setPlayerEngineType(item.type)
                  })
              }
            }
          })
        }
        .width('100%')
        .height(200)
        .columnsTemplate('1fr 1fr')
        .backgroundColor($r('app.color.bg_1'))
        .borderRadius(12)
        .padding({
          top: 20,
          left: 16,
          right: 16
        })
      }.padding({
        left: 16,
        right: 16
      })

      Text(this.list[this.selectIndex].tip)
        .fontSize(12)
        .fontColor($r('app.color.text_1'))
        .margin({ top: 10 })
    }.width('100%')
    .height('100%')
    .backgroundColor($r('app.color.start_window_background'))
    .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.BOTTOM])
  }
}

class PlayerEngineItem {
  type: PlayerEngineType
  imageSrc: Resource
  title: ResourceStr
  tip: string

  constructor(type: PlayerEngineType, imageSrc: Resource, title: ResourceStr, tip: string = '') {
    this.type = type
    this.imageSrc = imageSrc
    this.title = title
    this.tip = tip
  }
}
