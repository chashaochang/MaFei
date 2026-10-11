const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const ts = require('/Applications/DevEco-Studio.app/Contents/tools/ohpm/node_modules/typescript');

function harness(startOverride) {
  const calls = [], grants = [];
  const source = fs.readFileSync(path.join(__dirname, '../harmonyApp/entry/src/main/ets/player/PlayerBackgroundAudio.ets'), 'utf8');
  const module = { exports: {} };
  vm.runInNewContext(ts.transpileModule(source, { compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020,
  } }).outputText, { module, exports: module.exports, require(name) {
    if (name === '@kit.AbilityKit') return { wantAgent: {
      OperationType: { START_ABILITY: 1 }, WantAgentFlags: { UPDATE_PRESENT_FLAG: 1 },
      getWantAgent: async info => info,
    } };
    if (name === '@kit.BackgroundTasksKit') return { backgroundTaskManager: {
      BackgroundMode: { AUDIO_PLAYBACK: 1 },
      startBackgroundRunning: async () => { calls.push('start'); if (startOverride) await startOverride(); },
      stopBackgroundRunning: async () => { calls.push('stop'); },
    } };
    if (name === '@kit.PerformanceAnalysisKit') return { hilog: { warn() {} } };
    throw Error(name);
  } });
  const host = new module.exports.PlayerBackgroundAudio();
  const bind = () => host.bind({ abilityInfo: { bundleName: 'test', name: 'EntryAbility' } }, value => grants.push(value));
  bind();
  return { host, calls, grants, bind, drain: async () => { await host.operations; } };
}

test('only confirmed playing audio acquires the task; pause and close release it', async () => {
  const h = harness(); h.host.update(false, 'playing'); await h.drain();
  assert.deepEqual(h.calls, []);
  h.host.update(true, 'ready'); await h.drain(); assert.deepEqual(h.calls, []);
  h.host.update(true, 'playing'); await h.drain(); assert.deepEqual(h.grants, [true]);
  h.host.update(true, 'buffering'); await h.drain(); assert.deepEqual(h.calls, ['start']);
  h.host.update(true, 'paused'); await h.drain(); assert.deepEqual(h.grants, [true, false]);
  h.host.update(true, 'playing'); await h.drain(); h.host.close(); await h.drain();
  assert.deepEqual(h.calls, ['start', 'stop', 'start', 'stop']); assert.equal(h.host.active, false);
});

test('a retired asynchronous start is stopped and never grants the new source', async () => {
  let finish;
  const h = harness(() => new Promise(resolve => { finish = resolve; }));
  h.host.update(true, 'playing'); await new Promise(resolve => setImmediate(resolve));
  h.bind(); finish(); await h.drain();
  assert.deepEqual(h.calls, ['start', 'stop']); assert.deepEqual(h.grants, []);
});

test('failed task creation never grants background playback and is retryable', async () => {
  let attempts = 0;
  const h = harness(() => { if (++attempts === 1) throw Error('permission unavailable'); });
  h.host.update(true, 'playing'); await h.drain(); assert.equal(h.host.active, false);
  h.host.update(true, 'paused'); h.host.update(true, 'playing'); await h.drain();
  assert.equal(h.host.active, true); assert.deepEqual(h.grants, [true]);
});
