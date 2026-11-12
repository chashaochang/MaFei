const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require(process.env.MAFEI_TYPESCRIPT || 'typescript');

function load(file, imports, timers = {}) {
  const module = { exports: {} };
  const source = fs.readFileSync(file, 'utf8');
  vm.runInNewContext(ts.transpileModule(source, { compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020,
  } }).outputText, {
    module, exports: module.exports,
    require(name) {
      if (!(name in imports)) throw new Error(`Unexpected import: ${name}`);
      return imports[name];
    },
    ...timers,
  });
  return module.exports;
}

const flush = async () => { for (let i = 0; i < 8; i++) await Promise.resolve(); };
const launcherFile = 'harmonyApp/entry/src/main/ets/player/PlayerWindowLauncher.ets';
const abilityFile = 'harmonyApp/entry/src/main/ets/entryability/PlayerAbility.ets';

function launcherHost(deviceType = '2in1') {
  const calls = [];
  let mainRect = { width: 2200, height: 1400 };
  let id = 1, revision = 1, reject = false, pending;
  const native = {
    SetOhosPlayerWindowEnabled: enabled => calls.push(['enabled', enabled]),
    GetOhosPlayerWindowRequestId: () => id,
    GetOhosPlayerWindowRevision: () => revision,
    FailOhosPlayerWindow: failedId => calls.push(['failed', failedId]),
  };
  const exports = load(launcherFile, {
    '@kit.AbilityKit': {},
    '@kit.BasicServicesKit': { deviceInfo: { deviceType } },
    '@kit.PerformanceAnalysisKit': { hilog: { warn() {} } },
    'libentry.so': { default: native },
  }, {
    setInterval: callback => { pending = callback; return 1; },
    clearInterval: () => { pending = undefined; },
  });
  const launcher = new exports.PlayerWindowLauncher({
    applicationInfo: { name: 'example.app.dev' },
    startAbility: async (want, options) => {
      calls.push(['start', want, options]);
      if (reject) throw new Error('Window unavailable');
    },
  }, { getWindowProperties: () => ({ windowRect: mainRect }) });
  launcher.start();
  return {
    calls, tick: () => pending?.(), stop: () => launcher.stop(),
    select: next => { id = next; revision++; },
    revise: () => revision++,
    reject: () => { reject = true; },
    destroyed: exports.playerWindowDestroyed,
    resizeMain: (width, height) => { mainRect = { width, height }; },
  };
}

test('PC opens one singleton and only a new media revision activates it again', async () => {
  const h = launcherHost();
  h.tick(); h.tick(); await flush(); h.tick();
  assert.equal(h.calls.filter(c => c[0] === 'start').length, 1);
  const request = h.calls.find(c => c[0] === 'start');
  assert.equal(request[1].bundleName, 'example.app.dev');
  assert.equal(request[1].abilityName, 'PlayerAbility');
  assert.equal(request[2].withAnimation, false);
  h.revise(); h.tick(); await flush();
  assert.equal(h.calls.filter(c => c[0] === 'start').length, 2);
  h.select(0); h.tick();
  assert.equal(h.calls.filter(c => c[0] === 'start').length, 2);
});

test('a new player matches the current main window in pixels and reuse keeps the user size', async () => {
  const h = launcherHost();
  h.resizeMain(2560, 1600);
  h.tick(); await flush();
  let options = h.calls.filter(c => c[0] === 'start').at(-1)[2];
  assert.equal(options.windowWidth, 2560);
  assert.equal(options.windowHeight, 1600);
  h.resizeMain(1920, 1200);
  h.revise(); h.tick(); await flush();
  options = h.calls.filter(c => c[0] === 'start').at(-1)[2];
  assert.equal(options.windowWidth, undefined);
  assert.equal(options.windowHeight, undefined);
  h.destroyed(); h.tick(); await flush();
  options = h.calls.filter(c => c[0] === 'start').at(-1)[2];
  assert.equal(options.windowWidth, 1920);
  assert.equal(options.windowHeight, 1200);
});

test('phone and tablet retain their existing player presentation', () => {
  for (const device of ['phone', 'tablet']) {
    const h = launcherHost(device);
    h.tick(); h.stop();
    assert.deepEqual(h.calls, []);
  }
});

test('failed starts report the original request and a destroyed window permits retry', async () => {
  const h = launcherHost();
  h.reject(); h.tick(); h.select(2); await flush();
  assert.deepEqual(h.calls.filter(c => c[0] === 'failed'), [['failed', 1]]);
  h.tick(); await flush();
  assert.equal(h.calls.filter(c => c[0] === 'start').length, 2);
  h.destroyed(); h.tick(); await flush();
  assert.equal(h.calls.filter(c => c[0] === 'start').length, 3);
  h.stop(); h.tick();
  assert.deepEqual(h.calls.at(-1), ['enabled', false]);
});

function abilityHost(initialStatus = 3) {
  const calls = [], listeners = new Map();
  const status = { FULL_SCREEN: 1, MAXIMIZE: 2, FLOATING: 3 };
  const presentation = { ENTER_IMMERSIVE: 2, EXIT_IMMERSIVE: 1 };
  let id = 1, fullscreen = false, revision = 0, currentStatus = initialStatus;
  let timer, loadCallback, closeCount = 0;
  const native = {
    GetOhosPlayerWindowRequestId: () => id,
    GetOhosPlayerWindowFullscreenRevision: () => revision,
    IsOhosPlayerWindowFullscreenRequested: () => fullscreen,
    GetOhosPlayerWindowTitle: () => `Media ${id}`,
    CloseOhosPlayerWindow: closing => {
      calls.push(['close', closing]);
      if (closing === id) { id = 0; closeCount++; }
    },
    FailOhosPlayerWindow: failed => {
      calls.push(['failed', failed]);
      if (failed === id) id = 0;
    },
    UpdateOhosPlayerWindowMetrics: (...args) => calls.push(['metrics', ...args]),
  };
  const win = {
    on: (name, callback) => listeners.set(name, callback),
    off: name => listeners.delete(name),
    setWindowLimits: async () => {},
    setWindowLayoutFullScreen: async () => {},
    setWindowDecorHeight() {}, setWindowDecorVisible() {},
    setWindowTitleButtonVisible() {}, setWindowTitleMoveEnabled() {}, setDecorButtonStyle() {},
    setWindowTitle: title => calls.push(['title', title]),
    getUIContext: () => ({ setKeyboardAvoidMode() {}, px2vp: value => value / 2 }),
    getWindowProperties: () => ({ windowRect: { width: 2200, height: 1360 } }),
    getWindowAvoidArea: () => ({
      leftRect: { left: 0, width: 0 }, rightRect: { left: 0, width: 0 },
      topRect: { height: 0 }, bottomRect: { height: 0 },
    }),
    getTitleButtonRect: () => ({ top: 12, right: 1080, width: 120, height: 28 }),
    getWindowStatus: () => currentStatus,
    maximize: async mode => {
      calls.push(['maximize', mode]);
      currentStatus = mode === presentation.ENTER_IMMERSIVE ? status.FULL_SCREEN : status.MAXIMIZE;
    },
    recover: async () => { calls.push(['recover']); currentStatus = status.FLOATING; },
  };
  const exports = load(abilityFile, {
    '@kit.AbilityKit': { UIAbility: class {}, ConfigurationConstant: { ColorMode: { COLOR_MODE_DARK: 0 } } },
    '@kit.ArkUI': { KeyboardAvoidMode: { NONE: 0 }, window: {
      WindowStatusType: status, MaximizePresentation: presentation,
      AvoidAreaType: { TYPE_SYSTEM: 0, TYPE_NAVIGATION_INDICATOR: 1, TYPE_CUTOUT: 2 },
    } },
    '@kit.BasicServicesKit': { deviceInfo: { deviceType: '2in1' } },
    '@kit.PerformanceAnalysisKit': { hilog: { warn() {} } },
    'libentry.so': { default: native },
    '../player/PlayerWindowLauncher': { playerWindowDestroyed: () => calls.push(['destroyed']) },
    './ApplicationVisibility': { setApplicationWindowForeground: (...args) => calls.push(['foreground', ...args]) },
  }, {
    setInterval: callback => { timer = callback; return 1; },
    clearInterval: () => { timer = undefined; },
  });
  const ability = new exports.default();
  ability.context = {
    terminateSelf: async () => calls.push(['terminate']),
    eventHub: { emit: event => calls.push(['event', event]) },
  };
  ability.onCreate({ parameters: { playerWindowRequestId: id } });
  ability.onWindowStageCreate({
    getMainWindowSync: () => win,
    loadContent: (page, callback) => { calls.push(['page', page]); loadCallback = callback; },
  });
  return {
    calls, ability, listeners, closes: () => closeCount,
    tick: () => timer?.(),
    ready: async (error = { code: 0 }) => { await flush(); loadCallback(error); await flush(); },
    select: next => { id = next; ability.onNewWant({ parameters: { playerWindowRequestId: next } }); },
    requestFullscreen: value => { fullscreen = value; revision++; },
    nativeExit: () => { currentStatus = status.FLOATING; },
  };
}

test('player chrome reserves the actual title buttons and close stops its own session once', async () => {
  const h = abilityHost();
  await h.ready();
  assert.ok(h.calls.some(c => c[0] === 'page' && c[1] === 'pages/Player'));
  const metrics = h.calls.find(c => c[0] === 'metrics');
  assert.equal(metrics[5], 140);
  assert.equal(metrics[6], 40);
  assert.equal(await h.listeners.get('windowWillClose')(), false);
  h.ability.onWindowStageWillDestroy(); h.ability.onWindowStageDestroy(); h.ability.onDestroy();
  assert.equal(h.closes(), 1);
  assert.equal(h.listeners.size, 0);
});

test('a closing window cannot consume a newer session before destruction', async () => {
  const h = abilityHost();
  await h.ready();
  await h.listeners.get('windowWillClose')();
  h.select(2);
  h.ability.onWindowStageWillDestroy(); h.ability.onDestroy();
  assert.deepEqual(h.calls.filter(c => c[0] === 'close'), [['close', 1]]);
  assert.equal(h.closes(), 1);
});

test('initialization failure returns to the existing player without stopping it', async () => {
  const h = abilityHost();
  await h.ready({ code: 1 });
  assert.deepEqual(h.calls.filter(c => c[0] === 'failed'), [['failed', 1]]);
  assert.equal(h.closes(), 0);
  assert.ok(h.calls.some(c => c[0] === 'terminate'));
});

test('fullscreen restores floating or maximized state and respects a native exit', async () => {
  for (const previous of [2, 3]) {
    const h = abilityHost(previous);
    await h.ready();
    h.requestFullscreen(true); h.tick(); await flush();
    h.requestFullscreen(false); h.tick(); await flush();
    const transitions = h.calls.filter(c => ['maximize', 'recover'].includes(c[0]));
    assert.deepEqual(transitions, [['maximize', 2], previous === 2 ? ['maximize', 1] : ['recover']]);
    h.requestFullscreen(true); h.tick(); await flush();
    h.nativeExit(); h.tick(); await flush();
    assert.equal(h.calls.filter(c => c[0] === 'maximize').length, previous === 2 ? 3 : 2);
  }
});

test('moving focus between the two windows does not background the shared runtime', () => {
  const calls = [];
  let pending;
  const exports = load('harmonyApp/entry/src/main/ets/entryability/ApplicationVisibility.ets', {
    '@kit.BasicServicesKit': { deviceInfo: { deviceType: '2in1' } },
    'libentry.so': { default: { SetOhosApplicationForeground: value => calls.push(value) } },
  }, {
    setTimeout: callback => { pending = callback; return 1; },
    clearTimeout: () => { pending = undefined; },
  });
  const foreground = exports.setApplicationWindowForeground;
  foreground('main', true);
  foreground('main', false);
  foreground('player', true);
  pending?.();
  assert.ok(calls.every(Boolean));
  foreground('main', true);
  foreground('player', false);
  pending?.();
  assert.ok(calls.every(Boolean));
  foreground('main', false);
  pending();
  assert.equal(calls.at(-1), false);
});
