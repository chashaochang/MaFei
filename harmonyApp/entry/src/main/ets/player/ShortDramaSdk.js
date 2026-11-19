import * as mfplayer from 'mfplayer';

// The production HAR predates this optional export. Keep the check at the
// module boundary so loading the ordinary player never requires the RC SDK.
export function hasShortDramaSdk() {
  return typeof mfplayer.MultiInstancePlayer === 'function';
}

export function createShortDramaPlayer() {
  return new mfplayer.MultiInstancePlayer();
}
