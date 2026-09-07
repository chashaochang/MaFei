import { readFileSync, existsSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

// Source gate only. Artifact ABI, resources and first frame have separate
// acceptance evidence; a passing configuration gate cannot prove runtime.
export function verifyFoundation(root) {
  const failures = [];
  const check = (condition, message) => { if (!condition) failures.push(message); };
  const read = path => readFileSync(resolve(root, path), 'utf8');
  const catalog = read('gradle/libs.versions.toml');
  const version = key => catalog.match(new RegExp(`^${key} = "([^"]+)"`, 'm'))?.[1];
  const expected = {
    kotlin: '2.2.21-1.0.0', compose: '1.9.2-1.0.0',
    'compose-desktop': '1.9.2', coroutines: '1.10.2-1.0.0',
    serialization: '1.9.1-1.0.0', koin: '4.1.1-1.0.0',
  };
  for (const [name, value] of Object.entries(expected)) {
    check(version(name) === value, `${name}: expected frozen ${value}`);
  }
  check(catalog.includes('org.jetbrains.compose.desktop:desktop-jvm-macos-arm64'), 'Desktop must use macOS ARM64 artifact');
  const properties = read('gradle.properties');
  check(/^rendererBackend=skia\s*$/m.test(properties), 'Renderer must be skia');
  check(/^ohosSkikoVersion=0\.9\.22\.2-1\.0\.0\s*$/m.test(properties), 'OHOS Skiko version drift');
  const settings = read('settings.gradle.kts');
  const modules = [...settings.matchAll(/"(:[a-zA-Z0-9-]+(?::[a-zA-Z0-9-]+)*)"/g)].map(it => it[1]);
  check(modules.length > 0, 'No Gradle modules found');
  check(new Set(modules).size === modules.length, 'Duplicate Gradle module');
  for (const module of modules) {
    check(existsSync(resolve(root, module.slice(1).replaceAll(':', '/'), 'build.gradle.kts')), `${module}: missing module build file`);
  }
  for (const file of ['build.gradle.kts', 'composeApp/build.gradle.kts']) {
    check(!/\b(?:iosX64|ohosX64|macosX64)\s*\(/.test(read(file)), `${file}: Intel/x64 target forbidden`);
  }
  const app = read('composeApp/build.gradle.kts');
  const androidAbi = app.match(/ndk\s*\{([^}]+)\}/)?.[1];
  check(androidAbi?.trim() === 'abiFilters += "arm64-v8a"', 'Android must package only arm64-v8a');
  const harmony = JSON.parse(read('harmonyApp/entry/build-profile.json5'));
  check(JSON.stringify(harmony.buildOption.externalNativeOptions.abiFilters) === '["arm64-v8a"]', 'Harmony CMake ABI must be ARM64 only');
  const excludes = harmony.buildOption.nativeLib.filter.excludes;
  for (const abi of ['x86_64', 'x86', 'armeabi-v7a']) {
    check(excludes.includes(`**/${abi}/**`), `Harmony dependency ABI ${abi} is not excluded`);
  }
  const host = JSON.parse(read('harmonyApp/entry/oh-package.json5'));
  check(host.dependencies['@cpf-kmp-cmp/compose'] === expected.compose, 'compose HAR differs from CPF Compose');
  const ios = read('iosApp/MaFei.xcodeproj/project.pbxproj');
  check((ios.match(/ARCHS = arm64;/g) ?? []).length === 2, 'iOS Debug/Release must both target ARM64');
  check(ios.includes('TARGETED_DEVICE_FAMILY = "1,2"'), 'iOS host must support phone and tablet');
  check(/<key>CADisableMinimumFrameDurationOnPhone<\/key>\s*<true\s*\/>/.test(read('iosApp/MaFei/Info.plist')), 'iOS Compose high-refresh plist requirement missing');
  check(read('composeApp/src/androidMain/AndroidManifest.xml').includes('android:name="cn.mafei.cmp.MainActivity"'), 'Android launcher must match actual Kotlin package');
  if (failures.length) throw new Error(failures.join('\n'));
  return `CMP foundation: ${modules.length} modules, frozen versions and ARM-only host configuration verified`;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const root = process.argv[2] ?? resolve(dirname(fileURLToPath(import.meta.url)), '..');
  try { console.log(verifyFoundation(root)); }
  catch (error) { console.error(error.message); process.exitCode = 1; }
}
