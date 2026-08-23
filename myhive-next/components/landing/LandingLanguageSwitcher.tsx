'use client';

// The same language dropdown the main homepage header uses
// (legacy-src/components/LanguageSwitcher.js): globe pill, caret, native
// language names in the menu. Re-implemented here because the legacy component
// needs react-router's useLocation and the legacy global stylesheet, neither
// of which exists on the landing routes — markup, classes and visuals are
// kept identical (styles ported into landing.css under .tl).
import { useEffect, useRef, useState } from 'react';
import { usePathname } from 'next/navigation';
import { useT, useLocale, splitLocale, localizeHref, SUPPORTED_LOCALES } from '../../legacy-src/i18n';

// Native names, not translations: a German user lost on the English site must
// still recognize "Deutsch".
const LOCALE_LABELS: Record<string, string> = { en: 'English', de: 'Deutsch' };

export default function LandingLanguageSwitcher() {
  const t = useT('header');
  const locale = useLocale();
  const pathname = usePathname() ?? '/';
  const [open, setOpen] = useState(false);
  const rootRef = useRef<HTMLDivElement>(null);

  useEffect(() => {
    if (!open) return undefined;
    const onPointerDown = (e: PointerEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    const onKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        setOpen(false);
      }
    };
    document.addEventListener('pointerdown', onPointerDown);
    document.addEventListener('keydown', onKeyDown);
    return () => {
      document.removeEventListener('pointerdown', onPointerDown);
      document.removeEventListener('keydown', onKeyDown);
    };
  }, [open]);

  // usePathname keeps the locale prefix (unlike LegacyRouter's stripped
  // location), so drop it before re-prefixing. No useSearchParams: it would
  // force a Suspense/CSR bailout on these statically prerendered pages, and
  // landing URLs carry no query state worth preserving. Plain <a> on purpose —
  // switching locale must be a full load so the server renders the other
  // language.
  const path = splitLocale(pathname).pathname;

  return (
    <div className="lang-switcher" ref={rootRef}>
      <button
        type="button"
        className="lang-switcher-btn"
        aria-label={t('languageAria')}
        aria-expanded={open}
        onClick={() => setOpen(!open)}
      >
        <i className="ph ph-globe" aria-hidden="true" />
        <span className="lang-switcher-code">{locale.toUpperCase()}</span>
        <i className={`ph ph-caret-down lang-switcher-caret ${open ? 'open' : ''}`} aria-hidden="true" />
      </button>
      {open && (
        <div className="lang-switcher-menu">
          {SUPPORTED_LOCALES.map((l: string) =>
            l === locale ? (
              <span key={l} className="lang-switcher-item current" aria-current="true">
                {LOCALE_LABELS[l] || l}
              </span>
            ) : (
              <a
                key={l}
                className="lang-switcher-item"
                href={localizeHref(path, l)}
                lang={l}
                hrefLang={l}
              >
                {LOCALE_LABELS[l] || l}
              </a>
            ),
          )}
        </div>
      )}
    </div>
  );
}
