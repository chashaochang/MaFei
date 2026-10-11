import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { resolve, relative, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

export function boundaryViolations(module, source) {
  const code = source.replace(/\/\*[\s\S]*?\*\//g, '').replace(/\/\/[^\n]*/g, '');
  const references = [...code.matchAll(/cn\.mafei\.cmp\.(core|common|feature|providers|platform|data|session)(?:\.([a-zA-Z_]+))?/g)]
    .map(match => ({ group: match[1], sub: match[2], value: match[0] }));
  return [...new Set(references.filter(({ group, sub }) => {
    if (module.startsWith(':core:')) return ['feature', 'providers', 'data', 'session', 'platform'].includes(group) || group === 'common' && ['ui', 'glass', 'design_system', 'media'].includes(sub);
    if (module.startsWith(':providers:')) return ['feature', 'data', 'session'].includes(group);
    if (module.startsWith(':feature:')) return ['providers', 'platform', 'session'].includes(group);
    if ([':common:ui', ':common:design-system', ':common:media', ':common:glass'].includes(module)) return ['feature', 'data', 'providers', 'session'].includes(group);
    if ([':player:api', ':player:core'].includes(module)) return ['feature', 'data', 'providers', 'platform', 'session'].includes(group);
    return false;
  }).map(item => item.value))];
}

function sources(folder) {
  if (!existsSync(folder)) return [];
  return readdirSync(folder, { withFileTypes: true }).flatMap(entry => {
    const path = resolve(folder, entry.name);
    if (entry.isDirectory()) return /test/i.test(entry.name) ? [] : sources(path);
    return entry.name.endsWith('.kt') ? [path] : [];
  });
}

export function verifyDependencyBoundaries(root) {
  const settings = readFileSync(resolve(root, 'settings.gradle.kts'), 'utf8');
  const modules = [...settings.matchAll(/"(:[a-zA-Z0-9-]+(?::[a-zA-Z0-9-]+)*)"/g)].map(it => it[1]);
  const failures = [];
  let count = 0;
  for (const module of modules) {
    const directory = resolve(root, module.slice(1).replaceAll(':', '/'));
    for (const file of sources(resolve(directory, 'src'))) {
      count++;
      for (const target of boundaryViolations(module, readFileSync(file, 'utf8'))) failures.push(`${relative(root, file)}: forbidden ${target}`);
    }
  }
  if (failures.length) throw Error(failures.join('\n'));
  return `Dependency boundaries: ${count} production Kotlin sources checked`;
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  try { console.log(verifyDependencyBoundaries(process.argv[2] ?? resolve(dirname(fileURLToPath(import.meta.url)), '..'))); }
  catch (error) { console.error(error.message); process.exitCode = 1; }
}
