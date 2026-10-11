import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, copyFileSync, readFileSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
import { verifyFoundation } from './verify-cmp-foundation.mjs';

const source = resolve(dirname(fileURLToPath(import.meta.url)), '..');
function fixture(run) {
  const root = mkdtempSync(resolve(tmpdir(), 'mafei-foundation-test-'));
  const files = ['settings.gradle.kts', 'build.gradle.kts', 'gradle.properties', 'gradle/libs.versions.toml',
    'harmonyApp/entry/build-profile.json5', 'harmonyApp/oh-package.json5', 'harmonyApp/entry/oh-package.json5', 'iosApp/MaFei.xcodeproj/project.pbxproj',
    'iosApp/MaFei/Info.plist', 'composeApp/src/androidMain/AndroidManifest.xml'];
  const settings = readFileSync(resolve(source, 'settings.gradle.kts'), 'utf8');
  for (const match of settings.matchAll(/"(:[a-zA-Z0-9-]+(?::[a-zA-Z0-9-]+)*)"/g)) {
    files.push(`${match[1].slice(1).replaceAll(':', '/')}/build.gradle.kts`);
  }
  try {
    for (const file of files) {
      mkdirSync(dirname(resolve(root, file)), { recursive: true });
      copyFileSync(resolve(source, file), resolve(root, file));
    }
    run(root);
  } finally { rmSync(root, { recursive: true }); } // Only this test's mkdtemp directory.
}
function replace(root, file, from, to) {
  const path = resolve(root, file);
  const text = readFileSync(path, 'utf8');
  assert.ok(text.includes(from), 'Mutation must execute');
  writeFileSync(path, text.replace(from, to));
}
test('current frozen foundation passes', () => assert.match(verifyFoundation(source), /verified/));
test('missing module fails', () => fixture(root => {
  rmSync(resolve(root, 'feature/home/build.gradle.kts'));
  assert.throws(() => verifyFoundation(root), /missing module/);
}));
test('Compose drift fails', () => fixture(root => {
  replace(root, 'gradle/libs.versions.toml', 'compose = "1.9.2-1.1.0"', 'compose = "1.9.2"');
  assert.throws(() => verifyFoundation(root), /compose: expected/);
}));
test('root Harmony Compose package must match the frozen release', () => fixture(root => {
  replace(root, 'harmonyApp/oh-package.json5', '"@cpf-kmp-cmp/compose": "1.9.2-1.1.0"', '"@cpf-kmp-cmp/compose": "1.9.2-1.0.0"');
  assert.throws(() => verifyFoundation(root), /root compose HAR/);
}));
test('hardcoded Kotlin dependency cannot retain a different CPF release', () => fixture(root => {
  replace(root, 'build.gradle.kts', 'kotlin-test:2.2.21-1.1.0', 'kotlin-test:2.2.21-1.0.0');
  assert.throws(() => verifyFoundation(root), /hardcoded CPF dependency/);
}));
test('hardcoded navigation dependency cannot retain a different CPF release', () => fixture(root => {
  replace(root, 'common/ui/build.gradle.kts', 'navigationevent-compose:1.9.2-1.1.0', 'navigationevent-compose:1.9.2-1.0.0');
  assert.throws(() => verifyFoundation(root), /hardcoded CPF dependency/);
}));
test('x64 target fails', () => fixture(root => {
  replace(root, 'composeApp/build.gradle.kts', 'iosArm64()', 'iosX64()');
  assert.throws(() => verifyFoundation(root), /x64 target/);
}));
test('unfiltered Harmony dependency ABI fails', () => fixture(root => {
  replace(root, 'harmonyApp/entry/build-profile.json5', '"**/x86_64/**", ', '');
  assert.throws(() => verifyFoundation(root), /dependency ABI x86_64/);
}));
test('iOS high-refresh startup requirement fails closed', () => fixture(root => {
  replace(root, 'iosApp/MaFei/Info.plist', '<key>CADisableMinimumFrameDurationOnPhone</key><true/>', '');
  assert.throws(() => verifyFoundation(root), /high-refresh/);
}));
test('wrong Android launcher package fails', () => fixture(root => {
  replace(root, 'composeApp/src/androidMain/AndroidManifest.xml', 'cn.mafei.cmp.MainActivity', '.MainActivity');
  assert.throws(() => verifyFoundation(root), /launcher/);
}));
