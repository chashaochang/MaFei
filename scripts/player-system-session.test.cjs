const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const ts = require(process.env.MAFEI_TYPESCRIPT || '/Applications/DevEco-Studio.app/Contents/tools/ohpm/node_modules/typescript');

function harness(createOverride) {
  const sessions = [], actions = [], errors = [], images = [], castImages = [];
  let decodeOverride;
  let rejectCasting = false;
  function session() {
    const s = { listeners: {}, metadata: [], states: [], destroyed: false, active: false,
      on(name, fn) { this.listeners[name] = fn; }, off(name) { delete this.listeners[name]; },
      async setAVMetadata(data) { this.metadata.push(data); },
      async setAVPlaybackState(data) { this.states.push(data); },
      async activate() { this.active = true; }, async destroy() { this.destroyed = true; this.active = false; },
    }; sessions.push(s); return s;
  }
  let now = 10000;
  const module = { exports: {} };
  const source = fs.readFileSync(path.join(__dirname, '../harmonyApp/entry/src/main/ets/player/PlayerSystemSession.ets'), 'utf8');
  const js = ts.transpileModule(source, { compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020,
  } }).outputText;
  vm.runInNewContext(js, { module, exports: module.exports, Date: { now: () => now },
    require(name) {
      if (name === './PlayerCasting') return { PlayerCasting: class {
        bind() { if (rejectCasting) throw Error('unsupported casting event'); }
        update() {} updateArtwork(value) { castImages.push(value); } async close() {}
      } };
      if (name === '@kit.ArkTS') return { util: { Base64Helper: class { decodeSync(value) { return Uint8Array.from(Buffer.from(value, 'base64')); } } } };
      if (name === '@kit.ImageKit') return { image: { createImageSource(bytes) {
        return { async getImageInfo() { return { size: { width: 1200, height: 1800 } }; },
          async createPixelMap(options) {
            if (decodeOverride) return decodeOverride(options);
            const pixel = { options, bytes, released: false, async release() { this.released = true; } };
            images.push(pixel); return pixel;
          }, async release() {} };
      } } };
      if (name === '@kit.AVSessionKit') return { avSession: {
        createAVSession: async (context, name, type) => {
          const created = createOverride ? await createOverride(session) : session();
          created.type = type; return created;
        },
        PlaybackState: { PLAYBACK_STATE_PLAY: 1, PLAYBACK_STATE_PAUSE: 2, PLAYBACK_STATE_STOP: 3, PLAYBACK_STATE_PREPARE: 0 },
      } };
      if (name === '@kit.PerformanceAnalysisKit') return { hilog: { warn: (...args) => errors.push(args) } };
      throw Error(name);
    },
  });
  const host = new module.exports.PlayerSystemSession();
  const metadata = { id: 'video-1', title: '测试影片', canNext: true, canPrevious: false };
  const snapshot = { state: 'playing', positionMs: 45123, durationMs: 912345, bufferedMs: 60000, speed: 1.5 };
  return { host, metadata, snapshot, sessions, actions, errors, images, castImages,
    setDecode: callback => { decodeOverride = callback; },
    rejectCasting: () => rejectCasting = true,
    start: (data = metadata) => host.start({}, data, (action, value) => actions.push([action, value])),
    advance: () => now += 1000,
    drain: async () => { await host.operations; },
  };
}

test('unavailable casting observer does not destroy normal system playback controls', async () => {
  const h = harness(); h.rejectCasting(); h.start(); await h.drain();
  const session = h.sessions[0];
  assert.equal(session.active, true); assert.equal(session.destroyed, false);
  session.listeners.pause(); assert.deepEqual(h.actions, [['pause', 0]]);
  h.host.close(); await h.drain(); assert.equal(session.destroyed, true);
});

test('late authenticated artwork reaches AVSession and survives duration/progress updates', async () => {
  const h = harness(); h.start(); await h.drain();
  const metadata = { ...h.metadata, artworkData: Buffer.from('encoded artwork').toString('base64') };
  h.host.updateMetadata(metadata); await h.drain();
  const s = h.sessions[0], image = h.images[0];
  assert.equal(s.metadata.at(-1).mediaImage, image);
  assert.equal(h.castImages.at(-1), image);
  assert.equal(image.options.desiredSize.height, 480);
  assert.equal(image.options.desiredSize.width, 320);
  h.host.update(metadata, h.snapshot); await h.drain();
  assert.equal(s.metadata.at(-1).mediaImage, image);
  assert.equal(s.metadata.at(-1).duration, 912345);
  h.host.updateMetadata(metadata); await h.drain();
  assert.equal(h.images.length, 1, 'Do not decode artwork on every progress/command');
  h.host.close(); await h.drain(); assert.equal(image.released, true);
});

test('artwork decode finishing after a media switch cannot overwrite the next session', async () => {
  const h = harness(); h.start(); await h.drain();
  let finish; const oldImage = { async release() { this.released = true; } };
  h.setDecode(() => new Promise(resolve => { finish = () => resolve(oldImage); }));
  h.host.updateMetadata({ ...h.metadata, artworkData: 'YQ==' });
  await new Promise(resolve => setImmediate(resolve));
  h.start({ ...h.metadata, id: 'video-2', title: '下一集' }); finish(); await h.drain();
  assert.equal(oldImage.released, true);
  assert.equal(h.sessions[1].metadata.at(-1).mediaImage, undefined);
  const count = h.sessions[1].metadata.length;
  h.host.updateMetadata({ ...h.metadata, artworkData: 'YQ==' }); await h.drain();
  assert.equal(h.sessions[1].metadata.length, count, 'Reject a retired media ID');
});

test('a queued duration update captured before artwork arrived cannot clear the image', async () => {
  const h = harness(); h.start(); await h.drain();
  h.host.updateMetadata({ ...h.metadata, artworkData: 'YQ==' });
  h.host.update(h.metadata, h.snapshot);
  await h.drain();
  const image = h.images[0];
  assert.ok(image);
  assert.equal(h.sessions[0].metadata.at(-1).mediaImage, image);
  assert.equal(h.sessions[0].metadata.at(-1).duration, h.snapshot.durationMs);
  assert.equal(image.released, false);
});

test('invalid artwork does not disable playback controls or lose duration', async () => {
  const h = harness(); h.start(); await h.drain();
  h.setDecode(async () => { throw Error('invalid image'); });
  const metadata = { ...h.metadata, artworkData: 'YQ==' };
  h.host.updateMetadata(metadata); await h.drain();
  h.host.update(metadata, h.snapshot); await h.drain();
  assert.equal(h.sessions[0].active, true);
  assert.equal(h.sessions[0].metadata.at(-1).duration, h.snapshot.durationMs);
  h.sessions[0].listeners.pause(); assert.deepEqual(h.actions, [['pause', 0]]);
});

test('slow OS updates coalesce to the latest progress instead of accumulating a stale queue', async () => {
  const h = harness(); h.start(); await h.drain();
  const session = h.sessions[0];
  let finish;
  session.setAVPlaybackState = async data => {
    session.states.push(data);
    if (session.states.length === 1) await new Promise(resolve => { finish = resolve; });
  };
  h.host.update(h.metadata, h.snapshot);
  await new Promise(resolve => setImmediate(resolve));
  for (let i = 1; i <= 100; i++) {
    h.advance(); h.host.update(h.metadata, { ...h.snapshot, positionMs: i * 1000 });
  }
  finish(); await h.drain();
  assert.equal(session.states.length, 2);
  assert.equal(session.states.at(-1).position.elapsedTime, 100000);
});

test('publishes true millisecond duration, progress and speed', async () => {
  const h = harness(); h.start(); h.host.update(h.metadata, h.snapshot); await h.drain();
  const s = h.sessions[0];
  assert.equal(s.active, true); assert.equal(s.metadata.at(-1).duration, 912345);
  assert.equal(s.states[0].position.elapsedTime, 45123); assert.equal(s.states[0].speed, 1.5);
  assert.equal(s.metadata[0].title, '测试影片');
  assert.equal(s.listeners.playPrevious, undefined);
  s.listeners.playNext(); s.listeners.seek(123456); s.listeners.pause();
  assert.deepEqual(h.actions, [['next', 0], ['seek', 123456], ['pause', 0]]);
});

test('music creates an audio session while video retains a video session', async () => {
  const h = harness(); h.start({ ...h.metadata, audioOnly: true }); await h.drain();
  assert.equal(h.sessions[0].type, 'audio');
  h.start(); await h.drain();
  assert.equal(h.sessions[0].destroyed, true); assert.equal(h.sessions[1].type, 'video');
});

test('only the shared callback receives controls; invalid seek/speed is ignored', async () => {
  const h = harness(); h.start(); await h.drain(); const s = h.sessions[0];
  s.listeners.seek(-1); s.listeners.seek(NaN); s.listeners.setSpeed(Infinity); s.listeners.setSpeed(0);
  assert.equal(h.actions.length, 0); s.listeners.play(); s.listeners.setSpeed(2);
  assert.deepEqual(h.actions, [['play', 0], ['speed', 2]]);
});

test('close immediately invalidates queued old callbacks and destroys the session', async () => {
  const h = harness(); h.start(); await h.drain(); const s = h.sessions[0], latePlay = s.listeners.play;
  h.host.close(); latePlay(); await h.drain();
  assert.equal(h.actions.length, 0); assert.equal(s.destroyed, true); assert.equal(Object.keys(s.listeners).length, 0);
});

test('close during async creation cannot activate or leak a session', async () => {
  let finish;
  const h = harness(make => new Promise(resolve => { finish = () => resolve(make()); }));
  h.start(); await new Promise(resolve => setImmediate(resolve)); h.host.close(); finish(); await h.drain();
  assert.equal(h.sessions[0].destroyed, true); assert.equal(h.sessions[0].active, false);
});

test('replacement destroys old session and rejects stale media events', async () => {
  const h = harness(); h.start(); await h.drain(); const s = h.sessions[0], lateSeek = s.listeners.seek;
  h.start({ ...h.metadata, id: 'video-2', title: '另一集' }); lateSeek(50000);
  await h.drain();
  assert.equal(s.destroyed, true); assert.equal(h.sessions[1].metadata[0].assetId, 'video-2');
  assert.equal(h.actions.length, 0);
});

test('progress is throttled but pause is published immediately', async () => {
  const h = harness(); h.start(); h.host.update(h.metadata, h.snapshot); await h.drain();
  h.host.update(h.metadata, { ...h.snapshot, positionMs: 45200 });
  h.host.update(h.metadata, { ...h.snapshot, state: 'paused' }); await h.drain();
  assert.equal(h.sessions[0].states.length, 2); assert.equal(h.sessions[0].states[1].state, 2);
  h.advance(); h.host.update(h.metadata, { ...h.snapshot, state: 'paused' }); await h.drain();
  assert.equal(h.sessions[0].states.length, 3);
});

test('a platform failure does not poison later lifecycle work', async () => {
  let attempts = 0;
  const h = harness(async make => { if (++attempts === 1) throw Error('service unavailable'); return make(); });
  h.start(); await h.drain(); assert.equal(h.errors.length, 1);
  h.start(); await h.drain(); assert.equal(h.sessions[0].active, true);
  h.host.close(); await h.drain(); assert.equal(h.sessions[0].destroyed, true);
});

test('metadata completion from the old video cannot suppress the new video duration', async () => {
  const h = harness(); h.start(); await h.drain();
  const old = h.sessions[0];
  let finish;
  old.setAVMetadata = () => new Promise(resolve => { finish = resolve; });
  h.host.update(h.metadata, h.snapshot);
  await new Promise(resolve => setImmediate(resolve));
  const next = { ...h.metadata, id: 'video-2' };
  h.start(next);
  h.host.update(next, h.snapshot);
  finish(); await h.drain();
  assert.equal(h.sessions[1].metadata.at(-1).duration, h.snapshot.durationMs);
  assert.equal(old.states.length, 0);
});

test('failure during setup destroys the incomplete session without waiting for another load', async () => {
  const h = harness(async make => {
    const s = make();
    s.setAVMetadata = async () => { throw Error('metadata rejected'); };
    return s;
  });
  h.start(); await h.drain();
  const s = h.sessions[0];
  assert.equal(s.destroyed, true);
  assert.equal(Object.keys(s.listeners).length, 0);
  assert.equal(s.active, false);
});
