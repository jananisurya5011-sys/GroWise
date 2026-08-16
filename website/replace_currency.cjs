const fs = require('fs');
const path = require('path');

const srcDir = 'c:/Projects/GroWise_Fullstack/website/src';

function findAndReplace(dir) {
    fs.readdirSync(dir).forEach(file => {
        const fullPath = path.join(dir, file);
        if (fs.statSync(fullPath).isDirectory()) {
            findAndReplace(fullPath);
        } else if (fullPath.endsWith('.jsx') || fullPath.endsWith('.js')) {
            let content = fs.readFileSync(fullPath, 'utf8');
            if (content.includes('₹') && fullPath !== path.join(srcDir, 'utils', 'constants.js')) {
                let original = content;
                
                content = content.replace(/₹\s*\{([^}]+)\}/g, '{formatCurrency($1)}');
                content = content.replace(/₹\s*\$\{([^}]+)\}/g, '${formatCurrency($1)}');
                
                if (content !== original) {
                   const relPath = path.relative(path.dirname(fullPath), srcDir).replace(/\\/g, '/');
                   const importPath = relPath === '' ? './utils/constants' : relPath + '/utils/constants';
                   
                   const importStmt = 'import { formatCurrency } from \'' + importPath + '\';\n';
                   
                   if (!content.includes('formatCurrency')) {
                       const lastImportIndex = content.lastIndexOf('import ');
                       if (lastImportIndex !== -1) {
                           const endOfLastImport = content.indexOf('\n', lastImportIndex);
                           content = content.slice(0, endOfLastImport + 1) + importStmt + content.slice(endOfLastImport + 1);
                       } else {
                           content = importStmt + content;
                       }
                   }
                   
                   fs.writeFileSync(fullPath, content);
                   console.log('Updated: ' + fullPath);
                }
            }
        }
    });
}
findAndReplace(srcDir);
