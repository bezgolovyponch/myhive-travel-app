const fs = require('fs');
const path = require('path');

const buildDir = path.join(__dirname, '..', 'build');
const indexHtml = path.join(buildDir, 'index.html');

// All client-side routes that need to work on direct navigation.
// Render Static Sites resolve /path to path.html (clean URLs).
// Do NOT create directory/index.html — Render won't resolve those without trailing slash.
const routes = [
    'admin',
    'admin/login',
    'admin/bookings',
];

const indexContent = fs.readFileSync(indexHtml, 'utf8');

routes.forEach(route => {
    // Ensure parent directories exist for nested routes
    const parentDir = path.dirname(path.join(buildDir, route));
    fs.mkdirSync(parentDir, {recursive: true});

    // Create flat .html file: /admin/login → admin/login.html
    const target = path.join(buildDir, route + '.html');
    fs.writeFileSync(target, indexContent, 'utf8');
    console.log(`Created: ${route}.html`);
});

console.log('Post-build: SPA route files created successfully.');
