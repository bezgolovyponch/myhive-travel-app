// Shared chrome for the SSR public pages: same DOM shape as the legacy SPA's
// Layout.js (.app-container > Header > main > Footer) so global CSS applies.
import 'bootstrap/dist/css/bootstrap.min.css';
import '../../legacy-src/styles/global.css';
import Header from '../../components/site/Header';
import Footer from '../../components/site/Footer';

export default function PublicLayout({ children }: { children: React.ReactNode }) {
  return (
    <div className="app-container">
      <Header />
      <main>{children}</main>
      <Footer />
    </div>
  );
}
