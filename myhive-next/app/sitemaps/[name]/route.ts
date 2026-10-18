import { sitemapResponse } from '../../../lib/sitemaps';

export const revalidate = 3600;

export async function GET(
  _request: Request,
  { params }: { params: Promise<{ name: string }> },
): Promise<Response> {
  const { name } = await params;
  return sitemapResponse(name, 'en');
}
