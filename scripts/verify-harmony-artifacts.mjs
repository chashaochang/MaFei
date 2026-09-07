import { execFileSync } from 'node:child_process';
import { resolve, dirname } from 'node:path';
import { readFileSync } from 'node:fs';
import { createHash } from 'node:crypto';
import { fileURLToPath } from 'node:url';

// Run after publishDebugBinariesToHarmonyApp and assembleHap. No extraction,
// signing or deployment; generated header and library must match their source.
const root = resolve(process.argv[2] ?? resolve(dirname(fileURLToPath(import.meta.url)), '..'));
const hap = resolve(process.argv[3] ?? resolve(root, 'harmonyApp/entry/build/default/outputs/default/entry-default-unsigned.hap'));
function check(ok, message) { if (!ok) throw new Error(message); }
for (const [name, staged] of [
  ['libkn.so', 'harmonyApp/entry/libs/arm64-v8a/libkn.so'],
  ['libkn_api.h', 'harmonyApp/entry/src/main/cpp/include/arm64-v8a/libkn_api.h'],
]) {
  execFileSync('cmp', ['-s', resolve(root, `composeApp/build/bin/ohosArm64/debugShared/${name}`), resolve(root, staged)]);
}
const entries = execFileSync('unzip', ['-Z1', hap], { encoding: 'utf8' }).trim().split('\n');
for (const entry of entries.filter(it => /^libs\/.+\.so$/.test(it))) {
  check(entry.startsWith('libs/arm64-v8a/'), `Unexpected packaged ABI: ${entry}`);
}
for (const name of ['libkn.so', 'libentry.so', 'libskikobridge.so', 'libc++_shared.so']) {
  check(entries.includes(`libs/arm64-v8a/${name}`), `Missing native library: ${name}`);
}
// Hvigor strips debug symbols, so packaged bytes must match its stripped
// intermediate, not the larger original link output. Verify the copy-in too.
execFileSync('cmp', ['-s', resolve(root, 'harmonyApp/entry/libs/arm64-v8a/libkn.so'),
  resolve(root, 'harmonyApp/entry/build/default/intermediates/libs/default/arm64-v8a/libkn.so')]);
const strippedKn = readFileSync(resolve(root, 'harmonyApp/entry/build/default/intermediates/stripped_native_libs/default/arm64-v8a/libkn.so'));
const linkedKn = readFileSync(resolve(root, 'composeApp/build/bin/ohosArm64/debugShared/libkn.so'));
function buildId(elf) {
  check(elf.subarray(0, 6).equals(Buffer.from([0x7f, 0x45, 0x4c, 0x46, 2, 1])) && elf.readUInt16LE(18) === 183,
    'Expected little-endian ARM64 ELF');
  const sectionTable = Number(elf.readBigUInt64LE(40));
  const entrySize = elf.readUInt16LE(58);
  for (let i = 0; i < elf.readUInt16LE(60); i++) {
    const section = sectionTable + i * entrySize;
    if (elf.readUInt32LE(section + 4) !== 7) continue; // SHT_NOTE
    let note = Number(elf.readBigUInt64LE(section + 24));
    const end = note + Number(elf.readBigUInt64LE(section + 32));
    while (note + 12 <= end) {
      const nameSize = elf.readUInt32LE(note);
      const dataSize = elf.readUInt32LE(note + 4);
      const type = elf.readUInt32LE(note + 8);
      const data = note + 12 + Math.ceil(nameSize / 4) * 4;
      check(data + dataSize <= end && end <= elf.length, 'Malformed ELF note');
      if (type === 3 && elf.subarray(note + 12, note + 12 + nameSize).toString() === 'GNU\0') {
        return elf.subarray(data, data + dataSize).toString('hex');
      }
      note = data + Math.ceil(dataSize / 4) * 4;
    }
  }
  throw new Error('Missing GNU build ID');
}
check(buildId(linkedKn) === buildId(strippedKn), 'Stripped libkn is from another link build');
const packedKn = execFileSync('unzip', ['-p', hap, 'libs/arm64-v8a/libkn.so'], { maxBuffer: strippedKn.length + 1024 });
const hash = bytes => createHash('sha256').update(bytes).digest('hex');
check(hash(packedKn) === hash(strippedKn), 'HAP libkn differs from Hvigor stripped intermediate');
const resource = 'resources/rawfile/composeResources/mafei.composeapp.generated.resources/values/strings.commonMain.cvr';
check(entries.includes(resource), 'Missing or misplaced Compose resource probe');
const packaged = execFileSync('unzip', ['-p', hap, resource]);
const prepared = readFileSync(resolve(root, 'composeApp/build/generated/compose/resourceGenerator/preparedResources/commonMain/composeResources/values/strings.commonMain.cvr'));
check(packaged.equals(prepared), 'Packaged Compose resource differs from prepared resource');
console.log('PASS: matched libkn/header and Hvigor copy-in; same-build stripped/package libkn; HAP ARM64 libraries; byte-identical Compose resource');
console.log('Signing, installation and runtime are separate checks.');
