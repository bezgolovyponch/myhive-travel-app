// SSR blog hub — a thin server shell: metadata plus the post list. The markup is
// the canonical CRA page (legacy-src/pages/BlogPage.js), which fetches in an
// effect no crawler runs, so the posts are injected instead.
import { api } from '../../../lib/api';
import { pageMetadata } from '../../../lib/seo';
import LegacyBlog from '../../../components/site/legacy/LegacyBlog';

export const revalidate = 3600;

const TITLE = 'Stag Do Planning Guides & Ideas | Trivlu Blog';
const DESCRIPTION =
  'Guides, ideas and destination tips for planning the perfect stag do — from budgeting to the best cities in Europe.';

export const metadata = pageMetadata({
  title: TITLE,
  description: DESCRIPTION,
  path: '/blog',
});

export default async function Page() {
  const posts = (await api.getBlogPosts().catch(() => null)) ?? [];

  return <LegacyBlog posts={posts} />;
}
