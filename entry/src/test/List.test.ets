import localUnitTest from './LocalUnit.test';
import nativeThemeCapabilityTest from './NativeThemeCapability.test';

export default function testsuite() {
  localUnitTest();
  nativeThemeCapabilityTest();
}
