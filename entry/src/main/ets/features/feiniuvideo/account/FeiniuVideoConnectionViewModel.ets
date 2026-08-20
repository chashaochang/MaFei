import { common } from '@kit.AbilityKit'
import { hilog } from '@kit.PerformanceAnalysisKit'
import { BaseViewModel } from '../../../common/base/BaseViewModel'
import { MediaProviderComposition } from '../../setting/account/MediaProviderComposition'
import {
  FeiniuVideoRequestError,
  FeiniuVideoRequestFailureKind
} from '../network/FeiniuVideoApiModels'
import { FeiniuVideoConnectionUIState } from './FeiniuVideoConnectionUIState'

export interface FeiniuVideoConnectionGateway {
  login(address: string, username: string, password: string): Promise<void>
}

export interface FeiniuVideoConnectionGatewayFactory {
  create(context: common.Context): Promise<FeiniuVideoConnectionGateway>
}

class DefaultFeiniuVideoConnectionGateway implements FeiniuVideoConnectionGateway {
  private readonly composition: MediaProviderComposition

  constructor(composition: MediaProviderComposition) {
    this.composition = composition
  }

  async login(address: string, username: string, password: string): Promise<void> {
    await this.composition.loginFeiniu(address, username, password)
  }
}

class DefaultFeiniuVideoConnectionGatewayFactory implements FeiniuVideoConnectionGatewayFactory {
  async create(context: common.Context): Promise<FeiniuVideoConnectionGateway> {
    const composition = await MediaProviderComposition.initialize(context)
    return new DefaultFeiniuVideoConnectionGateway(composition)
  }
}

export class FeiniuVideoConnectionViewModel extends BaseViewModel<FeiniuVideoConnectionUIState> {
  private readonly gatewayFactory: FeiniuVideoConnectionGatewayFactory

  constructor(ui: FeiniuVideoConnectionUIState,
    gatewayFactory: FeiniuVideoConnectionGatewayFactory =
      new DefaultFeiniuVideoConnectionGatewayFactory()) {
    super(ui)
    this.gatewayFactory = gatewayFactory
  }

  async onLogin(): Promise<boolean> {
    const address = this.ui.address.trim()
    const username = this.ui.username.trim()
    if (address.length === 0) {
      this.ui.errorText = this.localized('feiniu_video_invalid_address_error')
      return false
    }
    if (username.length === 0 || this.ui.password.length === 0) {
      this.ui.errorText = this.localized('feiniu_video_credentials_required_error')
      return false
    }

    this.ui.errorText = ''
    this.ui.loading = true
    this.appUIState.isConnecting = true
    try {
      const hostContext = this.ui.context.getHostContext() as common.Context
      const gateway = await this.gatewayFactory.create(hostContext)
      await gateway.login(address, username, this.ui.password)
      this.appUIState.isLogin = true
      return true
    } catch (error) {
      const normalizedError = error as Error
      this.logLoginFailure(normalizedError)
      this.ui.errorText = this.errorMessage(normalizedError)
      return false
    } finally {
      this.ui.loading = false
      this.appUIState.isConnecting = false
    }
  }

  private errorMessage(error: Error): string {
    if (!(error instanceof FeiniuVideoRequestError)) {
      return this.localized('feiniu_video_login_failed_error')
    }
    if (error.kind === FeiniuVideoRequestFailureKind.Connection) {
      return this.localized('feiniu_video_connection_error')
    }
    if (error.kind === FeiniuVideoRequestFailureKind.Authentication) {
      return this.localized('feiniu_video_invalid_credentials_error')
    }
    if (error.kind === FeiniuVideoRequestFailureKind.InvalidRequest) {
      return this.localized('feiniu_video_invalid_address_error')
    }
    if (error.kind === FeiniuVideoRequestFailureKind.Business && error.message.trim().length > 0) {
      return error.message.trim()
    }
    if (error.kind === FeiniuVideoRequestFailureKind.InvalidResponse) {
      return this.localized('feiniu_video_invalid_response_error')
    }
    return this.localized('feiniu_video_login_failed_error')
  }

  private logLoginFailure(error: Error): void {
    const kind = error instanceof FeiniuVideoRequestError
      ? error.kind
      : FeiniuVideoRequestFailureKind.Unknown
    const errorName = error.name || 'Error'
    hilog.error(0x0000, 'mafei-feiniu-login',
      'login failed stage=%{public}s kind=%{public}s type=%{public}s',
      'gateway', kind, errorName)
  }

  private localized(name: string): string {
    try {
      return this.ui.context.getHostContext()?.resourceManager.getStringByNameSync(name) || ''
    } catch (_error) {
      return ''
    }
  }
}
