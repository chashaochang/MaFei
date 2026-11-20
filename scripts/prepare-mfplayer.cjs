const { spawnSync } = require('node:child_process');
const { createHash } = require('node:crypto');
const {
  existsSync, linkSync, mkdirSync, mkdtempSync, rmSync, statSync, writeFileSync,
} = require('node:fs');
const { tmpdir } = require('node:os');
const { dirname, join } = require('node:path');

const repository = 'https://github.com/chashaochang/mafei-player-artifacts.git';
const revision = 'b87e795a858b8148ae1f7e1f19cf7417de96e01e';
const filename = 'mfplayer-1.6.0-rc1.har';
const digest = 'eba974bd95b9ba258ab91a08f8c59a418709aa9c0bd6b1ce494199ce6c586657';

function downloadHar() {
  const checkout = mkdtempSync(join(tmpdir(), 'mafei-mfplayer-git-'));
  function git(...args) {
    const result = spawnSync('git', ['-C', checkout, ...args], {
      env: { ...process.env, GIT_TERMINAL_PROMPT: '0', GH_PROMPT_DISABLED: '1' },
      maxBuffer: 32 * 1024 * 1024,
      timeout: 120_000,
    });
    if (result.status !== 0) {
      const detail = result.error?.message ?? result.stderr.toString().trim();
      throw new Error(`Unable to fetch MFPlayer. Install Git and GitHub CLI, run "gh auth login --hostname github.com" with an account that can read mafei-player-artifacts, then retry.\n${detail}`);
    }
    return result.stdout;
  }

  try {
    git('init', '--quiet');
    git('-c', 'credential.helper=', '-c', 'credential.helper=!gh auth git-credential',
      'fetch', '--quiet', '--no-tags', '--depth=1', repository, revision);
    return git('show', `${revision}:${filename}`);
  } finally {
    rmSync(checkout, { recursive: true, force: true });
  }
}

function prepareDebugMfplayer(harmonyRoot, { download = downloadHar, sha256 = digest } = {}) {
  const destination = join(harmonyRoot, 'entry', 'libs', 'mfplayer.har');
  if (existsSync(destination)) {
    const existing = statSync(destination);
    if (!existing.isFile() || existing.size === 0) {
      throw new Error(`Not a usable local MFPlayer HAR: ${destination}`);
    }
    return false;
  }

  const bytes = download();
  if (createHash('sha256').update(bytes).digest('hex') !== sha256) {
    throw new Error('MFPlayer download failed its SHA-256 check; nothing was installed.');
  }

  mkdirSync(dirname(destination), { recursive: true });
  const staging = mkdtempSync(join(dirname(destination), '.mfplayer-'));
  try {
    const file = join(staging, 'download.har');
    writeFileSync(file, bytes);
    // Publish atomically without replacing an SDK installed by another process.
    linkSync(file, destination);
  } catch (error) {
    if (error.code !== 'EEXIST') throw error;
    return false;
  } finally {
    rmSync(staging, { recursive: true, force: true });
  }
  return true;
}

if (require.main === module) {
  try {
    const installed = prepareDebugMfplayer(join(__dirname, '..', 'harmonyApp'));
    console.log(installed ? `Prepared ${filename} for HarmonyOS Debug.` : 'Using the existing local MFPlayer HAR.');
  } catch (error) {
    console.error(error.message);
    process.exitCode = 1;
  }
}

module.exports = { prepareDebugMfplayer };
