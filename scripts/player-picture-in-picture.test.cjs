const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('/Applications/DevEco-Studio.app/Contents/tools/ohpm/node_modules/typescript');

function harness(createOverride, { failBackground = false, desktop = false } = {}) {
  const controllers = [], states = [], actions = [], failures = [], configurations = [], transitions = [];
  const context = { async moveAbilityToBackground() {
    transitions.push('background');
    if (failBackground) throw Error('background rejected');
  } };
  function make() {
    const c = { listeners: {}, starts: 0, stops: 0, sizes: [], controlStates: [],
      on(name, callback) { this.listeners[name] = callback; }, off(name) { delete this.listeners[name]; },
      async startPiP() { this.starts++; this.listeners.stateChange?.(2); },
      async stopPiP() { this.stops++; this.listeners.stateChange?.(4); },
      updateContentSize(width, height) { this.sizes.push([width, height]); },
      updatePiPControlStatus(type, status) { this.controlStates.push([type, status]); },
    }; controllers.push(c); return c;
  }
  const module = { exports: {} };
  const js = ts.transpileModule(fs.readFileSync('harmonyApp/entry/src/main/ets/player/PlayerPictureInPicture.ets', 'utf8'), {
    compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020 },
  }).outputText;
  vm.runInNewContext(js, { module, exports: module.exports, canIUse: () => true, require(name) {
    if (name === '@kit.ArkUI') return { PiPWindow: {
      create: async config => { configurations.push(config); return createOverride ? createOverride(make) : make(); },
      isPiPEnabled: () => true,
      PiPControlType: { VIDEO_PLAY_PAUSE: 0 }, PiPControlStatus: { PLAY: 1, PAUSE: 0 },
      PiPTemplateType: { VIDEO_PLAY: 1 }, PiPState: { STARTED: 2, STOPPED: 4, ABOUT_TO_RESTORE: 5, ERROR: 6 } },
      window: { getLastWindow: async () => ({ async minimize() { transitions.push('minimize'); } }) },
      promptAction: { showToast: options => failures.push(options) } };
    if (name === '@kit.BasicServicesKit') return { deviceInfo: { deviceType: desktop ? '2in1' : 'phone' } };
    if (name === '@kit.PerformanceAnalysisKit') return { hilog: { warn() {}, info() {} } };
    throw Error(name);
  } });
  const host = new module.exports.PlayerPictureInPicture();
  return { host, controllers, states, actions, failures, configurations, transitions,
    start: (snapshot = { widthPx: 1920, heightPx: 1080, playing: true }) =>
      host.start(context, {}, value => { states.push(value); transitions.push(`pip:${value}`); },
        value => { actions.push(value); transitions.push(value); }, snapshot),
    drain: async () => { await host.operations; await new Promise(resolve => setImmediate(resolve)); },
  };
}

test('native window delegates play/pause to shared controller and reuses active window', async () => {
  const h = harness(); h.start(); await h.drain(); h.start(); await h.drain();
  assert.equal(h.controllers.length, 1); assert.equal(h.controllers[0].starts, 1); assert.equal(h.host.active, true);
  h.controllers[0].listeners.controlPanelActionEvent('playbackStateChanged', 0);
  h.controllers[0].listeners.controlPanelActionEvent('playbackStateChanged', 1);
  assert.deepEqual(h.actions, ['pause', 'play']); assert.deepEqual(h.states, [true]);
});

test('entering PiP backgrounds the original ability only after playback has its PiP grant', async () => {
  const h = harness(); h.start(); await h.drain();
  assert.deepEqual(h.transitions, ['pip:true', 'background']);
  assert.equal(h.configurations[0].navigationId, undefined, 'CMP keeps its one ArkUI host page mounted');
  assert.equal(h.controllers[0].stops, 0);
});

test('desktop PiP minimizes its main window instead of calling the phone-only background API', async () => {
  const h = harness(undefined, { desktop: true }); h.start(); await h.drain();
  assert.deepEqual(h.transitions, ['pip:true', 'minimize']);
});

test('failed main-window handoff returns the surface without stopping playback or leaving a black page', async () => {
  const h = harness(undefined, { failBackground: true }); h.start(); await h.drain();
  assert.equal(h.host.active, false); assert.equal(h.controllers[0].stops, 1);
  assert.deepEqual(h.states, [true, false]); assert.deepEqual(h.actions, []);
  assert.equal(h.failures.length, 1);
});

test('closing the small window pauses the shared player before dropping the PiP grant', async () => {
  const h = harness(); h.start(); await h.drain();
  h.controllers[0].listeners.stateChange(4);
  assert.deepEqual(h.transitions.slice(-2), ['pause', 'pip:false']);
  assert.equal(h.host.active, false);
});

test('restoring the small window does not play, pause, reload or close the player page', async () => {
  const h = harness(); h.start(); await h.drain();
  h.controllers[0].listeners.stateChange(5);
  h.host.restoreToPlayer(); await h.drain();
  assert.equal(h.controllers[0].stops, 0, 'The system is already restoring its original surface');
  h.controllers[0].listeners.stateChange(4);
  assert.deepEqual(h.states, [true, false]); assert.deepEqual(h.actions, []);
});

test('opening the app while PiP is active returns the same surface once and preserves user pause', async () => {
  const h = harness(); h.start({ widthPx: 1920, heightPx: 1080, playing: false }); await h.drain();
  h.host.restoreToPlayer(); h.host.restoreToPlayer(); await h.drain();
  assert.equal(h.controllers.length, 1); assert.equal(h.controllers[0].stops, 1);
  assert.equal(h.controllers[0].starts, 1); assert.deepEqual(h.actions, []);
  assert.deepEqual(h.states, [true, false]);
});
test('close invalidates late controls synchronously and closes system window', async () => {
  const h = harness(); h.start(); await h.drain();
  const old = h.controllers[0], late = old.listeners.controlPanelActionEvent;
  h.host.close(); late('playbackStateChanged', 1); await h.drain();
  assert.equal(h.host.active, false); assert.equal(old.stops, 1); assert.deepEqual(h.actions, []);
  assert.equal(Object.keys(old.listeners).length, 0);
});
test('close during creation cannot install or start a retired window', async () => {
  let finish; const h = harness(make => new Promise(resolve => { finish = () => resolve(make()); }));
  h.start(); await Promise.resolve(); await Promise.resolve(); h.host.close(); finish(); await h.drain();
  assert.equal(h.controllers[0].starts, 0); assert.equal(h.controllers[0].stops, 1); assert.equal(h.host.active, false);
});
test('unavailable PiP reports failure without stopping the player', async () => {
  const h = harness(async () => { throw Error('unavailable'); }); h.start(); await h.drain();
  assert.deepEqual(h.states, [false]); assert.equal(h.failures.length, 1); assert.deepEqual(h.actions, []);
});

test('PiP uses media dimensions rather than the portrait surface aspect ratio', async () => {
  const h = harness(); h.start(); await h.drain();
  assert.equal(h.configurations[0].contentWidth, 1920);
  assert.equal(h.configurations[0].contentHeight, 1080);
  h.host.update({ widthPx: 1080, heightPx: 1920, playing: true });
  assert.deepEqual(h.controllers[0].sizes.at(-1), [1080, 1920]);
});

test('a video without known dimensions cannot start a wrong-ratio window and can retry later', async () => {
  const h = harness(); h.start({ widthPx: 0, heightPx: 0, playing: true }); await h.drain();
  assert.equal(h.controllers.length, 0); assert.deepEqual(h.states, [false]);
  assert.equal(h.failures.length, 1); assert.deepEqual(h.actions, []);
  h.start(); await h.drain();
  assert.equal(h.configurations[0].contentWidth, 1920);
  assert.equal(h.configurations[0].contentHeight, 1080);
  assert.equal(h.host.active, true);
});

test('native play/pause button follows the shared state without replaying it', async () => {
  const h = harness(); h.start({ widthPx: 1920, heightPx: 1080, playing: false }); await h.drain();
  const c = h.controllers[0];
  assert.deepEqual(c.controlStates.at(-1), [0, 0]);
  h.host.update({ widthPx: 1920, heightPx: 1080, playing: true });
  assert.deepEqual(c.controlStates.at(-1), [0, 1]);
  const sizes = c.sizes.length, updates = c.controlStates.length;
  h.host.update({ widthPx: 1920, heightPx: 1080, playing: true });
  assert.equal(c.sizes.length, sizes); assert.equal(c.controlStates.length, updates);
  assert.deepEqual(h.actions, []);
});

test('an action missing its explicit status must not fabricate a play command', async () => {
  const h = harness(); h.start(); await h.drain();
  h.controllers[0].listeners.controlPanelActionEvent('playbackStateChanged');
  assert.deepEqual(h.actions, []);
});

test('closing and restoring PiP follows native states without overriding pause', async () => {
  const h = harness(); h.start({ widthPx: 1920, heightPx: 1080, playing: false }); await h.drain();
  h.controllers[0].listeners.stateChange(5);
  assert.equal(h.host.active, true);
  h.controllers[0].listeners.stateChange(4);
  assert.equal(h.host.active, false);
  assert.deepEqual(h.states, [true, false]); assert.deepEqual(h.actions, []);
});
