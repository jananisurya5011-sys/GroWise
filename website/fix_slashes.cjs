const fs = require('fs');
const path = require('path');

const srcDir = 'c:/Projects/GroWise_Fullstack/website/src';

function fixSlashes(dir) {
    fs.readdirSync(dir).forEach(file => {
        const fullPath = path.join(dir, file);
        if (fs.statSync(fullPath).isDirectory()) {
            fixSlashes(fullPath);
        } else if (fullPath.endsWith('.jsx') || fullPath.endsWith('.js')) {
            let content = fs.readFileSync(fullPath, 'utf8');
            if (content.includes('formatCurrency') && fullPath !== path.join(srcDir, 'utils', 'constants.js')) {
                // Find the import
                const importRegex = /import\s+\{\s*formatCurrency\s*\}\s+from\s+['"](.*)['"]/g;
                let original = content;
                content = content.replace(importRegex, (match, p1) => {
                    const fixedPath = p1.replace(/\\\\/g, '/').replace(/\\/g, '/');
                    return 'import { formatCurrency } from \'' + fixedPath + '\'';
                });
                
                if (content !== original) {
                   fs.writeFileSync(fullPath, content);
                   console.log('Fixed slashes in: ' + fullPath);
                }
            }
        }
    });
}
fixSlashes(srcDir);
