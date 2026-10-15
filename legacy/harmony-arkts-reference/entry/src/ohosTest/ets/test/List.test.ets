import abilityTest from './Ability.test';
import managementUserProtectionTest from './ManagementUserProtection.test';
import nativeThemeMaterialTest from './NativeThemeMaterial.test';

export default function testsuite() {
  abilityTest();
  managementUserProtectionTest();
  nativeThemeMaterialTest();
}
