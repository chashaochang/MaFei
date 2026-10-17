const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const ts = require(process.env.MAFEI_TYPESCRIPT || 'typescript');

const read = relative => fs.readFileSync(path.join(__dirname, '..', relative), 'utf8');
const flush = () => new Promise(resolve => setImmediate(resolve));
function deferred() {
  let resolve;
  const promise = new Promise(done => { resolve = done; });
  return { promise, resolve };
}
function harness({ resolveGate, writeGate, failure } = {}) {
  const calls = [], warnings = [];
  let resolves = 0, awake = false;
  const target = {
    async setWindowKeepScreenOn(value) {
      calls.push(value);
      if (failure?.(value)) throw Error('window unavailable');
      if (writeGate) await writeGate.promise;
      awake = value;
    },
  };
  const module = { exports: {} };
  vm.runInNewContext(ts.transpileModule(read('harmonyApp/entry/src/main/ets/player/PlayerScreenAwake.ets'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText, {
    module, exports: module.exports, require(name) {
      if (name === '@kit.PerformanceAnalysisKit') return { hilog: { warn: (...args) => warnings.push(args) } };
      if (name === '@kit.ArkUI') return {};
      throw Error(name);
    },
  });
  const host = new module.exports.PlayerScreenAwake();
  const resolveWindow = async () => {
    resolves++;
    if (resolveGate) await resolveGate.promise;
    return target;
  };
  return { calls, warnings, host, target,
    set: (owner, requested) => host.update(owner, requested, resolveWindow),
    get awake() { return awake; }, get resolves() { return resolves; },
  };
}

test('playing and buffering keep the window awake without repeating the native call on progress', async () => {
  const h = harness(), owner = {};
  h.set(owner, true); await flush();
  for (let i = 0; i < 100; i++) h.set(owner, true);
  await flush(); assert.deepEqual(h.calls, [true]); assert.equal(h.resolves, 1);
  h.set(owner, false); await flush(); assert.deepEqual(h.calls, [true, false]); assert.equal(h.awake, false);
  h.set(owner, true); await flush(); assert.deepEqual(h.calls, [true, false, true]);
});

test('background or disposal before window resolution cannot enable a retired request', async () => {
  const gate = deferred(), h = harness({ resolveGate: gate }), owner = {};
  h.set(owner, true); h.set(owner, false); gate.resolve(); await flush();
  assert.deepEqual(h.calls, []); assert.equal(h.awake, false);
});

test('background or disposal during the enable write is followed by release', async () => {
  const gate = deferred(), h = harness({ writeGate: gate }), owner = {};
  h.set(owner, true); await flush(); h.set(owner, false); gate.resolve(); await flush();
  assert.deepEqual(h.calls, [true, false]); assert.equal(h.awake, false);
});

test('surface replacement cannot let the old surface release the new player request', async () => {
  const h = harness(), old = {}, next = {};
  h.set(old, true); await flush(); h.set(next, true); h.set(old, false); await flush();
  assert.deepEqual(h.calls, [true]); assert.equal(h.awake, true);
  h.set(next, false); await flush(); assert.deepEqual(h.calls, [true, false]);
});

test('a new surface arriving during release reacquires after that write finishes', async () => {
  const gate = deferred(), h = harness(), old = {}, next = {};
  h.set(old, true); await flush();
  const write = h.target.setWindowKeepScreenOn;
  h.target.setWindowKeepScreenOn = async value => { if (!value) await gate.promise; await write(value); };
  h.set(old, false); h.set(next, true); gate.resolve(); await flush();
  assert.deepEqual(h.calls, [true, false, true]); assert.equal(h.awake, true);
});

test('a failed window write is contained and a later lifecycle update can retry', async () => {
  let fail = true;
  const h = harness({ failure: () => { const result = fail; fail = false; return result; } }), owner = {};
  h.set(owner, true); await flush(); assert.equal(h.awake, false); assert.equal(h.warnings.length, 1);
  h.set(owner, true); await flush(); assert.equal(h.awake, true);
  h.set(owner, false); await flush(); assert.equal(h.awake, false);
});

test('failed release remains tracked so later cleanup retries the same window', async () => {
  let fail = true;
  const h = harness({ failure: value => !value && fail && (fail = false, true) }), owner = {};
  h.set(owner, true); await flush(); h.set(owner, false); await flush(); assert.equal(h.awake, true);
  h.set(owner, false); await flush(); assert.equal(h.awake, false);
  assert.deepEqual(h.calls, [true, false, false]); assert.equal(h.resolves, 1);
});

test('the shared video request reaches OHOS independently of command IDs and engine type', () => {
  const binding = read('composeApp/src/sharedUiMain/kotlin/cn/mafei/cmp/playback/PlayerBindings.kt');
  const kotlin = read('composeApp/src/ohosMain/kotlin/cn/mafei/cmp/OhosPlayerHost.kt');
  const arkts = read('harmonyApp/entry/src/main/ets/player/PlayerInterop.ets');
  assert.match(binding, /videoSurface = [\s\S]*LocalVideoScreenAwake provides state\.keepVideoScreenAwake\(\)/);
  assert.match(kotlin, /"keepScreenAwake"\(keepScreenAwake\)/);
  assert.match(kotlin, /toJsParameter\(latestSystemActions, artworkData, keepScreenAwake\)/);
  assert.match(arkts, /onArgsChanged\(\): void \{\s*this\.syncScreenAwake\(\)/);
  assert.match(arkts, /this\.hostMounted && this\.hostForeground &&\s*this\.args\.keepScreenAwake && !this\.args\.audioOnly/);
  for (const hook of ['onHostBackground', 'onHostForeground', 'aboutToAppear', 'aboutToDisappear']) {
    const start = arkts.indexOf(`${hook}`), end = arkts.indexOf('\n  }', start);
    assert.match(arkts.slice(start, end), /this\.syncScreenAwake\(\)/, hook);
  }
});

test('Android and iOS consume the same video-only request and clean up on disposal', () => {
  const android = read('composeApp/src/androidMain/kotlin/cn/mafei/cmp/AndroidPlayerHost.kt');
  const ios = read('composeApp/src/iosMain/kotlin/cn/mafei/cmp/IosPlayerHost.kt');
  const lifecycle = read('composeApp/src/iosMain/kotlin/cn/mafei/cmp/MainViewController.kt');
  const idle = read('composeApp/src/iosMain/kotlin/cn/mafei/cmp/IosVideoScreenAwake.kt');
  assert.match(android, /update = \{ view -> view\.keepScreenOn = keepScreenAwake \}/);
  assert.match(android, /onRelease = \{ view ->\s*view\.keepScreenOn = false/);
  assert.match(ios, /LocalVideoScreenAwake\.current/);
  assert.match(ios, /onDispose \{ IosVideoScreenAwake\.setRequested\(screenAwakeOwner, false\) \}/);
  assert.match(lifecycle, /IosVideoScreenAwake\.setForeground\(foreground\)/);
  assert.match(idle, /idleTimerDisabled = foreground && owners\.isNotEmpty\(\)/);
  const locals = read('composeApp/src/sharedUiMain/kotlin/cn/mafei/cmp/PlatformPlaybackActions.kt');
  assert.match(locals, /LocalVideoScreenAwake = staticCompositionLocalOf \{ false \}/);
});
