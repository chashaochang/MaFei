const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

function shell(nativeInterface) {
  const window = { NativeInterface: nativeInterface };
  const source = fs.readFileSync(path.join(__dirname, '../composeApp/src/commonMain/resources/native/nativeshell.js'), 'utf8');
  vm.runInNewContext(source, { window });
  return window.NativeShell;
}

test('legacy optional media hooks do not abort web startup on CMP hosts', async () => {
  const native = { getDeviceInformation: () => JSON.stringify({ deviceId: 'test-device', deviceName: 'Test', appName: 'MaFei', appVersion: '2.0' }) };
  const host = shell(native);
  assert.doesNotThrow(() => host.hideMediaSession());
  assert.doesNotThrow(() => host.updateMediaSession({ title: 'Web media' }));
  assert.doesNotThrow(() => host.updateVolumeLevel(30));
  assert.equal((await host.AppHost.init()).deviceId, 'test-device');
});

test('optional hooks still forward to a native host that actually provides them', () => {
  const received = [];
  const host = shell({
    hideMediaSession: () => received.push('hide'),
    updateMediaSession: value => received.push(JSON.parse(value).title),
    updateVolumeLevel: value => received.push(value),
  });
  host.hideMediaSession(); host.updateMediaSession({ title: 'Title' }); host.updateVolumeLevel(50);
  assert.deepEqual(received, ['hide', 'Title', 50]);
});
