import { existsSync, readdirSync, readFileSync } from 'node:fs';
import { relative, resolve } from 'node:path';

const root = resolve(process.argv[2] ?? process.cwd());
const scanRoots = ['common', 'feature', 'player', 'composeApp']
  .map((path) => resolve(root, path))
  .filter(existsSync);
const ignoredFiles = new Set([
  'composeApp/src/sharedUiMain/kotlin/cn/mafei/cmp/FoundationProbeScreen.kt',
]);

// These are visual Material widgets. MaterialTheme, Text and typography remain
// valid tokens; product feedback and surfaces must come from MaFei components.
const forbidden = [
  { name: 'CircularProgressIndicator', pattern: /\bCircularProgressIndicator\s*\(/g },
  { name: 'LinearProgressIndicator', pattern: /\bLinearProgressIndicator\s*\(/g },
  { name: 'PullToRefreshBox', pattern: /\bPullToRefreshBox\s*\(/g },
  { name: 'Button', pattern: /\b(?:Button|OutlinedButton|ElevatedButton|FilledTonalButton|TextButton)\s*\(/g },
  { name: 'Card', pattern: /\b(?:Card|OutlinedCard|ElevatedCard)\s*\(/g },
  { name: 'Chip', pattern: /\b(?:FilterChip|AssistChip|InputChip|SuggestionChip)\s*\(/g },
  { name: 'Surface', pattern: /\bSurface\s*\(/g },
];

function collectKotlin(directory, files) {
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = resolve(directory, entry.name);
    if (entry.isDirectory()) {
      collectKotlin(path, files);
    } else if (
      entry.isFile() &&
      entry.name.endsWith('.kt') &&
      path.includes('/src/sharedUiMain/') &&
      !ignoredFiles.has(relative(root, path))
    ) {
      files.push(path);
    }
  }
}

const files = [];
for (const directory of scanRoots) collectKotlin(directory, files);
const offenders = [];

for (const path of files) {
  const source = readFileSync(path, 'utf8');
  for (const rule of forbidden) {
    rule.pattern.lastIndex = 0;
    let match;
    while ((match = rule.pattern.exec(source)) !== null) {
      const line = source.slice(0, match.index).split('\n').length;
      offenders.push({ path: relative(root, path).replaceAll('\\', '/'), line, name: rule.name });
    }
  }
}

if (offenders.length > 0) {
  offenders.sort((a, b) => a.path.localeCompare(b.path) || a.line - b.line);
  console.error('Native Material visual widgets are forbidden in active CMP UI. Use MaFei components:');
  for (const offender of offenders) {
    console.error(` - ${offender.path}:${offender.line} (${offender.name})`);
  }
  process.exitCode = 1;
} else {
  console.log(`PASS: scanned ${files.length} active CMP UI Kotlin files; no native Material visual widgets found`);
}
