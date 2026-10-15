import { execFileSync } from 'node:child_process';

// Use only a development device explicitly supplied by the caller. Every tap
// resolves fresh UIAutomator bounds; screenshots never determine coordinates.
const serial = process.argv[2];
if (!serial) throw new Error('Usage: node scripts/android-probe-smoke.mjs <adb-serial>');
const adb = (...args) => execFileSync('adb', ['-s', serial, ...args], { encoding: 'utf8', timeout: 30_000 });
const packageName = 'cn.xiaobai.mafei.cmp.dev';
function tree() {
  adb('shell', 'uiautomator', 'dump', '/sdcard/mafei-probe-smoke.xml');
  return adb('exec-out', 'cat', '/sdcard/mafei-probe-smoke.xml');
}
function requireText(text) {
  const xml = tree();
  if (!xml.includes(`text="${text}"`)) throw new Error(`Missing probe text: ${text}`);
  console.log(`PASS visible: ${text}`);
}
function tap(text) {
  const nodes = [...tree().matchAll(/<node\s+[^>]*>/g)].map(it => it[0]);
  const node = nodes.find(it => it.includes(`package="${packageName}"`) && it.includes(`text="${text}"`));
  if (!node) throw new Error(`Cannot locate probe target ${text}`);
  const bounds = node.match(/bounds="\[(\d+),(\d+)\]\[(\d+),(\d+)\]"/);
  if (!bounds) throw new Error(`Target has no bounds: ${text}`);
  const [x1, y1, x2, y2] = bounds.slice(1).map(Number);
  if (x2 <= x1 || y2 <= y1) throw new Error(`Target not visible: ${text}`);
  console.log(`TAP ${text} bounds=[${x1},${y1}][${x2},${y2}]`);
  adb('shell', 'input', 'tap', String(Math.floor((x1 + x2) / 2)), String(Math.floor((y1 + y2) / 2)));
}

// Intentional cold launch of the *dev* package only. Does not clear app data.
adb('shell', 'am', 'force-stop', packageName);
adb('shell', 'am', 'start', '-W', '-n', `${packageName}/cn.mafei.cmp.MainActivity`);
tap('实验室');
requireText('Compose 资源已加载');
tap('浅色');
requireText('深色');
tap('深色');
requireText('浅色');
tap('液态玻璃 · 收起说明');
requireText('液态玻璃 · 展开说明');
tap('液态玻璃 · 展开说明');
requireText('液态玻璃 · 收起说明');
for (const [index, material] of ['完整玻璃', '简化玻璃', '实体背景'].entries()) {
  tap(material);
  tap(`命中 · ${index}`);
  requireText(`命中 · ${index + 1}`);
}
// Restore both system settings even on assertion failure. This exercises an
// Activity configuration change, not process-death or GPU-failure recovery.
const rotationSettings = ['accelerometer_rotation', 'user_rotation'].map(key =>
  [key, adb('shell', 'settings', 'get', 'system', key).trim()]);
try {
  adb('shell', 'settings', 'put', 'system', 'accelerometer_rotation', '0');
  adb('shell', 'settings', 'put', 'system', 'user_rotation', '1');
  if (!tree().includes('rotation="1"')) throw new Error('Landscape rotation not observed');
  requireText('命中 · 3');
  tap('命中 · 3');
  requireText('命中 · 4');
  adb('shell', 'settings', 'put', 'system', 'user_rotation', '0');
  if (!tree().includes('rotation="0"')) throw new Error('Portrait rotation not observed');
  requireText('命中 · 4');
} finally {
  for (const [key, value] of rotationSettings) {
    if (value === 'null') adb('shell', 'settings', 'delete', 'system', key);
    else adb('shell', 'settings', 'put', 'system', key, value);
  }
}
tap('检查弹层');
requireText('弹层可读性检查');
adb('shell', 'input', 'keyevent', '4');
requireText('命中 · 4');
tap('模拟特效熔断');
requireText('已熔断 · 实体回退');
tap('完整玻璃');
requireText('请求 Full → 能力档位 Opaque');
tap('命中 · 4');
requireText('命中 · 5');
adb('shell', 'input', 'keyevent', '3');
adb('shell', 'am', 'start', '-W', '-n', `${packageName}/cn.mafei.cmp.MainActivity`);
requireText('命中 · 5');
requireText('已熔断 · 实体回退');
console.log('PASS: Android probe theme, materials, rotation, real taps, dialog back, simulated fallback and foreground round-trip');
