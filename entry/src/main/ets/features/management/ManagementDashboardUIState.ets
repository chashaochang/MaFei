import { BaseUIState } from '../../common/base/BaseUIState';
import {
  ManagementActivityItem,
  ManagementMediaCounts,
  ManagementPermissionState,
  ManagementSectionStatus,
  ManagementServerOverview,
  ManagementSessionItem,
  ManagementTaskItem,
  ManagementUserSummary
} from './ManagementModels';

@ObservedV2
export class ManagementDashboardUIState extends BaseUIState {
  @Trace permissionState: ManagementPermissionState = ManagementPermissionState.Checking
  @Trace permissionMessage: string = ''
  @Trace isRefreshing: boolean = false
  @Trace refreshStatus: RefreshStatus = RefreshStatus.Inactive
  @Trace refreshOffset: number = 0
  @Trace isStartingScan: boolean = false
  @Trace isScanSubmissionLocked: boolean = false
  @Trace serverStatus: ManagementSectionStatus = ManagementSectionStatus.Loading
  @Trace countsStatus: ManagementSectionStatus = ManagementSectionStatus.Loading
  @Trace usersStatus: ManagementSectionStatus = ManagementSectionStatus.Loading
  @Trace sessionsStatus: ManagementSectionStatus = ManagementSectionStatus.Loading
  @Trace tasksStatus: ManagementSectionStatus = ManagementSectionStatus.Loading
  @Trace activitiesStatus: ManagementSectionStatus = ManagementSectionStatus.Loading
  @Trace serverMessage: string = ''
  @Trace countsMessage: string = ''
  @Trace usersMessage: string = ''
  @Trace sessionsMessage: string = ''
  @Trace tasksMessage: string = ''
  @Trace activitiesMessage: string = ''
  @Trace server: ManagementServerOverview | null = null
  @Trace counts: ManagementMediaCounts | null = null
  @Trace users: ManagementUserSummary | null = null
  @Trace sessions: ManagementSessionItem[] = []
  @Trace tasks: ManagementTaskItem[] = []
  @Trace activities: ManagementActivityItem[] = []

  get isLibraryScanRunning(): boolean {
    return this.tasks.some((task: ManagementTaskItem) => task.key === 'RefreshLibrary' && task.busy)
  }

  get hasRunningTasks(): boolean {
    return this.tasks.some((task: ManagementTaskItem) => task.busy)
  }
}
