import { readdirSync, readFileSync, statSync } from 'node:fs'
import { dirname, relative, resolve } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'

const CONTRACT_FILE = 'scripts/native_theme_page_contracts.json'
const FORBIDDEN_COLORS = [
  'bg_main',
  'bg_1',
  'bg_2',
  'component_bg',
  'menu_item_bg',
  'bg_tab',
  'bg_tab_item',
  'tab',
  'border',
  'border_1'
]
const FORBIDDEN_COLOR_LITERALS = ['#CBD2D3']
const VISUAL_CONFLICTS = [
  'background',
  'backgroundColor',
  'backgroundBlurStyle',
  'backgroundEffect',
  'blur',
  'border',
  'borderWidth',
  'borderColor',
  'shadow',
  'visualEffect'
]
const EXCEPTION_KINDS = new Set(['player', 'media', 'dangerous-dialog'])
const PLAIN_SURFACE_KINDS = new Set(['content-group', 'borderless-control'])
const BORDER_ATTRIBUTES = ['border', 'borderWidth', 'borderColor']
const CONTROL_WORDS = new Set([
  'if', 'else', 'for', 'while', 'switch', 'catch', 'return', 'new', 'throw'
])

function requiredSource(sources, path) {
  const source = sources.get(path)
  if (source === undefined) {
    throw new Error('missing source: ' + path)
  }
  return source
}

function structuralSource(source) {
  let result = ''
  let index = 0
  let state = 'code'

  while (index < source.length) {
    const char = source[index]
    const next = source[index + 1]

    if (state === 'line-comment') {
      if (char === '\n') {
        result += '\n'
        state = 'code'
      } else {
        result += ' '
      }
      index += 1
      continue
    }

    if (state === 'block-comment') {
      if (char === '*' && next === '/') {
        result += '  '
        index += 2
        state = 'code'
      } else {
        result += char === '\n' ? '\n' : ' '
        index += 1
      }
      continue
    }

    if (state === 'single-quote' || state === 'double-quote' || state === 'template') {
      const quote = state === 'single-quote' ? "'" : state === 'double-quote' ? '"' : '`'
      if (char === '\\') {
        result += ' '
        if (index + 1 < source.length) {
          result += source[index + 1] === '\n' ? '\n' : ' '
        }
        index += 2
      } else if (char === quote) {
        result += ' '
        index += 1
        state = 'code'
      } else {
        result += char === '\n' ? '\n' : ' '
        index += 1
      }
      continue
    }

    if (char === '/' && next === '/') {
      result += '  '
      index += 2
      state = 'line-comment'
    } else if (char === '/' && next === '*') {
      result += '  '
      index += 2
      state = 'block-comment'
    } else if (char === "'") {
      result += ' '
      index += 1
      state = 'single-quote'
    } else if (char === '"') {
      result += ' '
      index += 1
      state = 'double-quote'
    } else if (char === '`') {
      result += ' '
      index += 1
      state = 'template'
    } else {
      result += char
      index += 1
    }
  }

  return result
}

function matchingDelimiter(source, openingIndex, opening, closing) {
  const structural = structuralSource(source)
  let depth = 0
  for (let index = openingIndex; index < structural.length; index += 1) {
    if (structural[index] === opening) {
      depth += 1
    } else if (structural[index] === closing) {
      depth -= 1
      if (depth === 0) {
        return index
      }
    }
  }
  throw new Error('unterminated ' + opening + closing + ' block')
}

function componentRanges(source) {
  const structural = structuralSource(source)
  const pattern = /\b(?:export\s+)?struct\s+([A-Za-z_$][\w$]*)[^\{]*\{/g
  const components = []
  for (const match of structural.matchAll(pattern)) {
    const opening = structural.indexOf('{', match.index)
    const end = matchingDelimiter(source, opening, '{', '}')
    components.push({
      name: match[1],
      start: match.index,
      bodyStart: opening + 1,
      end
    })
  }
  return components
}

function directDepthAt(structural, start, position) {
  let depth = 0
  for (let index = start; index < position; index += 1) {
    if (structural[index] === '{') {
      depth += 1
    } else if (structural[index] === '}') {
      depth -= 1
    }
  }
  return depth
}

function isBuilderDecorator(source, componentStart, methodStart) {
  const prefix = source.slice(componentStart, methodStart)
  return /@Builder\s*$/.test(prefix)
}

function methodRanges(source) {
  const structural = structuralSource(source)
  const components = componentRanges(source)
  const methods = []
  const signature = /^[ \t]*(?:(?:private|public|protected|static|async|export)[ \t]+)*([A-Za-z_$][\w$]*)[ \t]*\(/gm

  for (const component of components) {
    signature.lastIndex = component.bodyStart
    let match
    while ((match = signature.exec(structural)) !== null && match.index < component.end) {
      if (directDepthAt(structural, component.bodyStart, match.index) !== 0 ||
        CONTROL_WORDS.has(match[1])) {
        continue
      }

      const openingParen = structural.indexOf('(', match.index)
      const closingParen = matchingDelimiter(source, openingParen, '(', ')')
      let cursor = closingParen + 1
      let angleDepth = 0
      while (cursor < component.end) {
        const char = structural[cursor]
        if (char === '<') {
          angleDepth += 1
        } else if (char === '>' && angleDepth > 0) {
          angleDepth -= 1
        } else if (angleDepth === 0 && char === '{') {
          break
        } else if (angleDepth === 0 && (char === '=' || char === ';')) {
          cursor = -1
          break
        }
        cursor += 1
      }
      if (cursor < 0 || cursor >= component.end) {
        continue
      }

      const end = matchingDelimiter(source, cursor, '{', '}')
      methods.push({
        component: component.name,
        name: match[1],
        isBuilder: isBuilderDecorator(source, component.bodyStart, match.index),
        start: match.index,
        bodyStart: cursor + 1,
        end,
        body: source.slice(cursor + 1, end)
      })
      signature.lastIndex = end + 1
    }
  }

  const globalBuilder = /@Builder\s+(?:export\s+)?function\s+([A-Za-z_$][\w$]*)\s*\(/g
  for (const match of structural.matchAll(globalBuilder)) {
    if (directDepthAt(structural, 0, match.index) !== 0) {
      continue
    }
    const openingParen = structural.indexOf('(', match.index)
    const closingParen = matchingDelimiter(source, openingParen, '(', ')')
    let openingBrace = closingParen + 1
    while (/\s/.test(structural[openingBrace] ?? '')) {
      openingBrace += 1
    }
    if (structural[openingBrace] !== '{') {
      continue
    }
    const end = matchingDelimiter(source, openingBrace, '{', '}')
    methods.push({
      component: '@global',
      name: match[1],
      isBuilder: true,
      start: match.index,
      bodyStart: openingBrace + 1,
      end,
      body: source.slice(openingBrace + 1, end)
    })
  }

  return methods
}

function nearestMethod(methods, position) {
  return methods
    .filter((method) => method.bodyStart <= position && position < method.end)
    .sort((left, right) => (left.end - left.bodyStart) - (right.end - right.bodyStart))[0]
}

function ownerKey(path, component, method) {
  return path + '#' + component + '#' + method
}

function contractOwnerKey(entry, label) {
  if (!entry || typeof entry.path !== 'string' || typeof entry.component !== 'string' ||
    typeof entry.method !== 'string') {
    throw new Error(label + ' entries require exact path, component, and method')
  }
  return ownerKey(entry.path, entry.component, entry.method)
}

function contractOwnerSet(entries, label) {
  if (!Array.isArray(entries)) {
    throw new Error(label + ' must be an array')
  }
  const owners = new Set()
  for (const entry of entries) {
    const key = contractOwnerKey(entry, label)
    if (owners.has(key)) {
      throw new Error('duplicate ' + label + ' owner: ' + key)
    }
    owners.add(key)
  }
  return owners
}

function attributeCalls(source, attribute) {
  const structural = structuralSource(source)
  const pattern = new RegExp('\\.' + attribute + '\\s*\\(', 'g')
  const calls = []
  for (const match of structural.matchAll(pattern)) {
    const opening = structural.indexOf('(', match.index)
    const closing = matchingDelimiter(source, opening, '(', ')')
    calls.push({
      start: match.index,
      opening,
      end: closing,
      expression: source.slice(opening + 1, closing)
    })
  }
  return calls
}

function optionExpressions(source, option) {
  const structural = structuralSource(source)
  const pattern = new RegExp('\\b' + option + '\\s*:', 'g')
  const options = []
  for (const match of structural.matchAll(pattern)) {
    let cursor = match.index + match[0].length
    while (/\s/.test(structural[cursor] ?? '')) {
      cursor += 1
    }
    const start = cursor
    let parentheses = 0
    let brackets = 0
    let braces = 0
    while (cursor < structural.length) {
      const char = structural[cursor]
      if (char === '(') {
        parentheses += 1
      } else if (char === ')') {
        parentheses -= 1
      } else if (char === '[') {
        brackets += 1
      } else if (char === ']') {
        brackets -= 1
      } else if (char === '{') {
        braces += 1
      } else if (char === '}') {
        if (parentheses === 0 && brackets === 0 && braces === 0) {
          break
        }
        braces -= 1
      } else if (char === ',' && parentheses === 0 && brackets === 0 && braces === 0) {
        break
      }
      cursor += 1
    }
    options.push({
      start: match.index,
      expression: source.slice(start, cursor).trim()
    })
  }
  return options
}

function containsConditionalMaterial(expression) {
  return structuralSource(expression).includes('?')
}

function conflictInBuilder(body) {
  const structural = structuralSource(body)
  for (const conflict of VISUAL_CONFLICTS) {
    if (new RegExp('\\.' + conflict + '\\s*\\(').test(structural)) {
      return conflict
    }
  }
  return undefined
}

function forbiddenColor(expression) {
  for (const color of FORBIDDEN_COLORS) {
    if (new RegExp("app\\.color\\." + color + "(?:['\"]|\\b)").test(expression)) {
      return color
    }
  }
  const normalized = expression.toUpperCase()
  for (const color of FORBIDDEN_COLOR_LITERALS) {
    if (normalized.includes(color)) {
      return color
    }
  }
  return undefined
}

function stripOuterParentheses(expression) {
  let result = expression.trim()
  while (result.startsWith('(')) {
    let closing
    try {
      closing = matchingDelimiter(result, 0, '(', ')')
    } catch (error) {
      return result
    }
    if (closing !== result.length - 1) {
      return result
    }
    result = result.slice(1, -1).trim()
  }
  return result
}

function splitTopLevelTernary(expression) {
  const structural = structuralSource(expression)
  let parentheses = 0
  let brackets = 0
  let braces = 0
  let question = -1
  let nestedTernaries = 0
  for (let index = 0; index < structural.length; index += 1) {
    const char = structural[index]
    if (char === '(') {
      parentheses += 1
    } else if (char === ')') {
      parentheses -= 1
    } else if (char === '[') {
      brackets += 1
    } else if (char === ']') {
      brackets -= 1
    } else if (char === '{') {
      braces += 1
    } else if (char === '}') {
      braces -= 1
    } else if (parentheses === 0 && brackets === 0 && braces === 0 && char === '?') {
      if (question < 0) {
        question = index
      } else {
        nestedTernaries += 1
      }
    } else if (parentheses === 0 && brackets === 0 && braces === 0 && char === ':' && question >= 0) {
      if (nestedTernaries === 0) {
        return {
          condition: expression.slice(0, question).trim(),
          whenTrue: expression.slice(question + 1, index).trim(),
          whenFalse: expression.slice(index + 1).trim()
        }
      }
      nestedTernaries -= 1
    }
  }
  return undefined
}

function nativeBranchForCondition(condition) {
  const normalized = structuralSource(condition).replace(/\s+/g, ' ').trim()
  const negated = /^!\s*/.test(normalized) || /(?:!==|!=)\s*ThemeStyle\.Native/.test(normalized)
  if (/\b(?:showLegacy\w*|legacy\w*|feiniu\w*)\b/i.test(normalized)) {
    return negated ? 'true' : 'false'
  }
  if (/\b(?:useNative\w*|isNative\w*|native(?:Surface|Material|Theme|Mode)?)\b/i.test(normalized) ||
    /ThemeStyle\.Native/.test(normalized)) {
    return negated ? 'false' : 'true'
  }
  return undefined
}

function avoidsForbiddenColorInNative(expression, methods = [], component, seen = new Set()) {
  const stripped = stripOuterParentheses(expression)
  const helper = /^this\.([A-Za-z_$][\w$]*)\s*\(\s*\)$/.exec(
    structuralSource(stripped).trim())
  if (helper && component && !seen.has(helper[1])) {
    const helperMethods = methods.filter((method) =>
      method.component === component && method.name === helper[1])
    if (helperMethods.length === 1) {
      const returns = [...helperMethods[0].body.matchAll(/\breturn\s+([^;\n}]+)/g)]
      if (returns.length > 0) {
        const nextSeen = new Set(seen)
        nextSeen.add(helper[1])
        return returns.every((match) =>
          avoidsForbiddenColorInNative(match[1], methods, component, nextSeen))
      }
    }
  }
  if (!forbiddenColor(stripped)) {
    return true
  }
  const ternary = splitTopLevelTernary(stripped)
  if (!ternary) {
    return false
  }
  const nativeBranch = nativeBranchForCondition(ternary.condition)
  if (nativeBranch === 'true') {
    return avoidsForbiddenColorInNative(ternary.whenTrue, methods, component, seen)
  }
  if (nativeBranch === 'false') {
    return avoidsForbiddenColorInNative(ternary.whenFalse, methods, component, seen)
  }
  return avoidsForbiddenColorInNative(ternary.whenTrue, methods, component, seen) &&
    avoidsForbiddenColorInNative(ternary.whenFalse, methods, component, seen)
}

function isLegacyOnlyMethod(method) {
  return /(?:feiniu|legacy)/i.test(method.name)
}

function branchRanges(body) {
  const structural = structuralSource(body)
  const ranges = []
  const pattern = /\bif\s*\(/g
  for (const match of structural.matchAll(pattern)) {
    const openingParen = structural.indexOf('(', match.index)
    let closingParen
    try {
      closingParen = matchingDelimiter(body, openingParen, '(', ')')
    } catch (error) {
      continue
    }
    let openingBrace = closingParen + 1
    while (/\s/.test(structural[openingBrace] ?? '')) {
      openingBrace += 1
    }
    if (structural[openingBrace] !== '{') {
      continue
    }
    const trueEnd = matchingDelimiter(body, openingBrace, '{', '}')
    let cursor = trueEnd + 1
    while (/\s/.test(structural[cursor] ?? '')) {
      cursor += 1
    }
    let falseRange
    if (structural.slice(cursor, cursor + 4) === 'else') {
      cursor += 4
      while (/\s/.test(structural[cursor] ?? '')) {
        cursor += 1
      }
      if (structural[cursor] === '{') {
        falseRange = { start: cursor + 1, end: matchingDelimiter(body, cursor, '{', '}') }
      }
    }
    ranges.push({
      nativeBranch: nativeBranchForCondition(body.slice(openingParen + 1, closingParen)),
      trueRange: { start: openingBrace + 1, end: trueEnd },
      falseRange
    })
  }
  return ranges
}

function occursOnlyInLegacyBranch(method, localPosition) {
  const containing = branchRanges(method.body)
    .filter((branch) =>
      (branch.trueRange.start <= localPosition && localPosition < branch.trueRange.end) ||
      (branch.falseRange && branch.falseRange.start <= localPosition && localPosition < branch.falseRange.end))
    .sort((left, right) =>
      (left.trueRange.end - left.trueRange.start) - (right.trueRange.end - right.trueRange.start))[0]
  if (!containing?.nativeBranch) {
    return false
  }
  const inTrue = containing.trueRange.start <= localPosition && localPosition < containing.trueRange.end
  return (containing.nativeBranch === 'true' && !inTrue) ||
    (containing.nativeBranch === 'false' && inTrue)
}

function methodForEntry(methods, entry, label) {
  const matches = methods.filter((method) =>
    method.component === entry.component && method.name === entry.method)
  if (matches.length !== 1) {
    throw new Error(label + ' must resolve exactly one method: ' +
      ownerKey(entry.path, entry.component, entry.method))
  }
  return matches[0]
}

function validateMaterialOwners(sources, contract, parsedMethods) {
  const allowedAttributes = contractOwnerSet(contract.attributeOwners, 'attributeOwners')
  const allowedOptions = contractOwnerSet(contract.optionOwners, 'optionOwners')
  const seenAttributes = new Set()
  const seenOptions = new Set()

  for (const [path, source] of sources.entries()) {
    const methods = parsedMethods.get(path) ?? []
    for (const call of attributeCalls(source, 'systemMaterial')) {
      const method = nearestMethod(methods, call.start)
      if (!method || !method.isBuilder) {
        throw new Error('.systemMaterial must belong to an @Builder method: ' + path)
      }
      const key = ownerKey(path, method.component, method.name)
      if (!allowedAttributes.has(key)) {
        throw new Error('unlisted .systemMaterial attribute owner: ' + key)
      }
      if (containsConditionalMaterial(call.expression)) {
        throw new Error('conditional .systemMaterial is forbidden: ' + key)
      }
      const conflict = conflictInBuilder(method.body)
      if (conflict) {
        throw new Error('.systemMaterial Builder also owns .' + conflict + ': ' + key)
      }
      seenAttributes.add(key)
    }

    for (const option of optionExpressions(source, 'systemMaterial')) {
      const method = nearestMethod(methods, option.start)
      if (!method) {
        throw new Error('systemMaterial option must belong to a method: ' + path)
      }
      const key = ownerKey(path, method.component, method.name)
      if (!allowedOptions.has(key)) {
        throw new Error('unlisted systemMaterial option owner: ' + key)
      }
      if (containsConditionalMaterial(option.expression)) {
        throw new Error('conditional systemMaterial option is forbidden: ' + key)
      }
      seenOptions.add(key)
    }
  }

  for (const key of allowedAttributes) {
    if (!seenAttributes.has(key)) {
      throw new Error('contract attribute owner has no .systemMaterial call: ' + key)
    }
  }
  for (const key of allowedOptions) {
    if (!seenOptions.has(key)) {
      throw new Error('contract option owner has no systemMaterial option: ' + key)
    }
  }
}

function normalizedExpression(expression) {
  return structuralSource(expression).replace(/\s+/g, '')
}

function validatePlainSurfaceOwners(sources, contract, parsedMethods) {
  const owners = new Set()
  for (const entry of contract.plainSurfaceOwners) {
    const key = contractOwnerKey(entry, 'plainSurfaceOwners')
    if (!PLAIN_SURFACE_KINDS.has(entry.kind)) {
      throw new Error('plain surface owner requires a supported kind: ' + key)
    }
    if (owners.has(key)) {
      throw new Error('duplicate plain surface owner: ' + key)
    }
    owners.add(key)

    const source = requiredSource(sources, entry.path)
    const methods = parsedMethods.get(entry.path) ?? methodRanges(source)
    const method = methodForEntry(methods, entry, 'plainSurfaceOwners')
    if (!method.isBuilder) {
      throw new Error('plain Native surface must be an @Builder: ' + key)
    }
    if (attributeCalls(method.body, 'systemMaterial').length > 0) {
      throw new Error('plain Native surface must not use .systemMaterial: ' + key)
    }

    if (entry.kind === 'content-group') {
      const hasSemanticBackground = attributeCalls(method.body, 'backgroundColor')
        .some((call) => normalizedExpression(call.expression) ===
          'AppThemeSurfaceResolver.contentGroupBackground()')
      if (!hasSemanticBackground) {
        throw new Error('plain content group must use contentGroupBackground: ' + key)
      }
      continue
    }

    for (const attribute of BORDER_ATTRIBUTES) {
      if (attributeCalls(method.body, attribute).length > 0) {
        throw new Error('borderless Native control must not use .' + attribute + ': ' + key)
      }
    }
  }
}

function rootBackgroundCalls(method) {
  const calls = [
    ...attributeCalls(method.body, 'backgroundColor').map((call) => ({ ...call, name: 'backgroundColor' })),
    ...attributeCalls(method.body, 'background').map((call) => ({ ...call, name: 'background' }))
  ]
  if (calls.length === 0) {
    return []
  }
  const structural = structuralSource(method.body)
  const depths = calls.map((call) => directDepthAt(structural, 0, call.start))
  const minimumDepth = Math.min(...depths)
  return calls.filter((_call, index) => depths[index] === minimumDepth)
}

function transparentRootExpression(expression, methods, component, seen = new Set()) {
  const stripped = stripOuterParentheses(expression)
  if (/^Color\.Transparent$/.test(structuralSource(stripped).trim())) {
    return true
  }
  if (/^AppThemeSurfaceResolver\.routeBackground\s*\([\s\S]*\)$/.test(
    structuralSource(stripped).trim())) {
    return true
  }
  const helper = /^this\.([A-Za-z_$][\w$]*)\s*\(\s*\)$/.exec(structuralSource(stripped).trim())
  if (helper) {
    if (helper[1] === 'cardColor' || seen.has(helper[1])) {
      return false
    }
    const helperMethods = methods.filter((method) =>
      method.component === component && method.name === helper[1])
    if (helperMethods.length !== 1) {
      return false
    }
    const returns = [...helperMethods[0].body.matchAll(/\breturn\s+([^;\n}]+)/g)]
    if (returns.length === 0) {
      return false
    }
    const nextSeen = new Set(seen)
    nextSeen.add(helper[1])
    return returns.every((match) =>
      transparentRootExpression(match[1], methods, component, nextSeen))
  }
  const ternary = splitTopLevelTernary(stripped)
  if (!ternary) {
    return false
  }
  const nativeBranch = nativeBranchForCondition(ternary.condition)
  if (nativeBranch === 'true') {
    return transparentRootExpression(ternary.whenTrue, methods, component, seen)
  }
  if (nativeBranch === 'false') {
    return transparentRootExpression(ternary.whenFalse, methods, component, seen)
  }
  return transparentRootExpression(ternary.whenTrue, methods, component, seen) &&
    transparentRootExpression(ternary.whenFalse, methods, component, seen)
}

function validateTransparentRoots(sources, contract, parsedMethods) {
  contractOwnerSet(contract.transparentNativeRoots, 'transparentNativeRoots')
  for (const entry of contract.transparentNativeRoots) {
    const source = requiredSource(sources, entry.path)
    const methods = parsedMethods.get(entry.path) ?? methodRanges(source)
    const method = methodForEntry(methods, entry, 'transparentNativeRoots')
    if (entry.builder === true && !method.isBuilder) {
      throw new Error('transparent Native root must be an @Builder: ' +
        ownerKey(entry.path, entry.component, entry.method))
    }
    for (const call of rootBackgroundCalls(method)) {
      if (call.name === 'background' || /\bthis\.cardColor\s*\(\s*\)/.test(call.expression) ||
        !transparentRootExpression(call.expression, methods, method.component)) {
        throw new Error('Native root must stay transparent: ' +
          ownerKey(entry.path, entry.component, entry.method))
      }
    }
  }
}

function validateCardColorMethods(sources, contract, parsedMethods) {
  const rootComponents = new Map()
  for (const entry of contract.transparentNativeRoots) {
    const key = entry.path + '#' + entry.component
    rootComponents.set(key, true)
  }

  for (const [path, methods] of parsedMethods.entries()) {
    for (const method of methods) {
      if (method.name !== 'cardColor' || !rootComponents.has(path + '#' + method.component)) {
        continue
      }
      const returns = [...method.body.matchAll(/\breturn\s+([^;\n}]+)/g)]
      for (const match of returns) {
        if (forbiddenColor(match[1]) &&
          !avoidsForbiddenColorInNative(match[1], methods, method.component)) {
          throw new Error('Native cardColor returns an opaque legacy color: ' +
            ownerKey(path, method.component, method.name))
        }
      }
      if (forbiddenColor(method.body) && returns.length === 0) {
        throw new Error('Native cardColor returns an opaque legacy color: ' +
          ownerKey(path, method.component, method.name))
      }
    }
  }
}

function validateOpaqueBackgrounds(sources, contract, parsedMethods) {
  if (!Array.isArray(contract.opaqueBackgroundExceptions)) {
    throw new Error('opaqueBackgroundExceptions must be an array')
  }
  const exceptions = new Set()
  for (const entry of contract.opaqueBackgroundExceptions) {
    const key = contractOwnerKey(entry, 'opaqueBackgroundExceptions')
    if (!EXCEPTION_KINDS.has(entry.kind)) {
      throw new Error('opaque background exception requires an exact supported kind: ' + key)
    }
    if (exceptions.has(key)) {
      throw new Error('duplicate opaque background exception: ' + key)
    }
    const methods = parsedMethods.get(entry.path) ?? []
    methodForEntry(methods, entry, 'opaqueBackgroundExceptions')
    exceptions.add(key)
  }

  const usedExceptions = new Set()
  const visualAuditPaths = new Set()
  for (const path of contract.visualAuditPaths) {
    if (typeof path !== 'string' || path.length === 0) {
      throw new Error('visualAuditPaths must contain non-empty source paths')
    }
    if (visualAuditPaths.has(path)) {
      throw new Error('duplicate visual audit path: ' + path)
    }
    requiredSource(sources, path)
    visualAuditPaths.add(path)
  }
  const auditedPaths = new Set([
    ...contract.attributeOwners.map((entry) => entry.path),
    ...contract.optionOwners.map((entry) => entry.path),
    ...contract.plainSurfaceOwners.map((entry) => entry.path),
    ...contract.transparentNativeRoots.map((entry) => entry.path),
    ...contract.opaqueBackgroundExceptions.map((entry) => entry.path),
    ...visualAuditPaths
  ])

  for (const path of auditedPaths) {
    const source = requiredSource(sources, path)
    const methods = parsedMethods.get(path) ?? []
    const backgroundOwners = [
      ...attributeCalls(source, 'backgroundColor'),
      ...attributeCalls(source, 'background'),
      ...optionExpressions(source, 'backgroundColor'),
      ...attributeCalls(source, 'borderColor'),
      ...attributeCalls(source, 'border'),
      ...attributeCalls(source, 'backgroundEffect'),
      ...attributeCalls(source, 'shadow')
    ]
    for (const call of backgroundOwners) {
      const avoidsLegacyColorInNative = avoidsForbiddenColorInNative(
        call.expression, methods, nearestMethod(methods, call.start)?.component)
      if (!forbiddenColor(call.expression) && avoidsLegacyColorInNative) {
        continue
      }
      const method = nearestMethod(methods, call.start)
      if (!method) {
        throw new Error('legacy background color outside a method: ' + path)
      }
      const key = ownerKey(path, method.component, method.name)
      const localPosition = call.start - method.bodyStart
      if (exceptions.has(key)) {
        usedExceptions.add(key)
        continue
      }
      if (isLegacyOnlyMethod(method) ||
        avoidsLegacyColorInNative ||
        occursOnlyInLegacyBranch(method, localPosition)) {
        continue
      }
      throw new Error('opaque legacy background can reach the Native theme: ' + key)
    }
  }

  for (const key of exceptions) {
    if (!usedExceptions.has(key)) {
      throw new Error('opaque background exception does not match a current legacy color: ' + key)
    }
  }
}

function validateContractShape(contract) {
  if (!contract || typeof contract !== 'object') {
    throw new Error('Native theme page contract must be an object')
  }
  for (const field of [
    'attributeOwners',
    'optionOwners',
    'plainSurfaceOwners',
    'transparentNativeRoots',
    'visualAuditPaths',
    'opaqueBackgroundExceptions'
  ]) {
    if (!Array.isArray(contract[field])) {
      throw new Error('missing contract array: ' + field)
    }
  }
}

export function defaultWorkspaceRoot() {
  return dirname(dirname(fileURLToPath(import.meta.url)))
}

export function discoverNativeThemeMaterialOwners(sources) {
  const attributeOwners = new Map()
  const optionOwners = new Map()
  for (const [path, source] of sources.entries()) {
    const methods = methodRanges(source)
    for (const call of attributeCalls(source, 'systemMaterial')) {
      const method = nearestMethod(methods, call.start)
      if (!method) {
        throw new Error('.systemMaterial must belong to a method: ' + path)
      }
      const entry = { path, component: method.component, method: method.name }
      attributeOwners.set(ownerKey(path, method.component, method.name), entry)
    }
    for (const option of optionExpressions(source, 'systemMaterial')) {
      const method = nearestMethod(methods, option.start)
      if (!method) {
        throw new Error('systemMaterial option must belong to a method: ' + path)
      }
      const entry = { path, component: method.component, method: method.name }
      optionOwners.set(ownerKey(path, method.component, method.name), entry)
    }
  }
  return {
    attributeOwners: [...attributeOwners.values()],
    optionOwners: [...optionOwners.values()]
  }
}

export function validateNativeThemePageContracts(sources, contract) {
  validateContractShape(contract)
  const parsedMethods = new Map()
  for (const [path, source] of sources.entries()) {
    parsedMethods.set(path, methodRanges(source))
  }
  validatePlainSurfaceOwners(sources, contract, parsedMethods)
  validateMaterialOwners(sources, contract, parsedMethods)
  validateTransparentRoots(sources, contract, parsedMethods)
  validateCardColorMethods(sources, contract, parsedMethods)
  validateOpaqueBackgrounds(sources, contract, parsedMethods)
}

function collectEtsFiles(directory, root, sources) {
  for (const name of readdirSync(directory)) {
    const absolute = resolve(directory, name)
    if (statSync(absolute).isDirectory()) {
      collectEtsFiles(absolute, root, sources)
    } else if (name.endsWith('.ets')) {
      sources.set(relative(root, absolute), readFileSync(absolute, 'utf8'))
    }
  }
}

function workspaceSources(root) {
  const sources = new Map()
  collectEtsFiles(resolve(root, 'entry/src/main/ets'), root, sources)
  return sources
}

export function validateWorkspace(root = defaultWorkspaceRoot()) {
  const sources = workspaceSources(root)
  const contract = JSON.parse(readFileSync(resolve(root, CONTRACT_FILE), 'utf8'))
  validateNativeThemePageContracts(sources, contract)
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
  if (process.argv.includes('--print-material-owners')) {
    console.log(JSON.stringify(
      discoverNativeThemeMaterialOwners(workspaceSources(defaultWorkspaceRoot())), null, 2))
  } else {
    validateWorkspace()
    console.log('Native theme page contracts verified')
  }
}
