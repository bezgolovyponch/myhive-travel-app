'use client';

// Client boundary for the canonical CRA blog listing. Posts come from the server
// so the listing is in the initial HTML rather than arriving in an effect.
import BlogPage from '../../../legacy-src/pages/BlogPage';

export default function LegacyBlog({ posts }: { posts: unknown[] }) {
  return <BlogPage posts={posts} />;
}
