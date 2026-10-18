#!/usr/bin/env node
const fs = require('node:fs');
const { execFileSync } = require('node:child_process');

function resolveBundleName(base, mode, requested) {
  if (mode !== 'debug' && mode !== 'release') {
    throw new Error(`Unsupported build mode: ${mode}; expected debug or release`);
  }
  if (typeof base !== 'string' || !/^[A-Za-z][A-Za-z0-9_]*(\.[A-Za-z][A-Za-z0-9_]*)+$/.test(base) || base.endsWith('.dev')) {
    throw new Error('AppScope/app.json5 must declare the official bundleName without .dev');
  }
  const expected = mode === 'debug' ? `${base}.dev` : base;
  // IDE configurations may still pass the official name; normalize it for debug.
  if (requested && requested !== expected && !(mode === 'debug' && requested === base)) {
    throw new Error(`Requested bundle ${requested} does not match ${expected}`);
  }
  return expected;
}

function verifyManifest(manifest, mode, expected) {
  if (mode !== 'debug' && mode !== 'release') throw new Error(`Unsupported build mode: ${mode}`);
  if (manifest?.app?.bundleName !== expected) {
    throw new Error(`HAP bundleName ${manifest?.app?.bundleName} does not match ${expected}; refusing installation`);
  }
  if (manifest.app.debug !== (mode === 'debug')) {
    throw new Error(`HAP debug=${manifest.app.debug} does not match build mode ${mode}; refusing installation`);
  }
  if ((mode === 'debug') !== expected.endsWith('.dev')) {
    throw new Error(`Bundle ${expected} is not valid for build mode ${mode}; refusing installation`);
  }
}

function readHapManifest(hapPath) {
  let json;
  if (process.platform === 'win32') {
    json = execFileSync('powershell.exe', ['-NoProfile', '-NonInteractive', '-Command', `
      $ErrorActionPreference = 'Stop'
      [Console]::OutputEncoding = [System.Text.UTF8Encoding]::new($false)
      Add-Type -AssemblyName System.IO.Compression.FileSystem
      $zip = [System.IO.Compression.ZipFile]::OpenRead($env:MAFEI_VERIFY_HAP_PATH)
      try {
        $entry = $zip.GetEntry('module.json')
        if ($null -eq $entry) { throw 'HAP is missing module.json' }
        $reader = [System.IO.StreamReader]::new($entry.Open())
        try { [Console]::Write($reader.ReadToEnd()) } finally { $reader.Dispose() }
      } finally { $zip.Dispose() }
    `], { encoding: 'utf8', env: { ...process.env, MAFEI_VERIFY_HAP_PATH: hapPath } });
  } else {
    json = execFileSync('unzip', ['-p', hapPath, 'module.json'], { encoding: 'utf8' });
  }
  return JSON.parse(json);
}

module.exports = { resolveBundleName, verifyManifest, readHapManifest };

if (require.main === module) {
  try {
    const args = process.argv.slice(2);
    if (args[0] === '--verify-hap') {
      if (args.length !== 4) throw new Error('Usage: resolveOhosBundle.cjs --verify-hap <hap> <debug|release> <expectedBundle>');
      verifyManifest(readHapManifest(args[1]), args[2], args[3]);
      console.log(`Verified HAP identity: ${args[3]} (${args[2]})`);
    } else {
      if (args.length < 2 || args.length > 3) throw new Error('Usage: resolveOhosBundle.cjs <AppScope/app.json5> <debug|release> [requestedBundle]');
      const manifest = JSON.parse(fs.readFileSync(args[0], 'utf8'));
      console.log(resolveBundleName(manifest.app?.bundleName, args[1], args[2]));
    }
  } catch (error) {
    console.error(`Error: ${error.message}`);
    process.exitCode = 1;
  }
}
