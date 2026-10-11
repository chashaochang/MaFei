const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');
const ts = require('/Applications/DevEco-Studio.app/Contents/tools/ohpm/node_modules/typescript');

const flush = () => new Promise(resolve => setImmediate(resolve));
function harness({ captureMs = 0, readMs = 0, releaseMs = 0 } = {}) {
  const frames = [], events = [], timers = new Map();
  let now = 0;
  let timerId = 0, releaseCapture, releaseRead, rejectCapture = false;
  const module = { exports: {} };
  const source = fs.readFileSync(path.join(__dirname, '../harmonyApp/entry/src/main/ets/player/PlayerVideoBackdropCapture.ets'), 'utf8');
  vm.runInNewContext(ts.transpileModule(source, { compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020,
  } }).outputText, {
    module, exports: module.exports, ArrayBuffer, Date: { now: () => now },
    setTimeout(fn, delay) { timers.set(++timerId, { fn, delay }); return timerId; },
    clearTimeout(id) { timers.delete(id); },
    require(name) {
      assert.equal(name, '@kit.ImageKit');
      return { image: { async createPixelMapFromSurface(surface) {
        now += captureMs;
        if (rejectCapture) throw Error('first frame not ready');
        if (releaseCapture) await new Promise(resolve => { releaseCapture.resolve = resolve; });
        const frame = { width: 3840, height: 2160,
          async getImageInfo() { return { size: { width: this.width, height: this.height }, alphaType: 1 }; },
          async scale(x, y) { this.width = Math.round(this.width * x); this.height = Math.round(this.height * y); },
          async readPixels(area) {
            now += readMs;
            assert.equal(area.stride, this.width * 4);
            assert.equal(area.pixels.byteLength, this.width * this.height * 4);
            if (releaseRead) await new Promise(resolve => { releaseRead.resolve = resolve; });
          },
          async release() { now += releaseMs; this.released = true; }, surface,
        };
        frames.push(frame); return frame;
      } } };
    },
  });
  const capture = new module.exports.PlayerVideoBackdropCapture((...args) => { events.push(args); return true; });
  return { capture, frames, events, timers,
    deferCapture() { releaseCapture = {}; return releaseCapture; },
    deferRead() { releaseRead = {}; return releaseRead; },
    failCapture() { rejectCapture = true; },
  };
}

test('publishes bounded live BGRA frames and releases PixelMaps; no disk or encoded image handoff', async () => {
  const h = harness(); h.capture.bind('surface', 1, 2, true); await flush();
  const [handle, revision, width, height, alpha, pixels] = h.events[0];
  assert.deepEqual([handle, revision, width, height, alpha], [1, 2, 256, 144, 1]);
  assert.equal(pixels.byteLength, 256 * 144 * 4); assert.equal(h.frames[0].released, true);
  assert.equal(h.timers.size, 1); h.capture.stop(); assert.equal(h.timers.size, 0);
});
test('source change retires an in-flight frame and never overlaps capture requests', async () => {
  const h = harness(), gate = h.deferCapture();
  h.capture.bind('old', 1, 1, true); h.capture.bind('new', 1, 2, true);
  assert.equal(h.frames.length, 0); gate.resolve(); await flush();
  assert.equal(h.events.length, 0); assert.equal(h.frames[0].released, true);
  assert.equal(h.timers.size, 1); assert.equal([...h.timers.values()][0].delay, 0);
  h.capture.stop();
});
test('hiding controls discards a frame awaiting pixel read and cancels future work', async () => {
  const h = harness(), gate = h.deferRead();
  h.capture.bind('surface', 1, 1, true); await flush();
  h.capture.bind('surface', 1, 1, false); gate.resolve(); await flush();
  assert.equal(h.events.length, 0); assert.equal(h.frames[0].released, true); assert.equal(h.timers.size, 0);
});
test('capture failure backs off without rejecting playback or holding an in-flight lease', async () => {
  const h = harness(); h.failCapture(); h.capture.bind('surface', 1, 1, true); await flush();
  assert.equal(h.events.length, 0); assert.equal(h.timers.size, 1);
  assert.equal([...h.timers.values()][0].delay, 700); h.capture.stop();
});
test('unchanged parameter updates do not restart an active sampler', async () => {
  const h = harness(); h.capture.bind('surface', 1, 1, true); await flush();
  h.capture.bind('surface', 1, 1, true); await flush();
  assert.equal(h.frames.length, 1); assert.equal(h.timers.size, 1); h.capture.stop();
});
test('fast live samples are scheduled at 30 Hz instead of waiting 180 ms after completion', async () => {
  const h = harness(); h.capture.bind('surface', 1, 1, true); await flush();
  const timer = [...h.timers.values()][0];
  assert.ok(Math.abs(timer.delay - 1000 / 30) < .001, `unexpected frame delay: ${timer.delay}`);
  h.capture.stop();
});
test('the frame budget includes capture, pixel read and release time', async () => {
  const h = harness({ captureMs: 12, readMs: 5, releaseMs: 3 });
  h.capture.bind('surface', 1, 1, true); await flush();
  const timer = [...h.timers.values()][0];
  assert.ok(Math.abs(timer.delay - (1000 / 30 - 20)) < .001, `unexpected remaining budget: ${timer.delay}`);
  assert.equal(h.events.length, 1); assert.equal(h.frames[0].released, true);
  h.capture.stop();
});
test('slow capture adds no artificial delay and never queues overlapping frames', async () => {
  const h = harness({ captureMs: 80 }), gate = h.deferRead();
  h.capture.bind('surface', 1, 1, true); await flush();
  assert.equal(h.timers.size, 0); assert.equal(h.frames.length, 1);
  h.capture.bind('surface', 1, 1, true);
  assert.equal(h.frames.length, 1);
  gate.resolve(); await flush();
  assert.equal(h.timers.size, 1); assert.equal([...h.timers.values()][0].delay, 0);
  h.capture.stop();
});
