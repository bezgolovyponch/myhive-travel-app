import {useEffect, useRef, useState} from 'react';
import {useLocation} from 'react-router-dom';
import {SUPPORTED_LOCALES, localizeHref, useLocale, useT} from '../i18n';
import './LanguageSwitcher.css';

// Native names, not translations: a German user lost on the English site must
// still recognize "Deutsch".
const LOCALE_LABELS = {en: 'English', de: 'Deutsch'};

function LanguageSwitcher() {
    const t = useT('header');
    const locale = useLocale();
    const location = useLocation();
    const [open, setOpen] = useState(false);
    const rootRef = useRef(null);

    useEffect(() => {
        if (!open) return undefined;
        const onPointerDown = (e) => {
            if (rootRef.current && !rootRef.current.contains(e.target)) {
                setOpen(false);
            }
        };
        const onKeyDown = (e) => {
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

    // location.pathname is locale-free in both worlds: LegacyRouter strips the
    // prefix on the Next side, and the standalone CRA never has one. Plain <a>
    // on purpose — switching locale must be a full load so the server renders
    // the other language (and SPA-owned paths simply stay unprefixed).
    const path = location.pathname + location.search;

    return (
        <div className="lang-switcher" ref={rootRef}>
            <button
                type="button"
                className="lang-switcher-btn"
                aria-label={t('languageAria')}
                aria-expanded={open}
                onClick={() => setOpen(!open)}
            >
                <i className="ph ph-globe" aria-hidden="true"/>
                <span className="lang-switcher-code">{locale.toUpperCase()}</span>
                <i className={`ph ph-caret-down lang-switcher-caret ${open ? 'open' : ''}`} aria-hidden="true"/>
            </button>
            {open && (
                <div className="lang-switcher-menu">
                    {SUPPORTED_LOCALES.map((l) =>
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
                        )
                    )}
                </div>
            )}
        </div>
    );
}

export default LanguageSwitcher;
