const { createHash } = require('node:crypto');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');

// The SDK producer marked this exact local candidate unfit for production.
const developmentHarSha = '2c0f52bb850fccef5178c064ffe226896aadca77b08279e42f62d2dfae4d0df9';
const developmentBridgeSha = '5558c9fd3269c21abaca5f7286419e5aa72f3dd22c64dffeb57e884e6a493413';

function digest(file) {
  try {
    return createHash('sha256').update(readFileSync(file)).digest('hex');
  } catch (error) {
    if (error.code === 'ENOENT') return null; // OHPM diagnoses missing SDK inputs.
    throw error;
  }
}

function assertReleaseMfplayer(harmonyRoot) {
  const entry = join(harmonyRoot, 'entry');
  if (digest(join(entry, 'libs/mfplayer.har')) === developmentHarSha ||
      digest(join(entry, 'oh_modules/mfplayer/libs/arm64-v8a/libmpvnative.so')) === developmentBridgeSha) {
    throw new Error('MFPlayer 1.6.0-rc1 short-drama candidate is development-only. Use a release-qualified HAR.');
  }
}

module.exports = { assertReleaseMfplayer };
