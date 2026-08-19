import {createContext, useContext} from 'react';
import {Helmet} from 'react-helmet-async';

// Wrapper around Helmet that a host app can switch off.
//
// react-helmet-async 3's React19Dispatcher renders real <title>/<meta>/<link>
// elements into the tree and lets React 19 hoist them into <head>. Under Next.js
// App Router that duplicates whatever the route's `metadata` export already
// emitted — two titles, two descriptions, two canonicals — and on streamed
// (dynamic) routes it left the <head> title empty while the real one landed in
// the body. Next owns the head there, so the SSR chrome provides `false` and
// these pages emit nothing; the SPA leaves the default `true` and behaves
// exactly as before.
export const PageHeadEnabledContext = createContext(true);

function PageHead({children}) {
    const enabled = useContext(PageHeadEnabledContext);
    if (!enabled) {
        return null;
    }
    return <Helmet>{children}</Helmet>;
}

export default PageHead;
