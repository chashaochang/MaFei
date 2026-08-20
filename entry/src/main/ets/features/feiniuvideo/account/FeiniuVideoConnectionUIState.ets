import { BaseUIState } from '../../../common/base/BaseUIState'

@ObservedV2
export class FeiniuVideoConnectionUIState extends BaseUIState {
  @Trace address: string = ''
  @Trace username: string = ''
  @Trace password: string = ''
  @Trace loading: boolean = false
  @Trace errorText: string = ''
}
