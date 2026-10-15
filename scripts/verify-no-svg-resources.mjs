import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { resolve, relative, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

function collectSvgFiles(directory, files) {
  if (!existsSync(directory)) return;
  for (const entry of readdirSync(directory, { withFileTypes: true })) {
    const path = resolve(directory, entry.name);
    if (entry.isDirectory()) collectSvgFiles(path, files);
    else if (entry.isFile() && entry.name.toLowerCase().endsWith('.svg')) files.push(path);
  }
}

const root = resolve(process.argv[2] ?? resolve(dirname(fileURLToPath(import.meta.url)), '..'));
const settings = readFileSync(resolve(root, 'settings.gradle.kts'), 'utf8');
const modules = [...settings.matchAll(/"(:[a-zA-Z0-9-]+(?::[a-zA-Z0-9-]+)*)"/g)]
  .map(match => match[1]);
const offenders = [];

for (const module of new Set(modules)) {
  const moduleRoot = resolve(root, module.slice(1).replaceAll(':', '/'));
  collectSvgFiles(resolve(moduleRoot, 'src'), offenders);
}

if (offenders.length > 0) {
  const list = offenders
    .map(path => relative(root, path).replaceAll('\\', '/'))
    .sort()
    .map(path => ` - ${path}`)
    .join('\n');
  console.error(`SVG resources are forbidden in active CMP modules. Convert them to PNG:\n${list}`);
  process.exitCode = 1;
} else {
  console.log(`PASS: ${new Set(modules).size} active CMP modules contain no SVG source resources`);
}
