import test from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import { productionSources, sourceSizeViolations as check, lineCount } from './verify-source-size.mjs';
const source = lines => Array(lines).fill('val x = 1').join('\n') + '\n';
const budgets = files => ({ defaultMaxLines: 800, files });

test('new production source including helpers is capped at 800 physical lines', () => {
  assert.deepEqual(check({ 'helper.kt': source(800) }, budgets({})), []);
  assert.match(check({ 'helper.kt': source(801) }, budgets({}))[0], /801 lines exceeds 800/);
  assert.equal(lineCount('one\ntwo\n'), 2);
});
test('existing large files can shrink without altering the limit', () => {
  assert.deepEqual(check({ 'Screen.kt': source(850) }, budgets({ 'Screen.kt': 900 }), budgets({ 'Screen.kt': 900 })), []);
  assert.deepEqual(check({ 'Screen.kt': source(850) }, budgets({ 'Screen.kt': 850 }), budgets({ 'Screen.kt': 900 })), []);
});
test('raising a limit or creating another exception cannot hide growth', () => {
  assert.match(check({ 'Screen.kt': source(901) }, budgets({ 'Screen.kt': 901 }), budgets({ 'Screen.kt': 900 })).join('\n'), /cannot increase/);
  assert.match(check({ 'helper.kt': source(901) }, budgets({ 'helper.kt': 901 }), budgets({})).join('\n'), /new exception/);
});
test('completed splits remove obsolete exceptions', () => {
  assert.deepEqual(check({ 'Screen.kt': source(400) }, budgets({}), budgets({ 'Screen.kt': 900 })), []);
  assert.match(check({}, budgets({ 'deleted.kt': 900 })).join('\n'), /remove the budget/);
});
test('invalid or relaxed defaults are rejected', () => {
  assert.match(check({}, { defaultMaxLines: 900, files: {} }).join('\n'), /must remain 800/);
  assert.match(check({ 'a.kt': '' }, budgets({ 'a.kt': '900' })).join('\n'), /integer/);
});
test('scanner includes active production helpers but excludes tests, legacy, build and vendor', () => {
  const root = mkdtempSync(join(tmpdir(), 'mafei-size-'));
  try {
    const included = ['feature/home/src/commonMain/kotlin/Helper.kt', 'composeApp/src/sharedUiMain/kotlin/Entry.kt'];
    const excluded = ['feature/home/src/desktopTest/kotlin/Test.kt', 'legacy/entry/src/commonMain/Old.kt',
      'common/glass/vendor/lib/src/commonMain/Vendor.kt', 'feature/home/build/src/commonMain/Generated.kt'];
    for (const path of [...included, ...excluded]) {
      mkdirSync(dirname(join(root, path)), { recursive: true });
      writeFileSync(join(root, path), 'fun example() {}\n');
    }
    assert.deepEqual(Object.keys(productionSources(root)).sort(), included.sort());
  } finally { rmSync(root, { recursive: true }); }
});
