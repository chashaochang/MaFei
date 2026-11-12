const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require(process.env.MAFEI_TYPESCRIPT || 'typescript');

test('ability visibility reaches the shared playback reducer before native surface observers', () => {
  const events = [], module = { exports: {} };
  const visibility = { exports: {} };
  const native = { default: { SetOhosApplicationForeground: visible => events.push(visible) } };
  const visibilitySource = fs.readFileSync('harmonyApp/entry/src/main/ets/entryability/ApplicationVisibility.ets', 'utf8');
  vm.runInNewContext(ts.transpileModule(visibilitySource, { compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020,
  } }).outputText, { module: visibility, exports: visibility.exports, require(name) {
    if (name === '@kit.BasicServicesKit') return { deviceInfo: { deviceType: 'phone' } };
    if (name === 'libentry.so') return native;
    throw new Error(`Unexpected import: ${name}`);
  } });
  const source = fs.readFileSync('harmonyApp/entry/src/main/ets/entryability/EntryAbility.ets', 'utf8');
  vm.runInNewContext(ts.transpileModule(source, { compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020,
  } }).outputText, { module, exports: module.exports, require(name) {
    if (name === '@kit.AbilityKit') return { UIAbility: class {
      context = { eventHub: { emit: event => events.push(event) } };
    } };
    if (name === 'libentry.so') return native;
    if (name === './ApplicationVisibility') return visibility.exports;
    return {};
  } });
  const ability = new module.exports.default();
  ability.onBackground(); ability.onForeground();
  assert.deepEqual(events, [false, 'onBackground', true, 'onForeground']);
});

test('the lifecycle bridge has matching ArkTS, N-API and Kotlin declarations', () => {
  const cpp = fs.readFileSync('harmonyApp/entry/src/main/cpp/napi_init.cpp', 'utf8');
  const types = fs.readFileSync('harmonyApp/entry/src/main/cpp/types/libentry/Index.d.ts', 'utf8');
  const kotlin = fs.readFileSync('composeApp/src/ohosMain/kotlin/cn/mafei/cmp/MainArkUIViewController.kt', 'utf8');
  assert.match(cpp, /"SetOhosApplicationForeground"/);
  assert.match(cpp, /SetOhosApplicationForeground\(foreground\)/);
  assert.match(types, /SetOhosApplicationForeground: \(foreground: boolean\) => void/);
  assert.match(kotlin, /@CName\("SetOhosApplicationForeground"\)/);
  assert.match(kotlin, /ProcessApplication\.setForeground\(foreground\)/);
});
