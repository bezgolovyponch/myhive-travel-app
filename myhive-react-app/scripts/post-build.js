const fs = require('fs');
const path = require('path');

const buildDir = path.join(__dirname, '..', 'build');
const indexHtml = path.join(buildDir, 'index.html');

// All client-side routes that need to work on direct navigation
const routes = [
    'admin',
    'admin/login',
    'admin/bookings',
];

const indexContent = fs.readFileSync(indexHtml, 'utf8');

routes.forEach(route => {
    const dir = path.join(buildDir, route);
    fs.mkdirSync(dir, {recursive: true});

    const target = path.join(dir, 'index.html');
    // Don't overwrite if the route dir itself is the build root
    if (target !== indexHtml) {
        fs.writeFileSync(target, indexContent, 'utf8');
        console.log(`Created: ${route}/index.html`);
    }
});

console.log('Post-build: SPA route files created successfully.');
