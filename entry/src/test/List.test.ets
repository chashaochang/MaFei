import localUnitTest from './LocalUnit.test';
import nativeThemeCapabilityTest from './NativeThemeCapability.test';
import appPreferenceTest from './AppPreference.test';
import themePromptServiceTest from './ThemePromptService.test';
import appThemeOverlayPolicyTest from './AppThemeOverlayPolicy.test';

export default function testsuite() {
  localUnitTest();
  nativeThemeCapabilityTest();
  appPreferenceTest();
  themePromptServiceTest();
  appThemeOverlayPolicyTest();
}
