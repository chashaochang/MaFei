import { AppUIState } from "../../entity/AppUIState"
import { AppStorageV2 } from "@kit.ArkUI"
import { ActionBar } from "../../component/ActionBar"
import { ConnectionUIState } from "./ConnectionUIState"
import { ConnectionViewModel } from "./ConnectionViewModel"
import { ServerSelectionMode } from "../../ui/state/ServerSelectionMode"
import { ServerSuggestion } from "../serverdiscovery/ServerSuggestion"
import { KeyCode } from "@kit.InputKit"
import { CheckUrlStateType, Unchecked, Error } from "../../ui/state/CheckUrlState"
import { inputMethod } from "@kit.IMEKit"
import { HMRouterMgr } from "@hadss/hmrouter"
import { RouterConsts } from "../../common/RouterConsts"
import { AppThemeMaterialRole, AppThemeSurfaceResolver } from "../../theme/AppThemeSurfaceResolver"

@ComponentV2
export struct ConnectScreen {
  @Param isFromAddAccount: boolean = false
  @Param showRouteActionBar: boolean = true
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;
  @Local ui: ConnectionUIState = new ConnectionUIState(this.getUIContext())
  vm: ConnectionViewModel = new ConnectionViewModel(this.ui)
  @Consumer() showExternalConnectionError: boolean = false

  aboutToAppear(): void {
    this.ui.externalError = this.showExternalConnectionError
    if (!this.isFromAddAccount) {
      this.vm.init()
    }
  }

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable)
  }

  @Builder
  private connectionFormContent(nativeSurface: boolean) {
    AddressSelection({
      text: this.ui.hostname,
      errorText: this.showExternalConnectionError ? '无法建立连接。 请检查主机名和你的网络连接。' :
        (this.ui.checkUrlState.type === CheckUrlStateType.Error ? (this.ui.checkUrlState as Error).message :
          undefined),
      loading: this.ui.checkUrlState.type === CheckUrlStateType.Pending,
      nativeSurface: nativeSurface,
      onTextChange: (value: string) => {
        this.ui.externalError = false
        this.ui.checkUrlState = new Unchecked()
        this.ui.hostname = value
      },
      onDiscoveryClick: () => {
        this.ui.externalError = false
        inputMethod.getController().hideTextInput()
      },
      onSubmit: () => {

      }
    })

    Blank().height(16)
    AuthTextField({
      text: this.ui.username,
      placeholder: '请输入用户名',
      nativeSurface: nativeSurface,
      onTextChanged: (v: string) => {
        this.ui.username = v
      }
    })
    Blank().height(16)
    AuthTextField({
      text: this.ui.password,
      placeholder: '请输入密码',
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
    Row({ space: 4 }) {
      Toggle({ type: ToggleType.Checkbox, isOn: this.ui.rememberPwd })
        .width(16)
        .onChange((isOn) => {
          this.ui.rememberPwd = isOn
        })
      Text("记住密码")
        .fontSize(12)
        .fontColor(nativeSurface ? $r('sys.color.font_secondary') : Color.Gray)
    }.onClick(() => {
      this.ui.rememberPwd = !this.ui.rememberPwd;
    }).margin({
      top: 4
    })

    if (!this.ui.loading) {
      Blank().height(20)
      StyledTextButton({
        text: '登录',
        enable: this.ui.username.length > 0 && this.ui.password.length > 0,
        nativeSurface: nativeSurface,
        onclick: (): void => {
          this.vm.onLogin(this.isFromAddAccount)
        }
      })
    } else {
      Row() {
        LoadingProgress().width(32).height(32)
        Text('正在登录...')
          .fontColor(nativeSurface ? $r('sys.color.font_secondary') : Color.Gray)
          .padding({ left: 12 })
      }.padding({ top: 24 })
    }
    Text("发现局域网内的设备")
      .fontSize(12)
      .fontColor(nativeSurface ? $r('sys.color.font_secondary') : $r('app.color.text_2'))
      .padding({ top: 24, bottom: 8 })
      .onClick(() => {
        HMRouterMgr.to(RouterConsts.ServerDiscoveryPage)
          .onResult((res) => {
            if (res && res.result) {
              this.ui.hostname = res.result as string
              inputMethod.getController().showTextInput()
            }
          })
          .push()
      })
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
    Scroll() {
      Column() {
        if (this.showRouteActionBar) {
          ActionBar({
            title: '',
            showBackButton: this.isFromAddAccount
          })
        }
        LogoHeader({ nativeSurface: this.useNativeSurface() })
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
    .height('100%')
    .width('100%')
    .scrollable(ScrollDirection.Vertical)
    .align(Alignment.Top)
    .backgroundColor(this.showRouteActionBar ? AppThemeSurfaceResolver.routeBackground(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable) : Color.Transparent)
  }
}

@ComponentV2
export struct LogoHeader {
  @Param nativeSurface: boolean = false

  build() {
    Column() {
      Image($r('app.media.app_icon')).height(70)
      Text($r('app.string.app_name'))
        .fontColor(this.nativeSurface ? $r('sys.color.font_primary') : $r('app.color.text_primary'))
        .fontSize(18)
        .fontWeight(FontWeight.Medium)
    }.padding({ bottom: 2 })
  }
}

// Reusable text field for username / password
@Component
struct AuthTextField {
  @Prop text: string
  // @Prop icon: ResourceStr
  @Prop placeholder: string
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
      // Image(this.icon).height(24).margin({ left: 16 })
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
  @Prop text: string
  @Prop enable: boolean
  @Prop nativeSurface: boolean = false
  onclick: () => void = () => {
  }

  build() {
    Button(this.text, { type: ButtonType.Normal, stateEffect: true })
      .borderRadius(this.nativeSurface ? 24 : 8)
      .backgroundColor(this.nativeSurface ? Color.Transparent : 0x317aff)
      .fontColor(this.nativeSurface ? $r('sys.color.font_primary') : Color.White)
      .width('100%')
      .height(48)
      .onClick(() => this.onclick())
      .enabled(this.enable)
      .padding({ top: 4, bottom: 4 })
      .margin({ top: 24 })
      .attributeModifier(AppThemeSurfaceResolver.modifier(
        AppThemeMaterialRole.AdaptiveInteractive,
        this.nativeSurface
      ))
  }
}

@Component
struct AddressSelection {
  @Prop text: string
  @Prop errorText: string
  @Prop loading: boolean
  @Prop nativeSurface: boolean = false
  onTextChange: (x: string) => void = () => {
  }
  onDiscoveryClick: () => void = () => {
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
      // if (!this.loading) {
      //   Blank().height(12)
      //   StyledTextButton({
      //     text: '连接服务器',
      //     enable: this.text.length > 0,
      //     onclick: this.onSubmit,
      //   })
      //   // StyledTextButton({
      //   //   text: '选择服务器',
      //   //   onclick: this.onDiscoveryClick
      //   // })
      // } else {
      //
      // }
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
        placeholder: '请输入IP地址/域名',
      })
        .onChange((v) => {
          this.onTextChanged(v)
        })
        .width('100%')
        .padding({ left: 16, right: 48 })
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
      if (this.nativeSurface) {
        SymbolGlyph($r('sys.symbol.externaldrive'))
          .fontSize(20)
          .fontColor([$r('sys.color.icon_primary')])
          .margin({ right: 14 })
          .onClick(() => this.openAccountHistory())
      } else {
        Image($r('app.media.history'))
          .height(20)
          .fillColor($r('app.color.text_primary'))
          .margin({ right: 12 })
          .onClick(() => this.openAccountHistory())
      }
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
