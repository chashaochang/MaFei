const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('/Applications/DevEco-Studio.app/Contents/tools/ohpm/node_modules/typescript');

function host(initialStatus = 3, deviceType = 'tablet') {
  const status = { FULL_SCREEN: 1, MAXIMIZE: 2, FLOATING: 3 };
  const presentation = { ENTER_IMMERSIVE: 2, EXIT_IMMERSIVE: 1 };
  const calls = [];
  let current = initialStatus, requested = false, revision = 0;
  const window = {
    isInFreeWindowMode: () => true,
    getWindowStatus: () => current,
    maximize: async mode => {
      calls.push(['maximize', mode]);
      current = mode === presentation.ENTER_IMMERSIVE ? status.FULL_SCREEN : status.MAXIMIZE;
    },
    recover: async () => { calls.push(['recover']); current = status.FLOATING; },
  };
  const module = { exports: {} };
  const source = fs.readFileSync('harmonyApp/entry/src/main/ets/entryability/EntryAbility.ets', 'utf8');
  vm.runInNewContext(ts.transpileModule(source, { compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020,
  } }).outputText, { module, exports: module.exports, require(name) {
    if (name === '@kit.AbilityKit') return { UIAbility: class {} };
    if (name === '@kit.ArkUI') return { window: { WindowStatusType: status, MaximizePresentation: presentation } };
    if (name === '@kit.BasicServicesKit') return { deviceInfo: { deviceType } };
    if (name === '@kit.PerformanceAnalysisKit') return { hilog: { warn() {} } };
    if (name === 'libentry.so') return { default: {
      GetOhosPlayerOrientationRevision: () => revision,
      IsOhosPlayerFullscreenRequested: () => requested,
    } };
    return {};
  } });
  const ability = new module.exports.default();
  ability.publishSafeArea = () => calls.push(['publish']);
  return {
    calls, status,
    open: () => ability.applyPlayerWindowMode(window, true),
    close: () => ability.applyPlayerWindowMode(window, false),
    request: value => { requested = value; revision++; ability.applyPlayerWindowMode(window, true); },
    nativeStatus: value => { current = value; },
  };
}

test('tablet freeform and 2-in-1 both enter real immersive fullscreen and restore the floating window', async () => {
  for (const device of ['tablet', '2in1']) {
    const h = host(3, device);
    h.open(); assert.deepEqual(h.calls, []);
    h.request(true); await Promise.resolve();
    assert.deepEqual(h.calls[0], ['maximize', 2]);
    h.close(); await Promise.resolve();
    assert.deepEqual(h.calls[2], ['recover']);
  }
});

test('ordinary maximize is distinct from fullscreen and is restored on exit', async () => {
  const h = host(2);
  h.open(); h.request(true); await Promise.resolve();
  h.request(false); await Promise.resolve();
  assert.deepEqual(h.calls.filter(call => call[0] === 'maximize'), [['maximize', 2], ['maximize', 1]]);
});

test('opening and closing playback does not exit an already fullscreen application', () => {
  const h = host(1);
  h.open(); h.close();
  assert.deepEqual(h.calls, []);
});

test('native exit is not undone by the poller and the next explicit request can re-enter', async () => {
  const h = host();
  h.open(); h.request(true); await Promise.resolve();
  h.nativeStatus(3);
  h.open(); h.open();
  assert.equal(h.calls.filter(call => call[0] === 'maximize').length, 1);
  h.request(true); await Promise.resolve();
  assert.equal(h.calls.filter(call => call[0] === 'maximize').length, 2);
});

test('rapid close during enter is restored after the in-flight transition settles', async () => {
  const h = host();
  h.open(); h.request(true); h.close();
  await Promise.resolve();
  h.close(); await Promise.resolve();
  assert.equal(h.calls.filter(call => call[0] === 'recover').length, 1);
});
