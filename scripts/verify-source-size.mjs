import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const manifest = 'scripts/source-size-budgets.json';
const roots = ['core', 'providers', 'data', 'common', 'feature', 'player', 'platform', 'composeApp'];
const skipped = new Set(['build', 'vendor', 'node_modules', 'generated']);
export const lineCount = source => source.replace(/\n$/, '').split('\n').length;

export function sourceSizeViolations(sources, budgets, previous) {
  const failures = [];
  if (budgets.defaultMaxLines !== 800) failures.push('defaultMaxLines must remain 800');
  const limits = budgets.files ?? {};
  for (const [path, max] of Object.entries(limits)) {
    if (!Number.isInteger(max) || max <= 800) failures.push(`${path}: exception must be an integer above 800`);
    if (!(path in sources)) failures.push(`${path}: remove the budget for a removed or out-of-scope file`);
    if (previous && max > (previous.files?.[path] ?? previous.defaultMaxLines)) {
      failures.push(`${path}: budget cannot increase or add a new exception (${max})`);
    }
  }
  for (const [path, source] of Object.entries(sources)) {
    const lines = lineCount(source);
    const limit = limits[path] ?? 800;
    if (lines > limit) failures.push(`${path}: ${lines} lines exceeds ${limit}; split by responsibility, do not raise the budget`);
  }
  return failures;
}

export function productionSources(root) {
  const sources = {};
  function visit(folder) {
    if (!existsSync(folder)) return;
    for (const entry of readdirSync(folder, { withFileTypes: true })) {
      const file = resolve(folder, entry.name);
      if (entry.isDirectory() && !entry.name.startsWith('.') && !skipped.has(entry.name)) visit(file);
      if (entry.isFile() && entry.name.endsWith('.kt')) {
        const path = relative(root, file).replaceAll('\\', '/');
        if (/\/src\/[^/]*Main\//.test(path)) sources[path] = readFileSync(file, 'utf8');
      }
    }
  }
  for (const folder of roots) visit(resolve(root, folder));
  return sources;
}

function baseBudgets(root, ref) {
  if (!ref || /^0+$/.test(ref)) return undefined; // A newly created remote branch has no before commit.
  const commit = spawnSync('git', ['rev-parse', '--verify', `${ref}^{commit}`], { cwd: root, encoding: 'utf8' });
  if (commit.status !== 0) throw Error(`Cannot inspect size budget base ${ref}; fetch the base commit first`);
  const content = spawnSync('git', ['show', `${commit.stdout.trim()}:${manifest}`], { cwd: root, encoding: 'utf8' });
  if (content.status !== 0) {
    // The first adoption commit has no previous manifest; subsequent changes must ratchet down.
    const tree = spawnSync('git', ['ls-tree', commit.stdout.trim(), '--', manifest], { cwd: root, encoding: 'utf8' });
    if (tree.status === 0 && !tree.stdout.trim()) return undefined;
    throw Error(`Cannot read source size budgets at ${ref}`);
  }
  return JSON.parse(content.stdout);
}

export function verifySourceSize(root, ref = process.env.MAFEI_SIZE_BASE_REF ?? 'HEAD') {
  const sources = productionSources(root);
  const budgets = JSON.parse(readFileSync(resolve(root, manifest), 'utf8'));
  const failures = sourceSizeViolations(sources, budgets, baseBudgets(root, ref));
  if (failures.length) throw Error(failures.join('\n'));
  return `Source size: ${Object.keys(sources).length} production Kotlin files checked; ${Object.keys(budgets.files).length} existing exceptions`;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { console.log(verifySourceSize(process.argv[2] ?? resolve(dirname(fileURLToPath(import.meta.url)), '..'))); }
  catch (error) { console.error(error.message); process.exitCode = 1; }
}
