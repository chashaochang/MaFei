import localUnitTest from './LocalUnit.test';
import nativeThemeCapabilityTest from './NativeThemeCapability.test';
import appPreferenceTest from './AppPreference.test';
import themePromptServiceTest from './ThemePromptService.test';
import appThemeOverlayPolicyTest from './AppThemeOverlayPolicy.test';
import breakpointPolicyTest from './BreakpointPolicy.test';
import homeShellPolicyTest from './HomeShellPolicy.test';
import homeInsetsPolicyTest from './HomeInsetsPolicy.test';
import homeDestinationTest from './HomeDestination.test';

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
}
