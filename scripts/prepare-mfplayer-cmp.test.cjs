const assert = require('node:assert/strict');
const { mkdirSync, mkdtempSync, readdirSync, rmSync, writeFileSync } = require('node:fs');
const { tmpdir } = require('node:os');
const { join } = require('node:path');
const test = require('node:test');
const { prepareCmpMfplayer } = require('./prepare-mfplayer-cmp.cjs');

function fixture(t) {
  const root = mkdtempSync(join(tmpdir(), 'mafei-cmp-sdk-test-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  return { root, sdk: join(root, 'build', 'mfplayer-sdk') };
}

test('refuses a changed download before extraction and removes the staging directory', t => {
  const { root, sdk } = fixture(t);
  assert.throws(() => prepareCmpMfplayer(root, {
    fetch: (name, dir) => writeFileSync(join(dir, name), 'wrong bytes'),
    unzip: () => assert.fail('Unverified bytes must not be unpacked'),
  }), /pinned SHA-256/);
  assert.deepEqual(readdirSync(sdk), []);
});

test('does not overwrite a local directory without a matching verification marker', t => {
  const { root, sdk } = fixture(t);
  const destination = join(sdk, 'mfplayer-cmp-0.1.0-rc.1');
  mkdirSync(destination, { recursive: true });
  writeFileSync(join(destination, 'local-file'), 'keep this');
  assert.throws(() => prepareCmpMfplayer(root, {
    fetch: () => assert.fail('existing directory must be preserved'),
  }), /Incomplete SDK directory/);
  assert.deepEqual(readdirSync(destination), ['local-file']);
});
