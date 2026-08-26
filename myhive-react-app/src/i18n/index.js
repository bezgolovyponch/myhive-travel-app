// Minimal i18n for the shared legacy components. Deliberately NOT a framework
// library: the same sources render inside Next (which owns locale routing via
// next-intl) and inside the standalone CRA (rollback build, English-only).
// Without a provider the context defaults to English, so the CRA app and every
// existing test keep working untouched; the Next [locale] layout mounts
// LocaleProvider to switch languages.
//
// Message syntax is ICU-compatible on purpose ({name} interpolation only), so
// the JSON files can later feed next-intl unchanged when components migrate.
import { createContext, useCallback, useContext } from 'react';
import en from './messages/en.json';
import { DEFAULT_LOCALE, bindSsrLocale, localizePath } from './routes';

export { DEFAULT_LOCALE, SUPPORTED_LOCALES, localizePath, localizeHref, splitLocale } from './routes';

const LocaleContext = createContext({ locale: DEFAULT_LOCALE, messages: en });

export function LocaleProvider({ locale, messages, children }) {
  // Server renders have no URL to read the locale from — bind it for the
  // hook-free helpers (currentLocale and the formatters built on it) so the
  // initial HTML matches what the client hydrates. See routes.js.
  bindSsrLocale(locale);
  return (
    <LocaleContext.Provider value={{ locale, messages: messages || en }}>
      {children}
    </LocaleContext.Provider>
  );
}

export function useLocale() {
  return useContext(LocaleContext).locale;
}

function lookup(messages, path) {
  let node = messages;
  for (const part of path.split('.')) {
    if (node == null || typeof node !== 'object') return undefined;
    node = node[part];
  }
  return node;
}

function format(template, vars) {
  if (!vars) return template;
  return template.replace(/\{(\w+)\}/g, (match, name) =>
    Object.prototype.hasOwnProperty.call(vars, name) ? String(vars[name]) : match
  );
}

/**
 * `const t = useT('header'); t('nav.about')` — dot-path lookup inside the
 * namespace, falling back to English, then to the key path itself so a missing
 * translation is visible instead of a crash.
 */
export function useT(namespace) {
  const { messages } = useContext(LocaleContext);
  return useCallback(
    (key, vars) => {
      const path = namespace ? `${namespace}.${key}` : key;
      let value = lookup(messages, path);
      if (typeof value !== 'string') value = lookup(en, path);
      if (typeof value !== 'string') return path;
      return format(value, vars);
    },
    [messages, namespace]
  );
}

/** Locale-prefix an internal path for plain hrefs: useLocalePath()('/about'). */
export function useLocalePath() {
  const locale = useLocale();
  return useCallback((path) => localizePath(path, locale), [locale]);
}
