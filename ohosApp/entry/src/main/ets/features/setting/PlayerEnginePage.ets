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
  @Local mpvDecodeIndex: number = 0
  @Local mpvCacheIndex: number = 1
  list: Array<PlayerEngineItem> = [
    new PlayerEngineItem(
      PlayerEngineType.AUTO,
      $r('app.media.movie'),
      $r('app.string.player_engine_auto'),
      '默认推荐。能用系统播放器就优先系统播放器；遇到系统播放器不支持的格式、音轨或字幕时，会自动切到 MPV 硬解。'
    ),
    new PlayerEngineItem(
      PlayerEngineType.MPV,
      $r('app.media.mpv'),
      $r('app.string.player_engine_mpv'),
      '始终使用 MPV。适合想手动固定复杂片源、外挂字幕和高兼容播放的人。'
    ),
    new PlayerEngineItem(
      PlayerEngineType.AV,
      $r('app.media.hmos'),
      $r('app.string.player_engine_av'),
      '始终使用系统播放器。普通视频更省心，也更适合你只想走系统解码的场景。'
    )
  ]
  mpvDecodeList: Array<MpvDecodeItem> = [
    new MpvDecodeItem('软解', '由播放器自己解码，兼容性更好，遇到黑屏或花屏先试它。', false),
    new MpvDecodeItem('硬解', '调用手机硬件解码，更省电，HDR 片源更适合，但少数机型可能有兼容问题。', true)
  ]
  mpvCacheList: Array<MpvCacheItem> = [
    new MpvCacheItem('512 MB', 512, '更省内存，适合局域网或短视频'),
    new MpvCacheItem('1024 MB', 1024, '默认推荐，兼顾稳定性和内存占用'),
    new MpvCacheItem('1536 MB', 1536, '弱网更稳，拖动和切集缓冲更从容'),
    new MpvCacheItem('2048 MB', 2048, '缓存拉满，长视频和弱网更稳，但内存占用最高')
  ]

  aboutToAppear(): void {
    const current = AppPreference.getInstance().getPlayerEngineType()
    const currentIndex = this.list.findIndex((item: PlayerEngineItem) => item.type === current)
    this.selectIndex = currentIndex >= 0 ? currentIndex : 0
    this.syncMpvSettings()
  }

  private syncMpvSettings(): void {
    const preference = AppPreference.getInstance()
    this.mpvDecodeIndex = preference.getMpvUseHardwareDecode() ? 1 : 0
    const currentCacheSize = preference.getMpvCacheSizeMb()
    const cacheIndex = this.mpvCacheList.findIndex((item: MpvCacheItem) => item.sizeMb === currentCacheSize)
    this.mpvCacheIndex = cacheIndex >= 0 ? cacheIndex : 1
  }

  private isMpvSelected(): boolean {
    return this.list[this.selectIndex].type === PlayerEngineType.MPV
  }

  private isAutoSelected(): boolean {
    return this.list[this.selectIndex].type === PlayerEngineType.AUTO
  }

  private shouldShowMpvCacheSettings(): boolean {
    return this.isAutoSelected() || this.isMpvSelected()
  }

  private shouldShowManualMpvDecodeSettings(): boolean {
    return this.isMpvSelected()
  }

  private selectEngine(index: number): void {
    if (index < 0 || index >= this.list.length || this.selectIndex === index) {
      return
    }
    this.selectIndex = index
    AppPreference.getInstance().setPlayerEngineType(this.list[index].type)
  }

  private selectDecode(index: number): void {
    this.mpvDecodeIndex = index
    AppPreference.getInstance().setMpvUseHardwareDecode(this.mpvDecodeList[index].useHardwareDecode)
  }

  private selectCache(index: number): void {
    this.mpvCacheIndex = index
    AppPreference.getInstance().setMpvCacheSizeMb(this.mpvCacheList[index].sizeMb)
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
                  .width(48)
                  .objectFit(ImageFit.Contain)
                  .borderRadius(8)
                  .borderWidth(0.5)
                  .borderColor($r('app.color.border_input'))
                  .fillColor($r('app.color.text_primary'))
                Blank().height(12)
                Text(item.title).fontSize(14).fontColor($r('app.color.text_primary'))
                Blank().height(6)
                Radio({ value: item.type, group: 'playerEngineGroup' }).checked(this.selectIndex == index)
                  .hitTestBehavior(HitTestMode.None)
              }.onClick(() => {
                this.selectEngine(index)
              })
            }
          })
        }
        .width('100%')
        .height(158)
        .columnsTemplate('1fr 1fr 1fr')
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
        .margin({ top: 20 })
        .padding({
          left: 16,
          right: 16
        })

      if (this.shouldShowMpvCacheSettings()) {
        Column({ space: 16 }) {
          if (this.shouldShowManualMpvDecodeSettings()) {
            Column({ space: 10 }) {
              Text('MPV 解码方式')
                .fontSize(16)
                .fontWeight(FontWeight.Medium)
                .fontColor($r('app.color.text_primary'))

              Row({ space: 12 }) {
                ForEach(this.mpvDecodeList, (item: MpvDecodeItem, index: number) => {
                  Column({ space: 8 }) {
                    Text(item.title)
                      .fontSize(15)
                      .fontColor(this.mpvDecodeIndex === index ? Color.White : $r('app.color.text_primary'))
                    Text(item.tip)
                      .fontSize(11)
                      .fontColor(this.mpvDecodeIndex === index ? '#E6FFFFFF' : $r('app.color.text_1'))
                      .maxLines(3)
                  }
                  .layoutWeight(1)
                  .constraintSize({ minHeight: 108 })
                  .alignItems(HorizontalAlign.Start)
                  .padding({
                    left: 14,
                    right: 14,
                    top: 16,
                    bottom: 16
                  })
                  .backgroundColor(this.mpvDecodeIndex === index ? $r('app.color.color_main') : $r('app.color.bg_1'))
                  .borderRadius(12)
                  .onClick(() => {
                    this.selectDecode(index)
                  })
                })
              }
              .width('100%')
            }
            .width('100%')
          }

          Column({ space: 10 }) {
            Text('MPV 缓存大小')
              .fontSize(16)
              .fontWeight(FontWeight.Medium)
              .fontColor($r('app.color.text_primary'))

            ForEach(this.mpvCacheList, (item: MpvCacheItem, index: number) => {
              Row() {
                Column({ space: 4 }) {
                  Text(item.title)
                    .fontSize(15)
                    .fontColor($r('app.color.text_primary'))
                  Text(item.tip)
                    .fontSize(11)
                    .fontColor($r('app.color.text_1'))
                }
                .alignItems(HorizontalAlign.Start)

                Blank()

                Radio({ value: item.title, group: 'mpvCacheGroup' }).checked(this.mpvCacheIndex === index)
                  .hitTestBehavior(HitTestMode.None)
              }
              .width('100%')
              .padding({
                left: 14,
                right: 14,
                top: 12,
                bottom: 12
              })
              .backgroundColor($r('app.color.bg_1'))
              .borderRadius(12)
              .onClick(() => {
                this.selectCache(index)
              })
            })
          }
          .width('100%')

          Text(this.isAutoSelected()
            ? '自动模式下，如果系统播放器吃不下当前片源，会自动切到 MPV，并优先使用硬解；你只需要保留一个合适的 MPV 缓存大小就行。'
            : this.mpvDecodeIndex === 1
              ? '当前已选择硬解。它会优先调用手机硬件解码，功耗更低，也更适合 HDR 片源；如果出现黑屏、花屏或颜色不对，再切回软解。'
              : '当前已选择软解。它由 MPV 自己解码，兼容性更好；如果想让 HDR 片源更省电，或者想试试硬件能力，再切到硬解。')
            .fontSize(12)
            .fontColor($r('app.color.text_1'))
        }
        .width('100%')
        .padding({ left: 16, right: 16, top: 20 })
      }
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

class MpvDecodeItem {
  title: string
  tip: string
  useHardwareDecode: boolean

  constructor(title: string, tip: string, useHardwareDecode: boolean) {
    this.title = title
    this.tip = tip
    this.useHardwareDecode = useHardwareDecode
  }
}

class MpvCacheItem {
  title: string
  sizeMb: number
  tip: string

  constructor(title: string, sizeMb: number, tip: string) {
    this.title = title
    this.sizeMb = sizeMb
    this.tip = tip
  }
}
