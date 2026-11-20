const { spawnSync } = require('node:child_process');
const { createHash } = require('node:crypto');
const { existsSync, mkdirSync, mkdtempSync, readFileSync, renameSync, rmSync, writeFileSync } = require('node:fs');
const { join } = require('node:path');

const repository = 'chashaochang/mafei-player-artifacts';
const tag = 'mfplayer-cmp-0.1.0-rc.1';
const artifacts = {
  maven: '81be1d87f643f819eb504047aac324b2cf8bc260e6a0e99f6c971cad823287c1',
  macos: '1f1f75b0a6b74c564b2db4df281f8f06092e5e547da058eeb9d21b7270ca5202',
};
const expected = [
  'maven/org/mfplayer/cmp/player-api/0.1.0-rc.1/player-api-0.1.0-rc.1.module',
  'macos/native/libmfplayer_jvm.dylib',
];

function run(command, args) {
  const result = spawnSync(command, args, {
    encoding: 'utf8',
    env: { ...process.env, GH_PROMPT_DISABLED: '1' },
    timeout: 900_000,
  });
  if (result.status !== 0) {
    throw new Error(`${command} failed: ${result.error?.message ?? result.stderr.trim()}`);
  }
}

function download(asset, directory) {
  try {
    run('gh', ['release', 'download', tag, '--repo', repository, '--pattern', asset, '--dir', directory]);
  } catch (error) {
    throw new Error(`Could not download ${asset} from ${repository}. Check the connection and confirm "gh auth status --hostname github.com" has read access.\n${error.message}`);
  }
}

function prepareCmpMfplayer(root, { fetch = download, unzip = run } = {}) {
  const base = join(root, 'build', 'mfplayer-sdk');
  const destination = join(base, tag);
  const seal = JSON.stringify(artifacts);
  function prepared(path) {
    const marker = join(path, '.assets.json');
    return existsSync(marker) && readFileSync(marker, 'utf8') === seal &&
      expected.every(file => existsSync(join(path, file)));
  }
  if (prepared(destination)) return destination;
  if (existsSync(destination)) {
    throw new Error(`Incomplete SDK directory at ${destination}. Move it aside and retry.`);
  }
  mkdirSync(base, { recursive: true });
  const staging = mkdtempSync(join(base, '.download-'));
  const unpacked = join(staging, 'sdk');
  mkdirSync(unpacked);
  try {
    for (const [platform, digest] of Object.entries(artifacts)) {
      const name = `${tag}-${platform}.zip`;
      const file = join(staging, name);
      fetch(name, staging);
      if (createHash('sha256').update(readFileSync(file)).digest('hex') !== digest) {
        throw new Error(`${name} failed its pinned SHA-256 check.`);
      }
      unzip('unzip', ['-q', file, '-d', join(unpacked, platform)]);
    }
    writeFileSync(join(unpacked, '.assets.json'), seal);
    if (!prepared(unpacked)) throw new Error('MFPlayer CMP SDK archive is missing required files.');
    try {
      renameSync(unpacked, destination);
    } catch (error) {
      if (!prepared(destination)) throw error;
    }
    return destination;
  } finally {
    rmSync(staging, { recursive: true, force: true });
  }
}

if (require.main === module) {
  try {
    console.log(`Prepared MFPlayer CMP SDK: ${prepareCmpMfplayer(join(__dirname, '..'))}`);
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}

module.exports = { prepareCmpMfplayer };
