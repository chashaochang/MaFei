import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const roots = ['common', 'feature', 'player', 'composeApp', 'platform', 'harmonyApp'];
const excluded = /^(?:build|vendor|legacy|generated|node_modules|oh_modules|\.git|\.hvigor|\.cxx|out|test|tests|__tests__|testFixtures|.*Test)$/i;
const oldInlineApis = new Set(['InlineProblem', 'MaFeiInlineNotice']);
const errorWord = /(?:^|_)(?:error|failure|failed|problem)(?:$|_)|(?:Error|Failure|Failed|Problem)(?:$|[A-Z_])|^(?:error|failure|failed|problem)[A-Z_]/;
const markerPattern = /^\/\/\s*mafei-feedback:\s*(full-page-error|dialog-content|business-history|diagnostic-state)\s*--\s*(\S.{7,})\s*$/;

function inlineComponentName(name) {
  if (!/^[A-Z]/.test(name)) return false;
  const words = name.replace(/([A-Z]+)([A-Z][a-z])/g, '$1 $2').replace(/([a-z0-9])([A-Z])/g, '$1 $2').toLowerCase().split(/[\s_]+/);
  const surfaces = ['inline', 'banner', 'notice', 'card', 'row', 'section'];
  const errors = ['error', 'failure', 'problem'];
  return [...surfaces, ...errors].includes(words.at(-1)) && words.some(word => errors.includes(word)) && words.some(word => surfaces.includes(word));
}

/** Keep offsets and executable interpolation; discard comments and literal text. */
function sourceCode(source, file) {
  const output = source.split('');
  const comments = [];
  const blank = (start, end) => {
    for (let i = start; i < end; i++) if (output[i] !== '\n' && output[i] !== '\r') output[i] = ' ';
  };
  const kotlin = /\.kts?$/.test(file);
  function string(start) {
    const quote = source[start];
    if (quote === '`' && kotlin) {
      const end = source.indexOf('`', start + 1);
      blank(start, start + 1);
      if (end >= 0) blank(end, end + 1);
      return end < 0 ? source.length : end + 1;
    }
    const delimiter = source.startsWith('"""', start) ? '"""' : quote;
    const templates = quote === '`' || quote === '"' && kotlin;
    blank(start, start + delimiter.length);
    let i = start + delimiter.length;
    while (i < source.length) {
      if (source.startsWith(delimiter, i)) {
        blank(i, i + delimiter.length);
        return i + delimiter.length;
      }
      if (source[i] === '\\' && delimiter.length === 1) {
        blank(i, i + 2); i += 2; continue;
      }
      if (templates && source.startsWith('${', i)) {
        blank(i, i + 1);
        i = code(i + 2, true);
        continue;
      }
      if (templates && source[i] === '$' && /[A-Za-z_]/.test(source[i + 1] ?? '')) {
        blank(i, i + 1); i++;
        while (i < source.length && /\w/.test(source[i])) i++;
        continue;
      }
      blank(i, i + 1); i++;
    }
    return i;
  }
  function code(start, interpolation = false) {
    let depth = 1;
    let i = start;
    while (i < source.length) {
      if (source.startsWith('//', i)) {
        const end = source.indexOf('\n', i);
        const stop = end < 0 ? source.length : end;
        comments.push({ start: i, value: source.slice(i, stop) });
        blank(i, stop); i = stop;
      } else if (source.startsWith('/*', i)) {
        const startComment = i;
        let nesting = 1; i += 2;
        while (i < source.length && nesting > 0) {
          if (source.startsWith('/*', i)) { nesting++; i += 2; }
          else if (source.startsWith('*/', i)) { nesting--; i += 2; }
          else i++;
        }
        blank(startComment, i);
      } else if ('"\'`'.includes(source[i])) {
        i = string(i);
      } else {
        if (interpolation && source[i] === '{') depth++;
        if (interpolation && source[i] === '}' && --depth === 0) return i + 1;
        i++;
      }
    }
    return i;
  }
  code(0);
  return { code: output.join(''), comments };
}

function tokensOf(code) {
  return [...code.matchAll(/[A-Za-z_$][\w$]*|->|=>|[^\s]/g)]
    .map(match => ({ value: match[0], start: match.index, end: match.index + match[0].length }));
}

function matchingPairs(tokens) {
  const pairs = new Map();
  const stack = [];
  const closers = { ')': '(', '}': '{', ']': '[' };
  for (let i = 0; i < tokens.length; i++) {
    const value = tokens[i].value;
    if ('({['.includes(value)) stack.push(i);
    else if (closers[value] && tokens[stack.at(-1)]?.value === closers[value]) {
      const start = stack.pop(); pairs.set(start, i);
    }
  }
  return pairs;
}

function argumentsOf(tokens, start, end, pairs) {
  const args = [];
  let argument = start + 1;
  for (let i = argument; i < end; i++) {
    if (tokens[i].value === ',') { args.push(tokens.slice(argument, i)); argument = i + 1; }
    else if (pairs.has(i)) i = pairs.get(i);
  }
  args.push(tokens.slice(argument, end));
  return args;
}

/** Policy guard, deliberately not a type checker or proof of visual layout. */
export function feedbackViolations(source, file = 'Screen.kt') {
  const { code, comments } = sourceCode(source, file);
  const tokens = tokensOf(code);
  const pairs = matchingPairs(tokens);
  const lineAt = offset => source.slice(0, offset).split('\n').length;
  const violations = [];
  const markers = new Map();
  for (const comment of comments.filter(item => item.value.includes('mafei-feedback:'))) {
    const match = markerPattern.exec(comment.value);
    const line = lineAt(comment.start);
    const lineStart = source.lastIndexOf('\n', comment.start - 1) + 1;
    if (!match || source.slice(lineStart, comment.start).trim()) {
      violations.push({ line, rule: 'invalid-exception', message: 'Use a standalone, next-line mafei-feedback marker with an approved category and a specific reason (8+ characters).' });
    } else markers.set(line + 1, { line, category: match[1], used: false });
  }
  const report = (token, rule, message, canExempt = false) => {
    const line = lineAt(token.start);
    const marker = markers.get(line);
    if (canExempt && marker && !marker.used) { marker.used = true; return; }
    if (!violations.some(item => item.line === line && item.rule === rule)) violations.push({ line, rule, message });
  };
  const renderers = new Set(['Text', 'BasicText', 'RichText', 'Label']);
  for (const match of code.matchAll(/\bimport\s+[\w.]+\.(Text|BasicText|RichText|Label)\s+as\s+(\w+)/g)) renderers.add(match[2]);

  // Standard single-argument lambdas introduce their own it. Receiver lambdas
  // (Column, run, apply, etc.) do not, so an outer error alias remains visible.
  const implicitItCalls = new Set(['let', 'also', 'forEach', 'map', 'mapNotNull', 'filter', 'filterNot', 'onEach']);
  const aliases = [];
  for (let i = 0; i < tokens.length; i++) {
    if (!implicitItCalls.has(tokens[i].value) || tokens[i + 1]?.value !== '{' || !pairs.has(i + 1)) continue;
    let receiver = i - 1;
    while (receiver >= 0 && ['.', '?', '!'].includes(tokens[receiver].value)) receiver--;
    const explicit = tokens[i + 3]?.value === '->';
    aliases.push({
      name: explicit ? tokens[i + 2].value : 'it', start: i + 1, end: pairs.get(i + 1),
      error: tokens[i].value === 'let' && errorWord.test(tokens[receiver]?.value ?? ''),
    });
  }
  // Track straightforward local aliases so val message = state.error cannot hide a Text call.
  const isError = (token, index) => {
    const binding = aliases.filter(alias => alias.name === token.value && index > alias.start && index < alias.end)
      .reduce((inner, alias) => !inner || alias.start > inner.start ? alias : inner, null);
    return errorWord.test(token.value) || binding?.error === true;
  };
  for (let i = 0; i < tokens.length; i++) {
    if (!['val', 'var', 'const', 'let'].includes(tokens[i].value)) continue;
    const declarationLine = lineAt(tokens[i].start);
    let assignment = i + 2;
    if (tokens[assignment]?.value === ':') {
      while (assignment < tokens.length && lineAt(tokens[assignment].start) === declarationLine &&
        !['=', ';', '{', '}'].includes(tokens[assignment].value)) assignment++;
    }
    if (tokens[assignment]?.value !== '=') continue;
    let end = assignment + 1;
    while (end < tokens.length && lineAt(tokens[end].start) === declarationLine && ![';', '}'].includes(tokens[end].value)) end++;
    if (!tokens.slice(assignment + 1, end).some(token => isError(token, i))) continue;
    let scopeEnd = tokens.length;
    for (const [open, close] of pairs) if (tokens[open].value === '{' && open < i && close > i && close < scopeEnd) scopeEnd = close;
    aliases.push({ name: tokens[i + 1].value, start: end - 1, end: scopeEnd, error: true });
  }

  for (let i = 0; i < tokens.length; i++) {
    const token = tokens[i];
    if (oldInlineApis.has(token.value)) {
      report(token, 'removed-inline-api', `${token.value} is forbidden, including imports, aliases and qualified references. Use independent feedback or a genuine full-page failure state.`);
    } else if (inlineComponentName(token.value) && tokens[i + 1]?.value === '(') {
      report(token, 'inline-error-component', `${token.value} recreates an inline error surface. Use MaFeiErrorDialog / MaFeiGlassConfirmDialog / LegacyTransientToast.`);
    }
    if (!renderers.has(token.value) || tokens[i + 1]?.value !== '(' || !pairs.has(i + 1) || tokens[i - 1]?.value === 'fun') continue;
    const args = argumentsOf(tokens, i + 1, pairs.get(i + 1), pairs);
    const textArgument = args.find(arg => ['text', 'value', 'content'].includes(arg[0]?.value) && arg[1]?.value === '=') ?? args[0];
    const errorText = textArgument.some(item => isError(item, i));
    const errorStyle = args.some(arg => arg[0]?.value === 'color' && arg.some(item => errorWord.test(item.value)));
    if (errorText || errorStyle) report(token, 'direct-error-text', 'Do not render failure feedback in content Text. Use standard independent feedback; narrowly document a full-page, dialog-internal, business-history or diagnostic rendering if applicable.', true);
  }
  for (const marker of markers.values()) if (!marker.used) violations.push({ line: marker.line, rule: 'unused-exception', message: `The ${marker.category} exception must immediately precede one otherwise-rejected text rendering. Remove stale or broad exceptions.` });
  return violations.sort((a, b) => a.line - b.line || a.rule.localeCompare(b.rule));
}

export function feedbackSources(root) {
  const sources = {};
  function walk(folder) {
    if (!existsSync(folder)) return;
    for (const entry of readdirSync(folder, { withFileTypes: true })) {
      const path = resolve(folder, entry.name);
      if (entry.isDirectory() && !excluded.test(entry.name)) walk(path);
      else if (entry.isFile() && /\.(?:kt|ets|ts|tsx|swift)$/.test(entry.name) && !/(?:Test|\.test|\.spec)\./i.test(entry.name) && path.includes('/src/')) {
        sources[relative(root, path).replaceAll('\\', '/')] = readFileSync(path, 'utf8');
      }
    }
  }
  for (const directory of roots) walk(resolve(root, directory));
  return sources;
}

export function verifyFeedbackPolicy(root) {
  const sources = feedbackSources(root);
  const violations = Object.entries(sources).flatMap(([file, source]) =>
    feedbackViolations(source, file).map(issue => `${file}:${issue.line} [${issue.rule}] ${issue.message}`));
  if (violations.length) throw Error(`Content-area error feedback is forbidden:\n${violations.map(issue => ` - ${issue}`).join('\n')}`);
  return `PASS: ${Object.keys(sources).length} active production sources comply with the feedback guard`;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { console.log(verifyFeedbackPolicy(process.argv[2] ?? resolve(dirname(fileURLToPath(import.meta.url)), '..'))); }
  catch (error) { console.error(error.message); process.exitCode = 1; }
}
