import { BaseUIState } from '../../../common/base/BaseUIState'
import {
  ManagementLibraryItem,
  ManagementLibraryOperationCode
} from './ManagementLibraryModels'

@ObservedV2
export class ManagementLibrariesUIState extends BaseUIState {
  @Trace items: ManagementLibraryItem[] = []
  @Trace stale: boolean = false
  @Trace errorCode: ManagementLibraryOperationCode = ManagementLibraryOperationCode.None
  @Trace errorMessage: string = ''
  @Trace isRefreshing: boolean = false
  @Trace refreshStatus: RefreshStatus = RefreshStatus.Inactive
  @Trace refreshOffset: number = 0
  @Trace scanningAll: boolean = false
  @Trace busyLibraryId: string = ''
  @Trace selectedLibraryId: string = ''
  @Trace removeCandidate?: ManagementLibraryItem = undefined
  @Trace removeDialogVisible: boolean = false
  scroller: Scroller = new Scroller()
}
