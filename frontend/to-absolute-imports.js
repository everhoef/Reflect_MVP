import fs from 'fs';
import path from 'path';

const srcDir = path.resolve('./src');

function getFiles(dir) {
  const files = [];
  const entries = fs.readdirSync(dir, { withFileTypes: true });
  for (const entry of entries) {
    if (entry.isDirectory()) {
      files.push(...getFiles(path.join(dir, entry.name)));
    } else if (entry.isFile() && (entry.name.endsWith('.ts') || entry.name.endsWith('.tsx'))) {
      files.push(path.join(dir, entry.name));
    }
  }
  return files;
}

const files = getFiles('./src');

files.forEach(file => {
  const filePath = path.resolve(file);
  let content = fs.readFileSync(filePath, 'utf8');
  let changed = false;

  const importRegex = /from\s+['"](\.[^'"]+)['"]/g;
  const dynamicImportRegex = /import\s*\(\s*['"](\.[^'"]+)['"]\s*\)/g;

  content = content.replace(importRegex, (match, relPath) => {
    const absolutePath = path.resolve(path.dirname(filePath), relPath);
    if (absolutePath.startsWith(srcDir)) {
      let newAlias = '@/' + path.relative(srcDir, absolutePath).replace(/\\/g, '/');
      changed = true;
      return `from '${newAlias}'`;
    }
    return match;
  });

  content = content.replace(dynamicImportRegex, (match, relPath) => {
    const absolutePath = path.resolve(path.dirname(filePath), relPath);
    if (absolutePath.startsWith(srcDir)) {
      let newAlias = '@/' + path.relative(srcDir, absolutePath).replace(/\\/g, '/');
      changed = true;
      return `import('${newAlias}')`;
    }
    return match;
  });

  if (changed) {
    fs.writeFileSync(filePath, content);
    console.log(`Updated ${file}`);
  }
});
