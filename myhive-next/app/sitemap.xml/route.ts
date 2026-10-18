import { indexItems, loadCatalog, sitemapIndexXml, xmlResponse } from '../../lib/sitemaps';

export const revalidate = 3600;

export async function GET(): Promise<Response> {
  return xmlResponse(sitemapIndexXml(indexItems(await loadCatalog())));
}
