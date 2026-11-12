const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const ts = require(process.env.MAFEI_TYPESCRIPT || 'typescript');

function surfaceHost() {
  const calls = [];
  const file = fs.readFileSync('harmonyApp/entry/src/main/ets/player/PlayerInterop.ets', 'utf8');
  // Run the real lifecycle/command dispatcher without ArkUI's declarative
  // build DSL or a native decoder. Loading itself is observed at its boundary.
  const source = file.slice(0, file.indexOf('// CustomBuilderAdapter:')) +
    file.slice(file.indexOf('struct MaFeiPlayerSurfaceHost {'), file.indexOf('\n  build() {'))
      .replace('struct MaFeiPlayerSurfaceHost', 'export class MaFeiPlayerSurfaceHost')
      .replace(/@Consume\([^)]*\)\s*@Watch\([^)]*\)\s*/g, '')
      .replace(/@State\s*/g, '') + '\n}';
  class Stub {}
  class Controller {
    setCacheDir() {}
    addListener() {}
    play() { calls.push(['play']); }
    pause() { calls.push(['pause']); }
    stop() { calls.push(['stop']); }
    release() { calls.push(['release']); }
    seekTo(position) { calls.push(['seek', position]); }
  }
  const module = { exports: {} };
  vm.runInNewContext(ts.transpileModule(source, { compilerOptions: {
    module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2020,
  } }).outputText, {
    module, exports: module.exports, XComponentController: Stub,
    getContext: () => ({ cacheDir: '/test/cache', eventHub: { on() {} } }),
    require(name) {
      if (name === 'mfplayer') return {
        PlayerController: Controller, PlayerState: { IDLE: 'idle' },
        PlayerContentMode: { Fit: 'fit', Fill: 'fill' },
      };
      if (name === '@kit.PerformanceAnalysisKit') return { hilog: { info() {} } };
      if (name === './PlayerSystemSession') return {
        PlayerSystemSession: class { casting = { active: false }; close() {} },
      };
      if (name === './PlayerPictureInPicture') return { PlayerPictureInPicture: class { close() {} } };
      if (name === './PlayerBackgroundAudio') return { PlayerBackgroundAudio: class { close() {} } };
      if (name === './VideoFramePreviewHelper') return { VideoFramePreviewHelper: Stub };
      if (name === './SystemAvPlayerEngine') return { SystemAvPlayerEngine: Stub };
      if (name === './PlayerVideoBackdropCapture') return { PlayerVideoBackdropCapture: Stub };
      return {};
    },
  });
  const host = new module.exports.MaFeiPlayerSurfaceHost();
  host.load = command => {
    host.lastLoad = command;
    calls.push(['load', command.loadId, command.handle, command.url, command.positionMs]);
  };
  host.emitDeviceState = async () => {};
  host.emitProgress = () => {};
  host.syncScreenAwake = host.syncSystemArtwork = host.syncGlassBackdrop = () => {};
  let args = {
    commandId: 43, loadId: 41, handle: 1, action: 'deviceState', positionMs: 12000,
    contentMode: 'Fit', url: 'https://media.example.test/video.mp4',
  };
  return {
    calls,
    mount: overrides => { args = { ...args, ...overrides }; host.args = args; host.aboutToAppear(); },
    update: overrides => { args = { ...args, ...overrides }; host.args = args; host.onArgsChanged(); },
  };
}

test('a late surface loads the active video after auxiliary commands replace load', () => {
  const h = surfaceHost();
  h.mount();
  h.update({ commandId: 44, action: 'deviceState' });
  h.update({ commandId: 45, action: 'play', positionMs: 0 });
  assert.deepEqual(h.calls, [
    ['load', 41, 1, 'https://media.example.test/video.mp4', 12000], ['play'],
  ]);
});

test('an observed load is applied once, and a later load of the same URL starts again', () => {
  const h = surfaceHost();
  h.mount({ commandId: 41, action: 'load' });
  h.update({ commandId: 41, action: 'load' });
  h.update({ commandId: 42, action: 'deviceState' });
  h.update({ commandId: 47, loadId: 46, action: 'deviceState', positionMs: 0 });
  assert.deepEqual(h.calls, [
    ['load', 41, 1, 'https://media.example.test/video.mp4', 12000],
    ['load', 46, 1, 'https://media.example.test/video.mp4', 0],
  ]);
});

test('a stopped or released source cannot be resurrected by a late surface', () => {
  for (const action of ['stop', 'release']) {
    const h = surfaceHost();
    h.mount({ loadId: 0, action });
    h.update({ commandId: 44, action: 'deviceState' });
    assert.deepEqual(h.calls, [[action]]);
  }
});

test('a load already consumed by a mounted surface is not repeated by later commands', () => {
  const h = surfaceHost();
  h.mount({ commandId: 41, action: 'load' });
  h.update({ commandId: 42, loadId: 0, action: 'stop' });
  h.update({ commandId: 43, action: 'deviceState' });
  h.update({ commandId: 45, loadId: 44, handle: 2, action: 'deviceState' });
  assert.deepEqual(h.calls, [
    ['load', 41, 1, 'https://media.example.test/video.mp4', 12000], ['stop'],
    ['load', 44, 2, 'https://media.example.test/video.mp4', 12000],
  ]);
});
