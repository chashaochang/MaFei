import {
  CollectionTypeOptions,
  CountryInfo,
  CultureDto
} from 'sdk/src/main/ets/generated-client/models'
import { BaseUIState } from '../../../common/base/BaseUIState'
import {
  ManagementLibraryAvailableOptions,
  ManagementLibraryDraft,
  ManagementLibraryOperationCode,
  ManagementLibraryValidationCode
} from './ManagementLibraryModels'

@ObservedV2
export class ManagementLibraryEditorUIState extends BaseUIState {
  @Trace isNew: boolean = true
  @Trace original?: ManagementLibraryDraft = undefined
  @Trace draft: ManagementLibraryDraft = new ManagementLibraryDraft()
  @Trace availableOptions: ManagementLibraryAvailableOptions = {}
  @Trace countries: CountryInfo[] = []
  @Trace cultures: CultureDto[] = []
  @Trace createTypes: CollectionTypeOptions[] = []
  @Trace validationCode: ManagementLibraryValidationCode = ManagementLibraryValidationCode.None
  @Trace pathErrors: Map<string, string> = new Map<string, string>()
  @Trace dirty: boolean = false
  @Trace saving: boolean = false
  @Trace saveCode: ManagementLibraryOperationCode = ManagementLibraryOperationCode.None
  @Trace saveMessage: string = ''
  @Trace advancedExpanded: boolean = false
  @Trace discardConfirmVisible: boolean = false
  scroller: Scroller = new Scroller()
}
