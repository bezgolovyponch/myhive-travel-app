# F1 SSR smoke checks (spec §11): raw HTML contains content + unique meta;
# unknown slugs return HTTP 404; robots/sitemap live; legacy fallback intact.
import re
import urllib.request
import urllib.error
import sys

BASE = sys.argv[1] if len(sys.argv) > 1 else 'http://localhost:3100'
fails = []

def fetch(path):
    try:
        with urllib.request.urlopen(BASE + path, timeout=15) as r:
            return r.status, r.read().decode('utf-8', 'replace')
    except urllib.error.HTTPError as e:
        return e.code, e.read().decode('utf-8', 'replace')

def check(path, *needles):
    code, html = fetch(path)
    h1 = len(re.findall(r'<h1[\s>]', html))
    title = re.search(r'<title>([^<]*)</title>', html)
    title = title.group(1) if title else '(none)'
    ok_meta = code == 200 and h1 == 1
    line = f"== {path} [{code}] h1x{h1} | {title[:70]}"
    print(line)
    if code != 200: fails.append(f"{path}: HTTP {code}")
    if h1 != 1: fails.append(f"{path}: {h1} h1 tags")
    for n in needles:
        if n in html:
            print(f"   OK  {n}")
        else:
            print(f"   MISS {n}")
            fails.append(f"{path}: missing {n!r}")

def expect_404(path):
    code, _ = fetch(path)
    print(f"== {path} -> {code} {'OK' if code == 404 else 'EXPECTED 404'}")
    if code != 404: fails.append(f"{path}: expected 404 got {code}")

check('/', 'The Easiest Stag Do Decision', 'Why Plan Your Prague', 'application/ld+json')
check('/destination/prague', 'Prague Stag Do', 'application/ld+json', 'activity-card')
check('/blog', 'Stag Do')
check('/about', 'Trivlu')
check('/contact', 'info@trivlu.com')
check('/terms', 'Terms')
check('/privacy-policy', 'Privacy')
check('/cookie-policy', 'Cookie')
check('/refund-policy', 'Refund')
expect_404('/destination/atlantis')
expect_404('/blog/not-a-real-post')
expect_404('/destination/prague/activity/not-a-real-activity')

code, robots = fetch('/robots.txt')
print(f"== /robots.txt [{code}]: {robots.strip()[:80]}")
code, sm = fetch('/sitemap.xml')
n_urls = sm.count('<url>')
print(f"== /sitemap.xml [{code}]: {n_urls} urls; first: {sm[sm.find('<loc>'):sm.find('<loc>')+70]}")
if n_urls < 10: fails.append(f"sitemap only {n_urls} urls")

for p in ('/admin', '/vote/new'):
    code, html = fetch(p)
    shim = 'Loading' in html
    print(f"== {p} [{code}] legacy-shim={'yes' if shim else 'NO'}")
    if code != 200 or not shim: fails.append(f"{p}: legacy fallback broken")

print('\nRESULT:', 'PASS' if not fails else f"{len(fails)} FAILURES")
for f in fails: print('  -', f)
