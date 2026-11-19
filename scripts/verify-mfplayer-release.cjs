const { createHash } = require('node:crypto');
const { readFileSync } = require('node:fs');
const { join } = require('node:path');

const developmentHarShas = new Set([
  '2c0f52bb850fccef5178c064ffe226896aadca77b08279e42f62d2dfae4d0df9',
  '0fe6aa36bc79d5931a899d3089f54f50cc2a8ca6a82e83de0c190f68fa18b365',
  'eba974bd95b9ba258ab91a08f8c59a418709aa9c0bd6b1ce494199ce6c586657',
]);
const developmentBridgeShas = new Set([
  '5558c9fd3269c21abaca5f7286419e5aa72f3dd22c64dffeb57e884e6a493413',
  'c437c11124338505c1ac4064b40d2bd8e6b71eaf28d84442b912fa7a42e06852',
  'af61c98ada16197f151c4c9187137844dd3810460c72673c9213b60bf1f525ea',
]);

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
  if (developmentHarShas.has(digest(join(entry, 'libs/mfplayer.har'))) ||
      developmentBridgeShas.has(digest(join(entry, 'oh_modules/mfplayer/libs/arm64-v8a/libmpvnative.so')))) {
    throw new Error('MFPlayer 1.6.0-rc1 short-drama candidate is development-only. Use a release-qualified HAR.');
  }
}

module.exports = { assertReleaseMfplayer };
