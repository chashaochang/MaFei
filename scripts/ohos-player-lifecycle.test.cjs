const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('/Applications/DevEco-Studio.app/Contents/tools/ohpm/node_modules/typescript');

test('ability visibility reaches the shared playback reducer before native surface observers', () => {
  const events = [], module = { exports: {} };
  const source = fs.readFileSync('harmonyApp/entry/src/main/ets/entryability/EntryAbility.ets', 'utf8');
  vm.runInNewContext(ts.transpileModule(source, { compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020,
  } }).outputText, { module, exports: module.exports, require(name) {
    if (name === '@kit.AbilityKit') return { UIAbility: class {
      context = { eventHub: { emit: event => events.push(event) } };
    } };
    if (name === 'libentry.so') return { default: { SetOhosApplicationForeground: visible => events.push(visible) } };
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
