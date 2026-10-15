import { BaseUIState } from '../../../common/base/BaseUIState'
import { ManagementLibraryPathEntry } from './ManagementLibraryModels'

@ObservedV2
export class ManagementLibraryPathBrowserUIState extends BaseUIState {
  @Trace entries: ManagementLibraryPathEntry[] = []
  @Trace currentPath: string = ''
  @Trace manualPath: string = ''
  @Trace validatedManualPath: string = ''
  @Trace isLoading: boolean = false
  @Trace isValidating: boolean = false
  @Trace errorMessage: string = ''
  @Trace validationMessage: string = ''
  scroller: Scroller = new Scroller()
}
