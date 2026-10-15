import { readFileSync, readdirSync } from 'node:fs';
import { dirname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ui = 'composeApp/src/sharedUiMain/kotlin/cn/mafei/cmp/';
const home = 'feature/home/src/commonMain/kotlin/cn/mafei/cmp/feature/home/';
const controllers = ['CatalogBrowser', 'SearchBrowser', 'FavoriteBrowser', 'DetailBrowser',
  'PlaybackSelectionBrowser', 'PersonBrowser', 'HistoryBrowser', 'MediaActionsBrowser'];

// Preserve newlines for diagnostics. Strings cannot introduce dependencies; imports and code can.
export function kotlinCode(source) {
  return source.replace(/\/\*[\s\S]*?\*\/|\/\/[^\n]*|"""[\s\S]*?"""|"(?:\\.|[^"\\])*"|'(?:\\.|[^'\\])*'/g,
    value => value.replace(/[^\n]/g, ' '));
}

export function appBoundaryViolations(path, source) {
  const code = kotlinCode(source);
  const failures = [];
  const reject = (pattern, message) => { if (pattern.test(code)) failures.push(message); };
  const budget = max => {
    // Count non-empty code lines; exclude comments, literals and generated resources elsewhere.
    const lines = code.split('\n').filter(line => line.trim()).length;
    if (lines > max) failures.push(`responsibility budget exceeded (${lines}/${max} code lines)`);
  };
  if (path === `${ui}App.kt`) {
    budget(180);
    reject(/\b(?:NavEntry|NavDisplay|MediaBrowser|CoroutineScope)\b|\blaunch\s*[{(]/, 'App must only assemble root flows');
    reject(/\b(?:class|interface|object)\s|\bfun\s+(?!App\s*\()/, 'App must not define route, state or helper implementations');
    reject(/cn\.mafei\.cmp\.feature\.|runtime\.(?:connect|savedConnections|switchAccount|deleteSavedConnection)\b|runtime\.sessions\.media\b/,
      'business access belongs to a flow owner or entry binding');
  }
  if (path === `${ui}browse/BrowseApplication.kt`) {
    reject(/\b[A-Z]\w*Screen\s*\(/, 'browse assembly must call entry bindings instead of product screens');
    reject(/runtime\.(?:connect|switchAccount|deleteSavedConnection|saveSearchHistory)\s*\(/,
      'browse assembly must delegate account/search mutations to their owner');
  }
  if ([`${ui}navigation/BrowseRoutes.kt`, `${ui}navigation/SignedOutRoutes.kt`, `${ui}navigation/BrowseShellMapping.kt`].includes(path)) {
    reject(/\b(?:ApplicationRuntime|MediaBrowser|CoroutineScope|LaunchedEffect|MutableStateFlow|remember\w*|mutableState\w*)\b|\w+Repository\b/,
      'route contracts and shell mappings must remain pure');
  }
  if (path.startsWith(ui) && path.endsWith('EntryBinding.kt')) {
    reject(/\b(?:ApplicationRuntime|LocalApplicationRuntime)\b/, 'entry bindings must receive explicit capabilities, stores and events');
    reject(/cn\.mafei\.cmp\.providers\./, 'entry bindings cannot access provider SDKs');
  }
  if (path === `${home}MediaBrowser.kt`) {
    budget(140);
    reject(/\b(?:MutableStateFlow|MediaPageAccumulator|launch|async)\b|\.copy\s*\(|repository\s*\./,
      'MediaBrowser is a compatibility facade; request and mutation logic belongs to controllers');
  }
  if (path.startsWith(`${home}browser/`) && path.endsWith('.kt')) {
    reject(/androidx\.compose\.|androidx\.navigation|\bApplicationRuntime\b|cn\.mafei\.cmp\.(?:providers|platform|player)\./,
      'browser controllers cannot depend on UI, platform hosts or provider SDKs');
    if (!path.endsWith('BrowserContext.kt')) {
      reject(/\bMutableStateFlow\b/, 'browser controllers must publish through the one shared read model');
    }
    const own = controllers.find(name => path.endsWith(`/${name}.kt`));
    if (own) {
      budget(500);
      for (const other of controllers.filter(name => name !== own)) {
        reject(new RegExp(`\\b${other}\\b`), `controller ${own} must not capture sibling ${other}`);
      }
    }
  }
  return failures;
}

function files(folder) {
  return readdirSync(folder, { withFileTypes: true }).flatMap(entry => {
    const file = resolve(folder, entry.name);
    return entry.isDirectory() ? files(file) : entry.name.endsWith('.kt') ? [file] : [];
  });
}

export function verifyAppBoundaries(root) {
  const failures = [];
  const paths = [...files(resolve(root, ui)), ...files(resolve(root, home))];
  for (const path of paths) {
    const name = relative(root, path).replaceAll('\\', '/');
    for (const message of appBoundaryViolations(name, readFileSync(path, 'utf8'))) failures.push(`${name}: ${message}`);
  }
  if (failures.length) throw Error(failures.join('\n'));
  return `App boundaries: ${paths.length} Kotlin files checked`;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { console.log(verifyAppBoundaries(process.argv[2] ?? resolve(dirname(fileURLToPath(import.meta.url)), '..'))); }
  catch (error) { console.error(error.message); process.exitCode = 1; }
}
