const { createHash } = require('node:crypto');
const { mkdirSync, mkdtempSync, readFileSync, readdirSync, rmSync, writeFileSync } = require('node:fs');
const { tmpdir } = require('node:os');
const { dirname, join } = require('node:path');
const assert = require('node:assert/strict');
const test = require('node:test');
const { prepareDebugMfplayer } = require('./prepare-mfplayer.cjs');

function fixture(t) {
  const root = mkdtempSync(join(tmpdir(), 'mafei-mfplayer-test-'));
  t.after(() => rmSync(root, { recursive: true, force: true }));
  return { root, destination: join(root, 'entry', 'libs', 'mfplayer.har') };
}

const artifact = Buffer.from([0x1f, 0x8b, 0x00, 0xff]);
const sha256 = createHash('sha256').update(artifact).digest('hex');

test('fetches and installs the pinned bytes on an empty checkout', (t) => {
  const { root, destination } = fixture(t);
  assert.equal(prepareDebugMfplayer(root, { download: () => artifact, sha256 }), true);
  assert.deepEqual(readFileSync(destination), artifact);
  assert.deepEqual(readdirSync(dirname(destination)), ['mfplayer.har']);
});

test('keeps an existing SDK without accessing GitHub', (t) => {
  const { root, destination } = fixture(t);
  mkdirSync(dirname(destination), { recursive: true });
  writeFileSync(destination, 'local SDK');
  assert.equal(prepareDebugMfplayer(root, { download: () => assert.fail('unexpected fetch') }), false);
  assert.equal(readFileSync(destination, 'utf8'), 'local SDK');
});

test('leaves no SDK behind when download or integrity checks fail', (t) => {
  const { root } = fixture(t);
  assert.throws(() => prepareDebugMfplayer(root, { download: () => { throw new Error('no access'); } }), /no access/);
  assert.throws(() => prepareDebugMfplayer(root, { download: () => Buffer.from('wrong bytes'), sha256 }), /SHA-256/);
  assert.deepEqual(readdirSync(root), []);
});

test('does not replace a local SDK added during download', (t) => {
  const { root, destination } = fixture(t);
  assert.equal(prepareDebugMfplayer(root, {
    sha256,
    download: () => {
      mkdirSync(dirname(destination), { recursive: true });
      writeFileSync(destination, 'parallel install');
      return artifact;
    },
  }), false);
  assert.equal(readFileSync(destination, 'utf8'), 'parallel install');
  assert.deepEqual(readdirSync(dirname(destination)), ['mfplayer.har']);
});
