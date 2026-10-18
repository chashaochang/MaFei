const { test } = require('node:test');
const assert = require('node:assert/strict');
const fs = require('node:fs');
const os = require('node:os');
const path = require('node:path');
const { spawnSync } = require('node:child_process');
const { resolveBundleName, verifyManifest } = require('../runscript/resolveOhosBundle.cjs');

const root = path.resolve(__dirname, '..');
const base = 'cn.xiaobai.mafei';

test('debug resolves to a separate app even when the IDE passes the official name', () => {
  assert.equal(resolveBundleName(base, 'debug'), `${base}.dev`);
  assert.equal(resolveBundleName(base, 'debug', base), `${base}.dev`);
  assert.equal(resolveBundleName(base, 'debug', `${base}.dev`), `${base}.dev`);
  assert.equal(resolveBundleName(base, 'release'), base);
  assert.equal(resolveBundleName('com.external.host', 'debug'), 'com.external.host.dev');
});

test('unknown identities and build modes fail closed', () => {
  assert.throws(() => resolveBundleName(base, 'debug', 'com.example.harmonyapp'), /does not match/);
  assert.throws(() => resolveBundleName(base, 'release', `${base}.dev`), /does not match/);
  assert.throws(() => resolveBundleName(base, 'Debug'), /Unsupported build mode/);
  assert.throws(() => resolveBundleName(`${base}.dev`, 'debug'), /without .dev/);
});

test('installation requires both the expected identity and debug flag in the artifact', () => {
  assert.doesNotThrow(() => verifyManifest({ app: { bundleName: `${base}.dev`, debug: true } }, 'debug', `${base}.dev`));
  assert.doesNotThrow(() => verifyManifest({ app: { bundleName: base, debug: false } }, 'release', base));
  assert.throws(() => verifyManifest({ app: { bundleName: base, debug: true } }, 'debug', `${base}.dev`), /refusing installation/);
  assert.throws(() => verifyManifest({ app: { bundleName: `${base}.dev`, debug: false } }, 'debug', `${base}.dev`), /refusing installation/);
  assert.throws(() => verifyManifest({ app: { bundleName: base, debug: true } }, 'debug', base), /refusing installation/);
  assert.throws(() => verifyManifest({ app: { bundleName: `${base}.dev` } }, 'debug', `${base}.dev`), /refusing installation/);
});

test('HAP verification reads the packaged manifest and rejects a stale official artifact', { skip: process.platform === 'win32' }, () => {
  const fixture = fs.mkdtempSync(path.join(os.tmpdir(), 'mafei-hap-identity-'));
  try {
    for (const bundle of [base, `${base}.dev`]) {
      fs.writeFileSync(path.join(fixture, 'module.json'), JSON.stringify({ app: { bundleName: bundle, debug: true } }));
      const hap = path.join(fixture, `${bundle}.hap`);
      const zipped = spawnSync('zip', ['-q', hap, 'module.json'], { cwd: fixture, encoding: 'utf8' });
      assert.equal(zipped.status, 0, zipped.stderr);
      const verified = spawnSync(process.execPath, [path.join(root, 'runscript/resolveOhosBundle.cjs'), '--verify-hap', hap, 'debug', `${base}.dev`], { encoding: 'utf8' });
      assert.equal(verified.status, bundle === base ? 1 : 0, verified.stdout + verified.stderr);
      if (bundle === base) assert.match(verified.stderr, /refusing installation/);
    }
  } finally {
    fs.rmSync(fixture, { recursive: true, force: true });
  }
});

test('Mac runner rejects missing or invalid build mode before any environment or device operation', { skip: process.platform === 'win32' }, () => {
  for (const args of [['-m'], ['-m', 'invalid'], ['--unknown']]) {
    const result = spawnSync('bash', [path.join(root, 'runscript/runOhosApp-Mac.sh'), ...args], { encoding: 'utf8' });
    assert.equal(result.status, 2, result.stdout + result.stderr);
    assert.doesNotMatch(result.stdout, /Checking environment/);
  }
});

test('Mac runner only force-stops the resolved development app and rejects an unrelated IDE bundle', { skip: process.platform === 'win32' }, () => {
  const fixture = fs.mkdtempSync(path.join(os.tmpdir(), 'mafei-identity-'));
  try {
    const devEco = path.join(fixture, 'DevEco');
    const bin = path.join(fixture, 'bin');
    const hdc = path.join(devEco, 'Contents/sdk/default/openharmony/toolchains/hdc');
    fs.mkdirSync(path.dirname(hdc), { recursive: true });
    fs.mkdirSync(bin);
    fs.mkdirSync(path.join(fixture, 'external/AppScope'), { recursive: true });
    fs.writeFileSync(path.join(fixture, 'external/AppScope/app.json5'), JSON.stringify({ app: { bundleName: base } }));
    fs.writeFileSync(path.join(bin, 'defaults'), '#!/bin/sh\necho 6.0.0\n', { mode: 0o755 });
    fs.writeFileSync(hdc, '#!/bin/sh\nprintf "%s\\n" "$*" >> "$MAFEI_TEST_HDC_LOG"\n', { mode: 0o755 });
    // Stop at the build boundary; this test must never build or contact a device.
    fs.writeFileSync(path.join(fixture, 'gradlew'), '#!/bin/sh\nexit 19\n', { mode: 0o755 });
    const log = path.join(fixture, 'device.log');
    const env = { ...process.env, DEVECO_PATH: devEco, DEVECO_HOME: path.join(devEco, 'Contents'), PATH: `${bin}${path.delimiter}${process.env.PATH}`, MAFEI_TEST_HDC_LOG: log };
    const run = (bundle) => spawnSync('bash', [path.join(root, 'runscript/runOhosApp-Mac.sh'), '-b', bundle, '-p', path.join(fixture, 'external'), 'ohosArm64', 'fixture-phone'], { cwd: fixture, env, encoding: 'utf8' });
    const rejected = run('com.unrelated.app');
    assert.equal(rejected.status, 1, rejected.stdout + rejected.stderr);
    assert.equal(fs.existsSync(log), false, 'an invalid identity must not reach hdc');
    const accepted = run(base);
    assert.equal(accepted.status, 19, accepted.stdout + accepted.stderr);
    assert.equal(fs.readFileSync(log, 'utf8').trim(), `-t fixture-phone shell aa force-stop ${base}.dev`);
  } finally {
    fs.rmSync(fixture, { recursive: true, force: true });
  }
});
