import assert from 'node:assert/strict'
import test from 'node:test'
import {
  inspectBuilderSource,
  validateAdapterAllowlist,
  validateWorkspace
} from './verify_no_new_builders.mjs'

test('accepts an adjacent marked Builder adapter', () => {
  const inspected = inspectBuilderSource([
    '// CustomBuilderAdapter: example-sheet',
    '@Builder',
    'function exampleSheet() {}'
  ].join('\n'), 'example.ets')
  assert.deepEqual(inspected.violations, [])
  assert.deepEqual(inspected.adapters, [{ id: 'example-sheet', path: 'example.ets', line: 2 }])
})

test('rejects unmarked or detached Builder decorators', () => {
  assert.match(inspectBuilderSource('@Builder\nfunction content() {}').violations[0], /缺少紧邻/)
  assert.match(inspectBuilderSource([
    '// CustomBuilderAdapter: detached',
    '',
    '@Builder',
    'function content() {}'
  ].join('\n')).violations.join('\n'), /必须紧邻|缺少紧邻/)
})

test('rejects BuilderParam CustomBuilder and wrapBuilder', () => {
  const inspected = inspectBuilderSource([
    '@BuilderParam content: () => void',
    'private content: CustomBuilder',
    'const wrapped = wrapBuilder(content)'
  ].join('\n'))
  assert.match(inspected.violations.join('\n'), /@BuilderParam/)
  assert.match(inspected.violations.join('\n'), /CustomBuilder/)
  assert.match(inspected.violations.join('\n'), /wrapBuilder/)
})

test('requires exact adapter ids and file paths', () => {
  const adapter = { id: 'example-sheet', path: 'entry/example.ets', line: 2 }
  assert.doesNotThrow(() => validateAdapterAllowlist([adapter], {
    'example-sheet': 'entry/example.ets'
  }))
  assert.throws(() => validateAdapterAllowlist([adapter], {}), /未审查/)
  assert.throws(() => validateAdapterAllowlist([adapter], {
    'example-sheet': 'entry/other.ets'
  }), /应位于/)
})

test('rejects duplicate and stale allowlist entries', () => {
  const adapter = { id: 'example-sheet', path: 'entry/example.ets', line: 2 }
  assert.throws(() => validateAdapterAllowlist([adapter, adapter], {
    'example-sheet': 'entry/example.ets'
  }), /重复/)
  assert.throws(() => validateAdapterAllowlist([], {
    'stale-sheet': 'entry/stale.ets'
  }), /不存在/)
})

test('current workspace matches the reviewed Builder adapter allowlist', () => {
  assert.equal(validateWorkspace(), 28)
})
