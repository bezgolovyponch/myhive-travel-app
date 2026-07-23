// Shared chrome for the SSR public pages: same DOM shape as the legacy SPA's
// Layout.js (.app-container > Header > main > Footer) so global CSS applies.
// A component (not only a route-group layout) so pages with an SPA escape
// hatch can render chrome-less — the CRA tree mounts its own Header/Footer.
import 'bootstrap/dist/css/bootstrap.min.css';
import '../../legacy-src/styles/global.css';
import Header from './Header';
import Footer from './Footer';

export default function PublicChrome({ children }: { children: React.ReactNode }) {
  return (
    <div className="app-container">
      <Header />
      <main>{children}</main>
      <Footer />
    </div>
  );
}
