import { HMRouter, HMRouterMgr } from "@hadss/hmrouter";
import { AxiosResponse } from "@ohos/axios";
import { AuthenticationResult } from "sdk/src/main/ets/generated-client/models";
import { getUserApi } from "sdk/src/main/ets/utils/api/user-api";
import { ApiClient } from "../../network/ApiClient";
import { AppStorageV2 } from "@kit.ArkUI";
import { AppUIState } from "../../entity/AppUIState";
import { AppThemeSurfaceResolver } from "../../theme/AppThemeSurfaceResolver";
import { AppThemeTokens } from "../../theme/AppThemeTokens";

@HMRouter({
  pageUrl: '/LoginPage',
  singleton: true
})
@ComponentV2
export struct LoginPage {
  // Local state for form fields and UI feedback
  @Local username: string = ''
  @Local password: string = ''
  @Local loading: boolean = false
  @Local errorText: string = ''
  appUIState: AppUIState = AppStorageV2.connect(AppUIState, () => new AppUIState())!;

  private useNativeSurface(): boolean {
    return AppThemeSurfaceResolver.useNativeSurface(
      this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable)
  }

  private async onLogin() {
    if (this.username.trim().length === 0 || this.password.length === 0) {
      this.errorText = '请输入用户名和密码'
      return
    }
    this.errorText = ''
    this.loading = true
    getUserApi(ApiClient.Instance()).authenticateUserByName({
      authenticateUserByName:{
        Username: this.username,
        Pw: this.password
      }
    }).then((result:AxiosResponse<AuthenticationResult>)=>{
      this.loading = false
      HMRouterMgr.pop({
        param:{
          loginRes: result.data
        }
      })
    }).catch((e:Error)=>{
      this.errorText = '登录失败，用户名或密码错误'
      this.loading = false
    })
  }

  @Builder
  private loginFormContent(nativeSurface: boolean) {
    Text('用户名')
      .padding({ bottom: 8 })
      .fontSize(18)
      .fontColor(nativeSurface ? $r('sys.color.font_primary') : Color.White)
    AuthTextField({
      text: this.username,
      icon: $r('app.media.ic_user'),
      placeholder: '请输入用户名',
      nativeSurface: nativeSurface,
      onTextChanged: (v: string) => {
        this.username = v
      },
      onSubmit: (): void => {
        this.onLogin()
      }
    })
    Blank().height(16)
    Text('密码')
      .padding({ bottom: 8 })
      .fontSize(18)
      .fontColor(nativeSurface ? $r('sys.color.font_primary') : Color.White)
    AuthTextField({
      text: this.password,
      icon: $r('app.media.ic_pwd'),
      placeholder: '请输入密码',
      password: true,
      nativeSurface: nativeSurface,
      onTextChanged: (v: string) => {
        this.password = v
      },
      onSubmit: (): void => {
        this.onLogin()
      }
    })
    AnimatedErrorText({ errorText: this.errorText }).padding({ top: 4 })
    if (!this.loading) {
      StyledTextButton({
        text: '登录',
        enable: this.username.length > 0 && this.password.length > 0,
        onclick: (): void => {
          this.onLogin()
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
  }

  @Builder
  private nativeLoginForm() {
    Column() {
      this.loginFormContent(true)
    }
    .width('100%')
    .constraintSize({ maxWidth: 560 })
    .alignItems(HorizontalAlign.Start)
    .padding(16)
    .borderRadius(AppThemeTokens.groupRadius(this.appUIState.themeStyle))
    .clip(true)
    .backgroundColor(AppThemeSurfaceResolver.contentGroupBackground())
  }

  @Builder
  private feiniuLoginForm() {
    Column() {
      this.loginFormContent(false)
    }
    .width('100%')
    .alignItems(HorizontalAlign.Start)
  }

  build() {
    Column() {
      LoginLogoHeader({ nativeSurface: this.useNativeSurface() })
      Blank().height(8)
      if (this.useNativeSurface()) {
        this.nativeLoginForm()
      } else {
        this.feiniuLoginForm()
      }

      Blank().layoutWeight(1)
    }
    .width('100%')
    .height('100%')
    .padding({ left: 16, right: 16 })
    .backgroundColor(this.useNativeSurface() ?
      AppThemeSurfaceResolver.routeBackground(
        this.appUIState.themeStyle, this.appUIState.nativeThemeAvailable) : '#101010')
    .expandSafeArea([SafeAreaType.SYSTEM],[SafeAreaEdge.BOTTOM])
  }
}

// Logo header similar to ConnectScreen's LogoHeader
@ComponentV2
struct LoginLogoHeader {
  @Param nativeSurface: boolean = false

  build() {
    Column({ space: 10 }) {
      Image($r('app.media.app_icon')).height(120)
      Text($r('app.string.app_name'))
        .fontColor(this.nativeSurface ? $r('sys.color.font_primary') : Color.White)
        .fontSize(36)
        .fontWeight(FontWeight.Bold)
      Text('欢迎登录')
        .fontColor(this.nativeSurface ? $r('sys.color.font_secondary') : Color.Gray)
        .fontSize(20)
    }.padding({ top: 48, bottom: 32 })
  }
}

// Reusable text field for username / password
@Component
struct AuthTextField {
  @Prop text: string
  @Prop icon: ResourceStr
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
        .padding({ left: 48, right: 16 })
        .onSubmit(() => this.onSubmit())
        .type(this.password ? InputType.Password : InputType.Normal)
        .maxLines(1)
        .borderWidth(this.nativeSurface ? 0 : 1)
        .borderColor($r('app.color.border_input'))
        .borderRadius(8)
        .height(56)
        .placeholderColor(Color.Gray)
        .fontColor(this.nativeSurface ? $r('sys.color.font_primary') : Color.White)
        .backgroundColor(this.nativeSurface ? Color.Transparent : $r('app.color.component_bg'))
      Image(this.icon).height(24).margin({ left: 16 })
    }.height(56).alignContent(Alignment.Start)
  }
}

@Component
export struct AnimatedErrorText {
  @Prop errorText: string

  build() {
    if (this.errorText && this.errorText.length > 0) {
      Text(this.errorText).width('100%').fontColor(Color.Red)
    } else {
      Blank()
    }
  }
}

@Component
export struct StyledTextButton {
  @Prop text: string
  @Prop enable: boolean
  onclick: () => void = () => {
  }

  build() {
    Button(this.text, { type: ButtonType.Normal, stateEffect: true })
      .borderRadius(8)
      .backgroundColor(0x317aff)
      .width('100%')
      .height(56)
      .onClick(() => this.onclick())
      .enabled(this.enable)
      .padding({ top: 4, bottom: 4 })
      .margin({ top: 24 })
  }
}
