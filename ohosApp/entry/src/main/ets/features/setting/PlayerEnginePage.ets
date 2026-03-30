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
      PlayerEngineType.IJK,
      $r('app.media.ijk'),
      $r('app.string.player_engine_ijk'),
      '兼容性好，支持更多格式'
    ),
    new PlayerEngineItem(
      PlayerEngineType.AV,
      $r('app.media.hmos'),
      $r('app.string.player_engine_av'),
      '鸿蒙原生播放器'
    ),
    new PlayerEngineItem(
      PlayerEngineType.MPV,
      $r('app.media.mpv'),
      $r('app.string.player_engine_mpv'),
      'MPV 内核，适合高兼容性播放'
    )
  ]
  mpvDecodeList: Array<MpvDecodeItem> = [
    new MpvDecodeItem('软解', '兼容性更好，黑屏或花屏时优先使用', false),
    new MpvDecodeItem('硬解', '优先使用硬件解码，功耗更低，但个别机型兼容性可能略差', true)
  ]
  mpvCacheList: Array<MpvCacheItem> = [
    new MpvCacheItem('512 MB', 512, '更省内存，适合局域网或短视频'),
    new MpvCacheItem('1024 MB', 1024, '默认推荐，兼顾稳定性和内存占用'),
    new MpvCacheItem('1536 MB', 1536, '弱网更稳，拖动和切集缓冲更从容'),
    new MpvCacheItem('2048 MB', 2048, '缓存拉满，长视频和弱网更稳，但内存占用最高')
  ]

  aboutToAppear(): void {
    const current = AppPreference.getInstance().getPlayerEngineType()
    if (current === PlayerEngineType.IJK) {
      this.selectIndex = 0
    } else if (current === PlayerEngineType.AV) {
      this.selectIndex = 1
    } else {
      this.selectIndex = 2
    }
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
              }.onClick(() => {
                this.selectIndex = index
                AppPreference.getInstance().setPlayerEngineType(item.type)
              })
            }
          })
        }
        .width('100%')
        .height(150)
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

      if (this.isMpvSelected()) {
        Column({ space: 16 }) {
          Column({ space: 10 }) {
            Text('MPV 解码模式')
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
                    .maxLines(2)
                }
                .width('50%')
                .alignItems(HorizontalAlign.Start)
                .padding(14)
                .backgroundColor(this.mpvDecodeIndex === index ? $r('app.color.color_main') : $r('app.color.bg_1'))
                .borderRadius(12)
                .onClick(() => {
                  this.selectDecode(index)
                })
              })
            }
          }
          .width('100%')

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
              }
              .width('100%')
              .padding({ left: 14, right: 14, top: 12, bottom: 12 })
              .backgroundColor($r('app.color.bg_1'))
              .borderRadius(12)
              .onClick(() => {
                this.selectCache(index)
              })
            })
          }
          .width('100%')

          Text(this.mpvDecodeIndex === 1 ? '硬解当前走 MPV 的 buffer-hw 路径；如果再次遇到黑屏或花屏，切回软解即可。' : '软解当前走 buffer-sw 路径，兼容性最好，但功耗和 CPU 占用会更高。')
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
