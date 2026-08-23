'use client';

// Client boundary for the legacy LocaleContext. The [locale] layout (a Server
// Component) resolves the messages for the request; this hands them to the
// legacy components' useT hook. Without this provider those components default
// to English — which is exactly what the standalone CRA build and its tests do.
import { LocaleProvider } from '../legacy-src/i18n';

export default function LegacyLocaleProvider({
  locale,
  messages,
  children,
}: {
  locale: string;
  messages: Record<string, unknown>;
  children: React.ReactNode;
}) {
  return (
    <LocaleProvider locale={locale} messages={messages}>
      {children}
    </LocaleProvider>
  );
}
