import { AppStorageV2 } from '@kit.ArkUI'
import { KeyCode } from '@kit.InputKit'
import { HMRouter, HMRouterMgr } from '@hadss/hmrouter'
import { ActionBar } from '../../../component/ActionBar'
import { AppRouteDestination } from '../../../component/AppRouteDestination'
import { RouterConsts } from '../../../common/RouterConsts'
import { AppUIState } from '../../../entity/AppUIState'
import {
  AppThemeMaterialRole,
  AppThemeSurfaceResolver
} from '../../../theme/AppThemeSurfaceResolver'
import { FeiniuVideoConnectionUIState } from './FeiniuVideoConnectionUIState'
import { FeiniuVideoConnectionViewModel } from './FeiniuVideoConnectionViewModel'

@HMRouter({
  pageUrl: RouterConsts.FeiniuVideoConnectPage,
  useNavDst: true
})
@ComponentV2
export struct FeiniuVideoConnectPage {
  @Builder
  private pageContent(showRouteActionBar: boolean): void {
    FeiniuVideoConnectScreen({ showRouteActionBar: showRouteActionBar })
  }

  build() {
    AppRouteDestination({
      title: $r('app.string.feiniu_video_connect_title'),
      contentBuilder: () => {
        this.pageContent(false)
      },
      legacyContentBuilder: () => {
        this.pageContent(true)
      }
    })
  }
}

@ComponentV2
export struct FeiniuVideoConnectScreen {
  @Param showRouteActionBar: boolean = true
  @Param embedded: boolean = false
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;
  @Local ui: FeiniuVideoConnectionUIState = new FeiniuVideoConnectionUIState(this.getUIContext())
  private vm: FeiniuVideoConnectionViewModel = new FeiniuVideoConnectionViewModel(this.ui)

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable)
  }

  private async submitLogin(): Promise<void> {
    if (!await this.vm.onLogin()) {
      return
    }
    if (this.embedded) {
      return
    }
    HMRouterMgr.getCurrentPathStack()?.clear()
    HMRouterMgr.to(RouterConsts.IndexPage).withAnimator(false).replace()
  }

  @Builder
  private connectionFormContent(nativeSurface: boolean) {
    AddressSelection({
      text: this.ui.address,
      errorText: '',
      nativeSurface: nativeSurface,
      onTextChange: (value: string) => {
        this.ui.address = value
        this.ui.errorText = ''
      },
      onSubmit: () => {
        this.submitLogin()
      }
    })

    Blank().height(16)
    AuthTextField({
      text: this.ui.username,
      placeholder: $r('app.string.feiniu_video_username_placeholder'),
      nativeSurface: nativeSurface,
      onTextChanged: (v: string) => {
        this.ui.username = v
      }
    })
    Blank().height(16)
    AuthTextField({
      text: this.ui.password,
      placeholder: $r('app.string.feiniu_video_password_placeholder'),
      password: true,
      nativeSurface: nativeSurface,
      onTextChanged: (v: string) => {
        this.ui.password = v
      }
    })
    AnimatedErrorText({
      errorText: this.ui.errorText,
      nativeSurface: nativeSurface
    }).padding({ top: 4 })
    Text($r('app.string.feiniu_video_password_notice'))
      .fontSize(12)
      .fontColor(nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_2'))
      .padding({ top: 6 })

    if (!this.ui.loading) {
      Blank().height(20)
      StyledTextButton({
        text: $r('app.string.feiniu_video_login_action'),
        enable: this.ui.address.trim().length > 0 && this.ui.username.trim().length > 0 &&
          this.ui.password.length > 0,
        nativeSurface: nativeSurface,
        onclick: (): void => {
          this.submitLogin()
        }
      })
    } else {
      Row() {
        LoadingProgress().width(32).height(32)
        Text($r('app.string.feiniu_video_logging_in'))
          .fontColor(nativeSurface ? $r('sys.color.font_secondary') : Color.Gray)
          .padding({ left: 12 })
      }.padding({ top: 24 })
    }
  }

  @Builder
  private nativeConnectionForm() {
    Column() {
      this.connectionFormContent(true)
    }
    .width('100%')
    .constraintSize({ maxWidth: 375 })
    .alignItems(HorizontalAlign.Start)
    .padding({ top: 16, bottom: 16, left: 12, right: 12 })
    .margin({ top: 20 })
  }

  @Builder
  private feiniuConnectionForm() {
    Column() {
      this.connectionFormContent(false)
    }
    .width('100%')
    .constraintSize({ maxWidth: 375 })
    .alignItems(HorizontalAlign.Start)
    .margin({ top: 30 })
  }

  build() {
    if (this.embedded) {
      if (this.useNativeSurface()) {
        this.nativeConnectionForm()
      } else {
        this.feiniuConnectionForm()
      }
    } else {
      Scroll() {
        Column() {
          if (this.showRouteActionBar) {
            ActionBar({
              title: $r('app.string.feiniu_video_connect_title'),
              showBackButton: true
            })
          }
          FeiniuVideoLogoHeader({ nativeSurface: this.useNativeSurface() })
          if (this.useNativeSurface()) {
            this.nativeConnectionForm()
          } else {
            this.feiniuConnectionForm()
          }
        }
        .width('100%')
        .height('100%')
        .justifyContent(FlexAlign.Start)
        .padding({ left: 16, right: 16 })
        .backgroundColor(this.showRouteActionBar && this.useNativeSurface() ?
          $r('app.color.native_canvas_background') : Color.Transparent)
      }
      .width('100%')
      .height('100%')
      .scrollable(ScrollDirection.Vertical)
      .align(Alignment.Top)
      .backgroundColor(this.showRouteActionBar ? AppThemeSurfaceResolver.routeBackground(
        this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable) : Color.Transparent)
    }
  }
}

@ComponentV2
struct FeiniuVideoLogoHeader {
  @Param nativeSurface: boolean = false

  build() {
    Column() {
      Image($r('app.media.feiniu_video_logo')).height(70)
      Text($r('app.string.feiniu_video_service_name'))
        .fontColor(this.nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary'))
        .fontSize(18)
        .fontWeight(FontWeight.Medium)
    }.padding({ top: 24, bottom: 2 })
  }
}

// Reusable text field for username / password
@Component
struct AuthTextField {
  @Prop text: string
  @Prop placeholder: ResourceStr
  @Prop password: boolean = false
  @Prop nativeSurface: boolean = false
  onTextChanged: (val: string) => void = () => {
  }
  onSubmit: () => void = () => {
  }

  build() {
    Stack() {
      TextInput({
        text: this.text,
        placeholder: this.placeholder,
      })
        .onChange((v) => this.onTextChanged(v))
        .width('100%')
        .padding({ left: 16, right: 16 })
        .cancelButton({
          style: CancelButtonStyle.INPUT,
          icon: {
            size: 18,
            color: this.nativeSurface ? $r('sys.color.icon_secondary') : $r('app.color.text_2')
          }
        })
        .onSubmit(() => this.onSubmit())
        .type(this.password ? InputType.Password : InputType.Normal)
        .maxLines(1)
        .borderWidth(this.nativeSurface ? 0 : 1)
        .borderColor($r('app.color.border_input'))
        .borderRadius(this.nativeSurface ? 14 : 8)
        .height(this.nativeSurface ? 52 : 56)
        .placeholderColor(this.nativeSurface ? $r('sys.color.font_secondary') : Color.Gray)
        .fontColor(this.nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary'))
        .backgroundColor(this.nativeSurface ? Color.Transparent : $r('app.color.component_bg'))
    }
    .height(this.nativeSurface ? 52 : 56)
    .borderRadius(this.nativeSurface ? 14 : 8)
    .clip(true)
    .alignContent(Alignment.Start)
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.AdaptiveInteractive,
      this.nativeSurface
    ))
  }
}


@Component
export struct AnimatedErrorText {
  @Prop errorText: string
  @Prop nativeSurface: boolean = false

  build() {
    if (this.errorText && this.errorText.length > 0) {
      Text(this.errorText)
        .width('100%')
        .fontColor(this.nativeSurface ? $r('app.color.status_warning') : Color.Red)
    } else {
      Blank()
    }
  }
}

@Component
export struct StyledTextButton {
  @Prop text: ResourceStr
  @Prop enable: boolean
  @Prop nativeSurface: boolean = false
  onclick: () => void = () => {
  }

  build() {
    Button(this.text, { type: ButtonType.Normal, stateEffect: true })
      .borderRadius(this.nativeSurface ? 24 : 8)
      .backgroundColor($r('app.color.color_main'))
      .fontColor(Color.White)
      .width('100%')
      .height(48)
      .onClick(() => this.onclick())
      .enabled(this.enable)
      .padding({ top: 4, bottom: 4 })
      .margin({ top: 24 })
      .attributeModifier(AppThemeSurfaceResolver.modifier(
        AppThemeMaterialRole.PrimaryInteractive,
        this.nativeSurface
      ))
  }
}

@Component
struct AddressSelection {
  @Prop text: string
  @Prop errorText: string
  @Prop nativeSurface: boolean = false
  onTextChange: (x: string) => void = () => {
  }
  onSubmit: () => void = () => {
  }

  build() {
    Column() {
      ServerUrlField({
        text: this.text,
        nativeSurface: this.nativeSurface,
        onTextChanged: this.onTextChange,
        onSubmit: this.onSubmit
      })
      AnimatedErrorText({
        errorText: this.errorText,
        nativeSurface: this.nativeSurface
      })
    }
  }
}

@Component
struct ServerUrlField {
  @Prop text: string
  @Prop nativeSurface: boolean = false
  onTextChanged: (val: string) => void = () => {
  }
  onSubmit: () => void = () => {
  }

  private openAccountHistory(): void {
    HMRouterMgr.to(RouterConsts.AccountPage).withParam({
      fromLogin: true
    }).push()
  }

  build() {
    Stack() {
      TextInput({
        text: this.text,
        placeholder: $r('app.string.feiniu_video_address_placeholder'),
      })
        .onChange((v) => {
          this.onTextChanged(v)
        })
        .width('100%')
        .padding({ left: 16, right: 80 })
        .onKeyEvent((event) => {
          if (event.keyCode === KeyCode.KEYCODE_ENTER) {
            this.onSubmit()
          }
        })
        .type(InputType.URL)
        .onSubmit(() => {
          this.onSubmit()
        })
        .maxLines(1)
        .borderWidth(this.nativeSurface ? 0 : 1)
        .borderColor($r('app.color.border_input'))
        .borderRadius(this.nativeSurface ? 14 : 8)
        .height(this.nativeSurface ? 52 : 56)
        .placeholderColor(this.nativeSurface ? $r('sys.color.font_secondary') : Color.Gray)
        .fontColor(this.nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary'))
        .backgroundColor(this.nativeSurface ? Color.Transparent : $r('app.color.component_bg'))
      Row({ space: 2 }) {
        if (this.text.length > 0) {
          Button({ type: ButtonType.Circle, stateEffect: true }) {
            SymbolGlyph($r('sys.symbol.xmark'))
              .fontSize(14)
              .fontColor([this.nativeSurface ?
                $r('sys.color.icon_secondary') : $r('app.color.text_2')])
          }
          .width(28)
          .height(28)
          .backgroundColor(Color.Transparent)
          .onClick((): void => {
            this.onTextChanged('')
          })
        }
        Button({ type: ButtonType.Circle, stateEffect: true }) {
          if (this.nativeSurface) {
            SymbolGlyph($r('sys.symbol.externaldrive'))
              .fontSize(19)
              .fontColor([$r('sys.color.icon_primary')])
          } else {
            Image($r('app.media.history'))
              .height(19)
              .fillColor($r('app.color.text_primary'))
          }
        }
        .width(28)
        .height(28)
        .backgroundColor(Color.Transparent)
        .accessibilityText($r('app.string.account_history_title'))
        .onClick((): void => {
          this.openAccountHistory()
        })
      }
      .padding({ right: 10 })
    }
    .height(this.nativeSurface ? 52 : 56)
    .borderRadius(this.nativeSurface ? 14 : 8)
    .clip(true)
    .alignContent(Alignment.End)
    .attributeModifier(AppThemeSurfaceResolver.modifier(
      AppThemeMaterialRole.AdaptiveInteractive,
      this.nativeSurface
    ))
  }
}
