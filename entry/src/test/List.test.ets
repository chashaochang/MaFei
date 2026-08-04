import localUnitTest from './LocalUnit.test';
import nativeThemeCapabilityTest from './NativeThemeCapability.test';
import appPreferenceTest from './AppPreference.test';
import themePromptServiceTest from './ThemePromptService.test';
import appThemeOverlayPolicyTest from './AppThemeOverlayPolicy.test';
import breakpointPolicyTest from './BreakpointPolicy.test';
import homeShellPolicyTest from './HomeShellPolicy.test';
import homeInsetsPolicyTest from './HomeInsetsPolicy.test';
import homeDestinationTest from './HomeDestination.test';
import managementSessionsTest from './ManagementSessions.test';
import managementDevicesTest from './ManagementDevices.test';
import managementActivityTest from './ManagementActivity.test';
import managementNativePagePolicyTest from './ManagementNativePagePolicy.test';
import managementUsersTest from './ManagementUsers.test';
import managementTasksTest from './ManagementTasks.test';
import mineHistoryPlannerTest from './MineHistoryPlanner.test';
import managementLibrariesTest from './ManagementLibraries.test';

export default function testsuite() {
  localUnitTest();
  nativeThemeCapabilityTest();
  appPreferenceTest();
  themePromptServiceTest();
  appThemeOverlayPolicyTest();
  breakpointPolicyTest();
  homeShellPolicyTest();
  homeInsetsPolicyTest();
  homeDestinationTest();
  managementSessionsTest();
  managementTasksTest();
  managementDevicesTest();
  managementActivityTest();
  managementNativePagePolicyTest();
  managementUsersTest();
  mineHistoryPlannerTest();
  managementLibrariesTest();
}
