const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require('/Applications/DevEco-Studio.app/Contents/tools/ohpm/node_modules/typescript');

function harness(create) {
  const players = [], events = [], fallbacks = [];
  function make() {
    const p = { state: 'idle', duration: 900000, width: 1920, height: 1080, listeners: {}, seeks: [], rates: [], released: false,
      on(name, callback) { this.listeners[name] = callback; },
      emit(state) { this.state = state; this.listeners.stateChange?.(state); },
      async setMediaSource(source) { this.source = source; this.emit('initialized'); },
      async prepare() { this.prepares = (this.prepares || 0) + 1; },
      async play() { this.emit('playing'); }, async pause() { this.emit('paused'); },
      seek(ms) { this.seeks.push(ms); }, setPlaybackRate(rate) { this.rates.push(rate); },
      async release() { this.released = true; },
      async getTrackDescription() { return [ { track_type: 1, track_index: 0 },
        { track_type: 0, track_index: 1, language: '中文', codec_mime: 'aac' },
        { track_type: 2, track_index: 2, language: '英文', codec_mime: 'srt' } ]; },
      async getSelectedTracks() { return [1]; }, async selectTrack() {}, async deselectTrack() {},
    }; players.push(p); return p;
  }
  const module = { exports: {} };
  const source = fs.readFileSync('harmonyApp/entry/src/main/ets/player/SystemAvPlayerEngine.ets', 'utf8');
  const js = ts.transpileModule(source, { compilerOptions: { module: ts.ModuleKind.CommonJS,
    target: ts.ScriptTarget.ES2020 } }).outputText;
  vm.runInNewContext(js, { module, exports: module.exports, Date, Promise, require(name) {
    if (name === '@kit.MediaKit') return { media: { createAVPlayer: () => create ? create(make) : Promise.resolve(make()),
      createMediaSourceWithUrl: (url, headers) => ({ url, headers }), SeekMode: { SEEK_CLOSEST: 2 },
      VideoScaleType: { VIDEO_SCALE_TYPE_FIT_CROP: 1, VIDEO_SCALE_TYPE_SCALED_ASPECT: 2 },
      BufferingInfoType: { BUFFERING_START: 1, BUFFERING_END: 2, CACHED_DURATION: 3 } } };
    throw Error(name);
  } });
  const engine = new module.exports.SystemAvPlayerEngine(event => events.push(event),
    (...values) => fallbacks.push(values));
  const resource = { handle: 1, url: 'https://test/video', headers: ['Authorization: secret', 'Cookie: a:b'],
    subtitleUrl: '', positionMs: 120000, speed: 1 };
  return { engine, resource, players, events, fallbacks, drain: () => new Promise(resolve => setImmediate(resolve)) };
}

test('authenticated source and surface are real prerequisites; no fabricated readiness', async () => {
  const h = harness(); h.engine.load(h.resource); await h.drain(); const p = h.players[0];
  assert.equal(p.prepares, undefined); assert.equal(h.events.length, 0);
  assert.equal(p.source.headers.Authorization, 'secret'); assert.equal(p.source.headers.Cookie, 'a:b');
  h.engine.surface('surface'); assert.equal(p.surfaceId, 'surface'); assert.equal(p.prepares, 1);
  h.engine.surface('surface'); assert.equal(p.prepares, 1);
  p.emit('prepared'); await h.drain();
  assert.equal(h.events[0].event, 'ready'); assert.equal(h.events[0].durationMs, 900000);
  assert.equal(h.events[0].positionMs, 120000); assert.equal(p.state, 'prepared');
});

test('playback follows shared play/seek/speed commands and tracks are engine-owned', async () => {
  const h = harness(); h.engine.surface('surface'); h.engine.load(h.resource); await h.drain();
  const p = h.players[0]; p.emit('prepared'); await h.drain();
  h.engine.play(120000); h.engine.speed(1.5); h.engine.pause();
  assert.deepEqual(p.seeks, [120000]); assert.deepEqual(p.rates, [1.5]);
  const tracks = h.events.find(event => event.event === 'tracks').tracks;
  assert.equal(tracks.length, 2); assert.equal(tracks[0].label, '中文 · aac'); assert.equal(tracks[0].selected, true);
});

test('mid-play failure falls back exactly once with current progress, rate and intent', async () => {
  const h = harness(); h.engine.surface('s'); h.engine.load(h.resource); await h.drain(); const p = h.players[0];
  p.emit('prepared'); h.engine.play(120000); h.engine.speed(2); p.listeners.timeUpdate(135000);
  p.listeners.error(Error('failed')); p.listeners.error(Error('late')); p.emit('playing');
  assert.equal(h.fallbacks.length, 1); assert.deepEqual(h.fallbacks[0], [135000, 2, true]); assert.equal(p.released, true);
});

test('release immediately invalidates late native callbacks', async () => {
  const h = harness(); h.engine.load(h.resource); await h.drain(); const p = h.players[0];
  h.engine.release(); p.emit('prepared'); p.listeners.timeUpdate(1); p.listeners.error(Error('late'));
  assert.equal(h.events.length, 0); assert.equal(h.fallbacks.length, 0); assert.equal(p.released, true);
});

test('a delayed create cannot install a retired player', async () => {
  let finish;
  const h = harness(make => new Promise(resolve => { finish = () => resolve(make()); }));
  h.engine.load(h.resource); h.engine.release(); finish(); await h.drain();
  assert.equal(h.players[0].released, true); assert.equal(h.players[0].source, undefined);
});

test('failed creation falls back from the requested resume point', async () => {
  const h = harness(() => Promise.reject(Error('not available')));
  h.engine.load(h.resource); await h.drain();
  assert.equal(h.fallbacks.length, 1); assert.deepEqual(h.fallbacks[0], [120000, 1, false]);
});

test('PiP dimensions come from the prepared native player and reset on release', async () => {
  const h = harness(); h.engine.surface('s'); h.engine.load(h.resource); await h.drain();
  assert.equal(h.engine.videoWidthPx, 0); assert.equal(h.engine.videoHeightPx, 0);
  h.players[0].emit('prepared'); await h.drain();
  assert.equal(h.engine.videoWidthPx, 1920); assert.equal(h.engine.videoHeightPx, 1080);
  h.engine.release();
  assert.equal(h.engine.videoWidthPx, 0); assert.equal(h.engine.videoHeightPx, 0);
});
