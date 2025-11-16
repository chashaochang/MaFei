import { HMRouter } from '@hadss/hmrouter';
import { RouterConsts } from '../../common/RouterConsts';
import { ActionBar } from '../../component/ActionBar';
import { AppPreference } from '../../data/AppPreference';
import { AppUIState } from '../../entity/AppUIState';
import { CustomColorMode } from '../../entity/CustomColorMode';
import { AppStorageV2 } from '@kit.ArkUI';
import { common, ConfigurationConstant } from '@kit.AbilityKit';
import { setAutoColorMode, setDarkColorMode, setLightColorMode } from './ColorModeChangeFunctions';

@HMRouter({
  pageUrl: RouterConsts.ColorModePage
})
@ComponentV2
export struct ColorModePage {
  private context: common.UIAbilityContext = this.getUIContext().getHostContext() as common.UIAbilityContext;
  @Local selectIndex: number = 0
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;
  list: Array<ColorModeItem> = [
    new ColorModeItem(
      CustomColorMode.Auto,
      $r('app.media.color_auto'),
      $r('app.string.color_mode_auto'),
      '选择后,将跟随设备的系统设置切换外观'
    ),
    new ColorModeItem(
      CustomColorMode.Light,
      $r('app.media.color_light'),
      $r('app.string.color_mode_light')
    ),
    new ColorModeItem(
      CustomColorMode.Dark,
      $r('app.media.color_dark'),
      $r('app.string.color_mode_dark')
    )
  ]

  aboutToAppear(): void {
    if (this.appUIState.customColorMode === CustomColorMode.Auto) {
      this.selectIndex = 0
    } else if (this.appUIState.customColorMode === CustomColorMode.Light) {
      this.selectIndex = 1
    } else if (this.appUIState.customColorMode === CustomColorMode.Dark) {
      this.selectIndex = 2
    }
  }

  build() {
    Column() {
      ActionBar({
        title: '外观'
      })
      Stack() {
        Grid() {
          ForEach(this.list, (item: ColorModeItem, index) => {
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
                Radio({ value: item.mode, group: 'radioGroup' }).checked(this.selectIndex == index)
                  .onChange(() => {
                    this.selectIndex = index
                    if (item.mode === CustomColorMode.Auto) {
                      setAutoColorMode(this.context)
                    } else if (item.mode === CustomColorMode.Light) {
                      setLightColorMode(this.context)
                    } else if (item.mode === CustomColorMode.Dark) {
                      setDarkColorMode(this.context)
                    }
                    AppPreference.getInstance().setCustomColorMode(item.mode)
                    this.appUIState.customColorMode = item.mode
                  })
              }
            }
          })
        }
        .width('100%')
        .height(230)
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
        .margin({ top: 10 })
    }.width('100%')
    .height('100%')
    .backgroundColor($r('app.color.start_window_background'))
    .expandSafeArea([SafeAreaType.SYSTEM], [SafeAreaEdge.BOTTOM])
  }
}

class ColorModeItem {
  mode: CustomColorMode
  imageSrc: Resource
  title: ResourceStr
  tip: string

  constructor(mode: CustomColorMode, imageSrc: Resource, title: ResourceStr, tip: string = '') {
    this.mode = mode
    this.imageSrc = imageSrc
    this.title = title
    this.tip = tip
  }
}