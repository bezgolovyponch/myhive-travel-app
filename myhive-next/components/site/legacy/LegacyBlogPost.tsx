'use client';

// Client boundary for the canonical CRA blog post. RouteMatch re-declares the CRA
// route so useParams() resolves the slug; the post itself is supplied by the
// server so the article body is in the initial HTML.
import { useLocale } from '../../../legacy-src/i18n';
import { routePath } from '../../../legacy-src/i18n/routes';
import BlogPostPage from '../../../legacy-src/pages/BlogPostPage';
import RouteMatch from './RouteMatch';

export default function LegacyBlogPost({ post }: { post: unknown }) {
  const locale = useLocale();
  return (
    <RouteMatch pattern={routePath('/ideas/:slug', locale)}>
      <BlogPostPage post={post} />
    </RouteMatch>
  );
}
