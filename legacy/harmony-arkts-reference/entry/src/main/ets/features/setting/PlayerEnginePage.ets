import { HMRouter } from '@hadss/hmrouter';
import { RouterConsts } from '../../common/RouterConsts';
import { ActionBar } from '../../component/ActionBar';
import { AppRouteDestination, AppRouteDestinationOptions } from '../../component/AppRouteDestination';
import { AppPreference } from '../../data/AppPreference';
import { PlayerEngineType } from '../../entity/PlayerEngineType';
import { AppThemeSurfaceResolver } from '../../theme/AppThemeSurfaceResolver';
import { HdsNavDestination, HdsNavDestinationTitleMode } from '@kit.UIDesignKit'

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

@ComponentV2
struct PlayerEngineChoiceGrid {
  @Require @Param items: PlayerEngineItem[]
  @Require @Param selectIndex: number
  @Require @Param nativeSurface: boolean
  @Event onSelect: (index: number) => void = () => {
  }

  private primaryTextColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private dividerColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.comp_divider') : $r('app.color.border_input')
  }

  build() {
    Grid() {
      ForEach(this.items, (item: PlayerEngineItem, index: number) => {
        GridItem() {
          Column() {
            Image(item.imageSrc)
              .width(48)
              .objectFit(ImageFit.Contain)
              .borderRadius(8)
              .borderWidth(this.nativeSurface ? 0 : 0.5)
              .borderColor(this.dividerColor())
              .fillColor(this.primaryTextColor())
            Blank().height(12)
            Text(item.title)
              .fontSize(14)
              .fontColor(this.primaryTextColor())
            Blank().height(6)
            Radio({ value: item.type, group: 'playerEngineGroup' })
              .checked(this.selectIndex === index)
              .hitTestBehavior(HitTestMode.None)
          }
          .onClick(() => this.onSelect(index))
        }
      })
    }
    .width('100%')
    .height(158)
    .columnsTemplate('1fr 1fr 1fr')
    .borderRadius(this.nativeSurface ? 16 : 12)
    .clip(this.nativeSurface)
    .padding({ top: 20, left: 16, right: 16 })
    .backgroundColor(this.nativeSurface ?
      AppThemeSurfaceResolver.contentGroupBackground() : $r('app.color.bg_1'))
  }
}

@ComponentV2
struct MpvSettingsSurface {
  @Require @Param decodeItems: MpvDecodeItem[]
  @Require @Param cacheItems: MpvCacheItem[]
  @Require @Param decodeIndex: number
  @Require @Param cacheIndex: number
  @Require @Param autoSelected: boolean
  @Require @Param showDecodeSettings: boolean
  @Require @Param nativeSurface: boolean
  @Event onDecodeSelect: (index: number) => void = () => {
  }
  @Event onCacheSelect: (index: number) => void = () => {
  }

  private primaryTextColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary')
  }

  private secondaryTextColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  private dividerColor(): ResourceColor {
    return this.nativeSurface ? $r('sys.color.comp_divider') : $r('app.color.border_input')
  }

  build() {
    Column({ space: 16 }) {
      if (this.showDecodeSettings) {
        Column({ space: 10 }) {
          Text('MPV 解码方式')
            .fontSize(16)
            .fontWeight(FontWeight.Medium)
            .fontColor(this.primaryTextColor())
          Row({ space: 12 }) {
            ForEach(this.decodeItems, (item: MpvDecodeItem, index: number) => {
              Column({ space: 8 }) {
                Text(item.title)
                  .fontSize(15)
                  .fontColor(this.decodeIndex === index ? Color.White : this.primaryTextColor())
                Text(item.tip)
                  .fontSize(11)
                  .fontColor(this.decodeIndex === index ? '#E6FFFFFF' : this.secondaryTextColor())
                  .maxLines(3)
              }
              .layoutWeight(1)
              .constraintSize({ minHeight: 108 })
              .alignItems(HorizontalAlign.Start)
              .padding({ left: 14, right: 14, top: 16, bottom: 16 })
              .backgroundColor(this.decodeIndex === index ?
                $r('app.color.color_main') :
                (this.nativeSurface ? Color.Transparent : $r('app.color.bg_1')))
              .borderRadius(12)
              .onClick(() => this.onDecodeSelect(index))
            })
          }
          .width('100%')
        }
        .width('100%')
      }

      Column({ space: this.nativeSurface ? 0 : 10 }) {
        Text('MPV 缓存大小')
          .fontSize(16)
          .fontWeight(FontWeight.Medium)
          .fontColor(this.primaryTextColor())
          .margin({ bottom: this.nativeSurface ? 6 : 0 })
        ForEach(this.cacheItems, (item: MpvCacheItem, index: number) => {
          Row() {
            Column({ space: 4 }) {
              Text(item.title)
                .fontSize(15)
                .fontColor(this.primaryTextColor())
              Text(item.tip)
                .fontSize(11)
                .fontColor(this.secondaryTextColor())
            }
            .alignItems(HorizontalAlign.Start)
            Blank()
            Radio({ value: item.title, group: 'mpvCacheGroup' })
              .checked(this.cacheIndex === index)
              .hitTestBehavior(HitTestMode.None)
          }
          .width('100%')
          .padding({ left: 14, right: 14, top: 12, bottom: 12 })
          .backgroundColor(this.nativeSurface ? Color.Transparent : $r('app.color.bg_1'))
          .borderRadius(12)
          .onClick(() => this.onCacheSelect(index))
          if (this.nativeSurface && index < this.cacheItems.length - 1) {
            Divider()
              .color(this.dividerColor())
              .margin({ left: 14, right: 14 })
          }
        })
      }
      .width('100%')

      Text(this.autoSelected
        ? '自动模式下，如果系统播放器吃不下当前片源，会自动切到 MPV，并优先使用硬解；你只需要保留一个合适的 MPV 缓存大小就行。'
        : this.decodeIndex === 1
          ? '当前已选择硬解。它会优先调用手机硬件解码，功耗更低，也更适合 HDR 片源；如果出现黑屏、花屏或颜色不对，再切回软解。'
          : '当前已选择软解。它由 MPV 自己解码，兼容性更好；如果想让 HDR 片源更省电，或者想试试硬件能力，再切到硬解。')
        .fontSize(12)
        .fontColor(this.secondaryTextColor())
    }
    .width('100%')
    .padding(this.nativeSurface ? 16 : { left: 16, right: 16, top: 20 })
    .borderRadius(this.nativeSurface ? 16 : 0)
    .clip(this.nativeSurface)
    .backgroundColor(this.nativeSurface ?
      AppThemeSurfaceResolver.contentGroupBackground() : Color.Transparent)
  }
}

@ComponentV2
struct PlayerEnginePageSurface {
  @Require @Param showLegacyActionBar: boolean
  @Local selectIndex: number = 0
  @Local mpvDecodeIndex: number = 0
  @Local mpvCacheIndex: number = 1
  private list: PlayerEngineItem[] = [
    new PlayerEngineItem(PlayerEngineType.AUTO, $r('app.media.movie'),
      $r('app.string.player_engine_auto'),
      '默认推荐。能用系统播放器就优先系统播放器；遇到系统播放器不支持的格式、音轨或字幕时，会自动切到 MPV 硬解。'),
    new PlayerEngineItem(PlayerEngineType.MPV, $r('app.media.mpv'),
      $r('app.string.player_engine_mpv'),
      '始终使用 MPV。适合想手动固定复杂片源、外挂字幕和高兼容播放的人。'),
    new PlayerEngineItem(PlayerEngineType.AV, $r('app.media.hmos'),
      $r('app.string.player_engine_av'),
      '始终使用系统播放器。普通视频更省心，也更适合你只想走系统解码的场景。')
  ]
  private mpvDecodeList: MpvDecodeItem[] = [
    new MpvDecodeItem('软解', '由播放器自己解码，兼容性更好，遇到黑屏或花屏先试它。', false),
    new MpvDecodeItem('硬解', '调用手机硬件解码，更省电，HDR 片源更适合，但少数机型可能有兼容问题。', true)
  ]
  private mpvCacheList: MpvCacheItem[] = [
    new MpvCacheItem('512 MB', 512, '更省内存，适合局域网或短视频'),
    new MpvCacheItem('1024 MB', 1024, '默认推荐，兼顾稳定性和内存占用'),
    new MpvCacheItem('1536 MB', 1536, '弱网更稳，拖动和切集缓冲更从容'),
    new MpvCacheItem('2048 MB', 2048, '缓存拉满，长视频和弱网更稳，但内存占用最高')
  ]

  aboutToAppear(): void {
    const preference = AppPreference.getInstance()
    const current = preference.getPlayerEngineType()
    const currentIndex = this.list.findIndex((item: PlayerEngineItem) => item.type === current)
    this.selectIndex = currentIndex >= 0 ? currentIndex : 0
    this.mpvDecodeIndex = preference.getMpvUseHardwareDecode() ? 1 : 0
    const currentCacheSize = preference.getMpvCacheSizeMb()
    const cacheIndex = this.mpvCacheList.findIndex((item: MpvCacheItem) => item.sizeMb === currentCacheSize)
    this.mpvCacheIndex = cacheIndex >= 0 ? cacheIndex : 1
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

  private isMpvSelected(): boolean {
    return this.list[this.selectIndex].type === PlayerEngineType.MPV
  }

  private isAutoSelected(): boolean {
    return this.list[this.selectIndex].type === PlayerEngineType.AUTO
  }

  private nativeSurface(): boolean {
    return !this.showLegacyActionBar
  }

  private secondaryTextColor(): ResourceColor {
    return this.nativeSurface() ? $r('sys.color.font_secondary') : $r('app.color.text_1')
  }

  build() {
    Column() {
      if (this.showLegacyActionBar) {
        ActionBar({ title: $r('app.string.player_engine_title') })
      }
      Stack() {
        PlayerEngineChoiceGrid({
          items: this.list,
          selectIndex: this.selectIndex,
          nativeSurface: this.nativeSurface(),
          onSelect: (index: number) => this.selectEngine(index)
        })
      }
      .width('100%')
      .constraintSize({ maxWidth: this.nativeSurface() ? 560 : '100%' })
      .padding({ left: 16, right: 16 })

      Text(this.list[this.selectIndex].tip)
        .fontSize(12)
        .fontColor(this.secondaryTextColor())
        .margin({ top: 20 })
        .padding({ left: 16, right: 16 })
        .constraintSize({ maxWidth: this.showLegacyActionBar ? '100%' : 560 })

      if (this.isAutoSelected() || this.isMpvSelected()) {
        Stack() {
          MpvSettingsSurface({
            decodeItems: this.mpvDecodeList,
            cacheItems: this.mpvCacheList,
            decodeIndex: this.mpvDecodeIndex,
            cacheIndex: this.mpvCacheIndex,
            autoSelected: this.isAutoSelected(),
            showDecodeSettings: this.isMpvSelected(),
            nativeSurface: this.nativeSurface(),
            onDecodeSelect: (index: number) => this.selectDecode(index),
            onCacheSelect: (index: number) => this.selectCache(index)
          })
        }
        .width('100%')
        .constraintSize({ maxWidth: this.nativeSurface() ? 560 : '100%' })
        .padding({ left: this.nativeSurface() ? 16 : 0, right: this.nativeSurface() ? 16 : 0,
          top: this.nativeSurface() ? 20 : 0 })
      }
    }
    .width('100%')
    .height('100%')
    .backgroundColor(this.showLegacyActionBar ? $r('app.color.start_window_background') : Color.Transparent)
    .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.BOTTOM])
  }
}

@HMRouter({
  pageUrl: RouterConsts.PlayerEnginePage,
  useNavDst: true
})
@ComponentV2
export struct PlayerEnginePage {
  private readonly routeDestination: AppRouteDestination = new AppRouteDestination(this)
  private routeOptions(): AppRouteDestinationOptions {
    return {
        title: $r('app.string.player_engine_title')
      }
  }

  build() {
    if (this.routeDestination.usesHdsDestination()) {
      HdsNavDestination() {
        Column() {
          Blank().height(this.routeDestination.nativeContentTopInset(this.routeOptions()))
          Stack() {
            if (this.routeDestination.usesNativeChrome()) {
              PlayerEnginePageSurface({ showLegacyActionBar: false })
            } else {
              PlayerEnginePageSurface({ showLegacyActionBar: true })
            }
          }
          .width('100%')
          .layoutWeight(1)
        }
        .width('100%')
        .height('100%')
      }
      .backgroundColor(this.routeDestination.hdsBackgroundColor())
      .titleMode(HdsNavDestinationTitleMode.MINI)
      .titleBar(this.routeDestination.nativeTitleBarOptions(this.routeOptions()))
      .hideTitleBar(this.routeDestination.hdsTitleBarHidden(this.routeOptions()))
      .hideBackButton(!this.routeDestination.backButtonVisible(this.routeOptions()))
      .hideToolBar(true)
      .bindToScrollable(this.routeDestination.hdsBoundScrollers(this.routeOptions()))
      .onWillAppear(() => this.routeDestination.helper.onWillAppear())
      .onAppear(() => this.routeDestination.helper.onAppear())
      .onWillShow(() => this.routeDestination.helper.onWillShow())
      .onShown(() => this.routeDestination.helper.onShown())
      .onWillHide(() => this.routeDestination.helper.onWillHide())
      .onHidden(() => this.routeDestination.helper.onHidden())
      .onWillDisappear(() => this.routeDestination.helper.onWillDisappear())
      .onDisAppear(() => this.routeDestination.helper.onDisAppear())
      .onReady((ctx: NavDestinationContext) => this.routeDestination.helper.onReady(ctx))
      .onBackPressed(() => this.routeDestination.handleBackPressed(this.routeOptions()))
      .onResult((result: ESObject) => this.routeDestination.helper.onResult(result))
      .onActive((reason: NavDestinationActiveReason) => this.routeDestination.helper.onActive(reason))
      .onInactive((reason: NavDestinationActiveReason) => this.routeDestination.helper.onInactive(reason))
      .ignoreLayoutSafeArea(
        this.routeDestination.nativeSafeAreaTypes(),
        this.routeDestination.nativeSafeAreaEdges()
      )
      .width('100%')
      .height('100%')
    } else {
      NavDestination() {
        PlayerEnginePageSurface({ showLegacyActionBar: true })
      }
      .attributeModifier(this.routeDestination.legacyModifier(this.routeOptions()))
      .gestureModifier(this.routeDestination.gestureModifier)
      .width('100%')
      .height('100%')
    }
  }
}
