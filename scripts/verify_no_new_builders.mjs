import { readFileSync, readdirSync } from 'node:fs'
import { dirname, join, relative, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const workspaceRoot = dirname(dirname(fileURLToPath(import.meta.url)))
const sourceRoot = resolve(workspaceRoot, 'entry/src/main/ets')
const allowlistPath = resolve(workspaceRoot, 'scripts/legacy_builder_baseline.json')
const adapterMarkerPattern = /^\s*\/\/\s*CustomBuilderAdapter:\s*([a-z0-9][a-z0-9-]*)\s*$/
const builderPattern = /^\s*@Builder\b/
const forbiddenPatterns = [
  { label: '@BuilderParam', pattern: /^\s*@BuilderParam\b/ },
  { label: 'CustomBuilder', pattern: /\bCustomBuilder\b/ },
  { label: 'wrapBuilder', pattern: /\bwrapBuilder\b/ }
]

function collectEtsFiles(directory, files = []) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = join(directory, entry.name)
    if (entry.isDirectory()) {
      if (path === resolve(sourceRoot, 'generated')) {
        continue
      }
      collectEtsFiles(path, files)
    } else if (entry.isFile() && entry.name.endsWith('.ets')) {
      files.push(path)
    }
  }
  return files
}

export function inspectBuilderSource(source, path = 'source.ets') {
  const lines = source.split(/\r?\n/)
  const adapters = []
  const violations = []

  for (let index = 0; index < lines.length; index += 1) {
    const line = lines[index]
    for (const forbidden of forbiddenPatterns) {
      if (forbidden.pattern.test(line)) {
        violations.push(`${path}:${index + 1}: 禁止使用 ${forbidden.label}`)
      }
    }

    const marker = line.match(adapterMarkerPattern)
    if (marker && !builderPattern.test(lines[index + 1] || '')) {
      violations.push(`${path}:${index + 1}: CustomBuilderAdapter 标记后必须紧邻 @Builder`)
    }

    if (!builderPattern.test(line)) {
      continue
    }
    const previousMarker = (lines[index - 1] || '').match(adapterMarkerPattern)
    if (!previousMarker) {
      violations.push(`${path}:${index + 1}: @Builder 缺少紧邻的 CustomBuilderAdapter 标记`)
      continue
    }
    adapters.push({ id: previousMarker[1], path, line: index + 1 })
  }

  return { adapters, violations }
}

export function validateAdapterAllowlist(adapters, allowlist) {
  const violations = []
  const actual = new Map()

  for (const adapter of adapters) {
    if (actual.has(adapter.id)) {
      violations.push(`${adapter.path}:${adapter.line}: 重复的 CustomBuilderAdapter ID ${adapter.id}`)
      continue
    }
    actual.set(adapter.id, adapter.path)
    const allowedPath = allowlist[adapter.id]
    if (!allowedPath) {
      violations.push(`${adapter.path}:${adapter.line}: 未审查的 Builder 适配器 ${adapter.id}`)
    } else if (allowedPath !== adapter.path) {
      violations.push(`${adapter.path}:${adapter.line}: Builder 适配器 ${adapter.id} 应位于 ${allowedPath}`)
    }
  }

  for (const [id, path] of Object.entries(allowlist)) {
    if (!actual.has(id)) {
      violations.push(`${path}: 白名单适配器 ${id} 不存在，请同步删除白名单项`)
    }
  }

  if (violations.length > 0) {
    throw new Error(`Builder 适配门禁失败：\n${violations.join('\n')}`)
  }
}

export function inspectWorkspace() {
  const adapters = []
  const violations = []
  for (const absolutePath of collectEtsFiles(sourceRoot)) {
    const path = relative(workspaceRoot, absolutePath)
    const inspected = inspectBuilderSource(readFileSync(absolutePath, 'utf8'), path)
    adapters.push(...inspected.adapters)
    violations.push(...inspected.violations)
  }
  return { adapters, violations }
}

export function validateWorkspace() {
  const baseline = JSON.parse(readFileSync(allowlistPath, 'utf8'))
  if (baseline.version !== 1 || !baseline.adapters) {
    throw new Error('Builder 适配白名单格式无效')
  }
  const inspected = inspectWorkspace()
  if (inspected.violations.length > 0) {
    throw new Error(`Builder 适配门禁失败：\n${inspected.violations.join('\n')}`)
  }
  validateAdapterAllowlist(inspected.adapters, baseline.adapters)
  return inspected.adapters.length
}

if (process.argv[1] === fileURLToPath(import.meta.url)) {
  const total = validateWorkspace()
  console.log(`Builder 适配门禁通过，已审查 ${total} 个必要适配壳`)
}
