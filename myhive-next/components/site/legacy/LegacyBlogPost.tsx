'use client';

// Client boundary for the canonical CRA blog post. RouteMatch re-declares the CRA
// route so useParams() resolves the slug; the post itself is supplied by the
// server so the article body is in the initial HTML.
import BlogPostPage from '../../../legacy-src/pages/BlogPostPage';
import RouteMatch from './RouteMatch';

export default function LegacyBlogPost({ post }: { post: unknown }) {
  return (
    <RouteMatch pattern="/blog/:slug">
      <BlogPostPage post={post} />
    </RouteMatch>
  );
}
