import ReactMarkdown from 'react-markdown';
import remarkGfm from 'remark-gfm';

// Single markdown renderer for blog content — consumed by the Next SSR blog
// page (via the legacy-src sync) and by the admin editor preview, so what the
// editor previews is exactly what production serves. Server-component safe:
// no hooks, no browser APIs. Raw HTML in content is NOT rendered as HTML
// (react-markdown ignores html nodes without rehype-raw) and URLs go through
// react-markdown's default sanitizer (http/https/mailto/tel + relative only).
export default function MarkdownContent({children}) {
    return <ReactMarkdown remarkPlugins={[remarkGfm]}>{children || ''}</ReactMarkdown>;
}
