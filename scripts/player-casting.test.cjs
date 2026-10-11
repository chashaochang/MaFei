const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const ts = require('/Applications/DevEco-Studio.app/Contents/tools/ohpm/node_modules/typescript');

function harness(uri = 'http://nas.test/jellyfin/Videos/file?api_key=fixture') {
  const events = [], actions = [], calls = [], errors = [];
  const controller = { listeners: {},
    on(name, ...args) { this.listeners[name] = args.at(-1); }, off(name) { delete this.listeners[name]; },
    async prepare(item) { calls.push(['prepare', item]); }, async start(item) { calls.push(['start', item]); },
    async getAVPlaybackState() { return { state: 1, position: { elapsedTime: 15000 } }; },
    async sendControlCommand(command) { calls.push(['command', command]); },
  };
  const session = { listeners: {}, on(name, fn) { this.listeners[name] = fn; }, off(name) { delete this.listeners[name]; },
    async getAVCastController() { return controller; }, async stopCasting() { calls.push(['stop']); } };
  const module = { exports: {} };
  const source = fs.readFileSync(path.join(__dirname, '../harmonyApp/entry/src/main/ets/player/PlayerCasting.ets'), 'utf8');
  vm.runInNewContext(ts.transpileModule(source, { compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020,
  } }).outputText, { module, exports: module.exports, require(name) {
    if (name === '@kit.AVSessionKit') return { avSession: {
      AVCastCategory: { CATEGORY_REMOTE: 1, CATEGORY_LOCAL: 0 }, ConnectionState: { STATE_CONNECTED: 1 },
      PlaybackState: { PLAYBACK_STATE_PLAY: 1, PLAYBACK_STATE_PAUSE: 2 },
      AVCastPickerHelper: class { async select() { calls.push(['picker']); } },
    } };
    if (name === '@kit.ArkUI') return { promptAction: { showToast(value) { errors.push(value); } } };
    if (name === '@kit.ArkTS') return { url: { URL: { parseURL: value => new URL(value) } } };
    throw Error(name);
  } });
  const host = new module.exports.PlayerCasting();
  const sourceData = uri ? { id: 'video', title: '影片', mediaUri: uri, audioOnly: false, positionMs: 0, durationMs: 60000 } : undefined;
  host.bind(session, sourceData, event => events.push(event), action => actions.push(action));
  return { host, sourceData, session, controller, events, actions, calls, errors,
    connect: async () => { session.listeners.outputDeviceChange(1, { devices: [{ castCategory: 1 }] }); await new Promise(r => setImmediate(r)); },
  };
}

test('uses the validated remote URL and current position, then routes commands to the receiver', async () => {
  const h = harness(); h.host.update(12000, 60000); await h.host.choose({}); await h.connect();
  assert.equal(h.host.active, true); const item = h.calls.find(c => c[0] === 'prepare')[1];
  assert.equal(item.description.startPosition, 12000); assert.equal(item.description.duration, 60000);
  assert.equal(item.description.mediaUri, h.sourceData.mediaUri);
  assert.deepEqual(h.events.map(e => e.event), ['casting', 'playing']);
  await h.host.command('seek', 22000); await h.host.command('pause', 0);
  assert.equal(h.calls.at(-2)[1].command, 'seek'); assert.equal(h.calls.at(-2)[1].parameter, 22000);
  assert.equal(h.calls.at(-1)[1].command, 'pause');
  h.controller.listeners.playNext(); assert.deepEqual(h.actions, ['next']);
});

test('receiver media description includes the same image as AVSession metadata', async () => {
  const h = harness(); const artwork = { pixelMap: true };
  h.host.updateArtwork(artwork); await h.connect();
  assert.equal(h.calls.find(c => c[0] === 'prepare')[1].description.mediaImage, artwork);
  assert.equal(h.calls.find(c => c[0] === 'start')[1].description.mediaImage, artwork);
});

test('local proxy URLs and header-only resources are not offered to remote devices', async () => {
  for (const uri of [undefined, 'http://localhost/source/1', 'http://127.0.0.1/source/1', 'http://[::1]/source/1', 'file:///movie']) {
    const h = harness(uri === undefined ? null : uri); await h.host.choose({}); await h.connect();
    assert.equal(h.host.active, false); assert.equal(h.calls.some(c => c[0] === 'picker' || c[0] === 'start'), false);
    assert.equal(h.errors.length, 1);
  }
});

test('disconnect resumes from receiver progress and late callbacks cannot touch a new source', async () => {
  const h = harness(); await h.connect();
  const late = h.controller.listeners.playbackStateChange;
  late({ state: 2, position: { elapsedTime: 33000 } });
  await h.host.choose({}); assert.equal(h.events.at(-1).active, false); assert.equal(h.events.at(-1).positionMs, 33000);
  await h.host.close(); const count = h.events.length;
  late({ state: 1, position: { elapsedTime: 90000 } });
  assert.equal(h.events.length, count); assert.equal(Object.keys(h.controller.listeners).length, 0);
});

test('close while receiver prepares never starts the retired stream', async () => {
  const h = harness(); let finish;
  h.controller.prepare = () => new Promise(resolve => { finish = resolve; });
  const pending = h.connect(); await pending; await h.host.close(); finish();
  await new Promise(r => setImmediate(r));
  assert.equal(h.calls.some(c => c[0] === 'start'), false); assert.equal(h.host.active, false);
});

test('retired receiver commands cannot skip items or end a reconnected stream', async () => {
  const h = harness(); await h.connect();
  const next = h.controller.listeners.playNext, previous = h.controller.listeners.playPrevious;
  const ended = h.controller.listeners.endOfStream;
  await h.host.choose({}); await h.connect();
  const count = h.events.length;
  next(); previous(); ended();
  assert.deepEqual(h.actions, []); assert.equal(h.events.length, count);
  h.controller.listeners.playNext(); assert.deepEqual(h.actions, ['next']);
});
