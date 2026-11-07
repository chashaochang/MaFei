const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const vm = require('node:vm');
const path = require('node:path');

function shell(nativeInterface, deviceInformation = undefined) {
  const window = { NativeInterface: nativeInterface, __MAFEI_DEVICE_INFORMATION__: deviceInformation };
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

test('web startup uses the request device information if the host proxy omits its getter', async () => {
  const device = { deviceId: 'request-device', deviceName: 'MaFei', appName: 'MaFei', appVersion: '2.0.0' };
  const host = shell({}, device);
  assert.deepEqual(JSON.parse(JSON.stringify(await host.AppHost.init())), device);
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

test('web client advertises only capabilities wired to the CMP host', async () => {
  const host = shell({
    getDeviceInformation: () => JSON.stringify({ deviceId: 'd', deviceName: 'D', appName: 'MaFei', appVersion: '2.0' }),
  });
  const supported = ['exit', 'externallinks', 'clientsettings', 'physicalvolumecontrol', 'multiserver'];
  for (const feature of supported) assert.equal(host.AppHost.supports(feature), true, feature);
  const unsupported = ['filedownload', 'remotecontrol', 'castmenuhashchange'];
  for (const feature of unsupported) assert.equal(host.AppHost.supports(feature), false, feature);
  assert.deepEqual(Array.from(host.getPlugins()), ['NavigationPlugin', 'ExoPlayerPlugin']);
});

test('Harmony rawfile NativeShell stays byte-identical to the shared web contract', () => {
  const shared = fs.readFileSync(path.join(__dirname, '../composeApp/src/commonMain/resources/native/nativeshell.js'));
  const harmony = fs.readFileSync(path.join(__dirname, '../harmonyApp/entry/src/main/resources/rawfile/native/nativeshell.js'));
  assert.deepEqual(harmony, shared);
});

test('unimplemented download and cast bridges fail gracefully instead of breaking the web client', async () => {
  const host = shell({
    getDeviceInformation: () => JSON.stringify({ deviceId: 'd', deviceName: 'D', appName: 'MaFei', appVersion: '2.0' }),
  });
  assert.doesNotThrow(() => host.downloadFile({ url: 'https://nas.test/file' }));
  assert.doesNotThrow(() => host.downloadFiles([]));
  let error;
  await host.execCast('getDevices', {}, err => { error = err; });
  assert.match(error.message, /not supported/i);
});
