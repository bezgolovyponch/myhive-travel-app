import { Routes, Route } from 'react-router-dom';
import Header from './Header';
import ChatPanel from './ChatPanel';
import HomePage from '../pages/HomePage';
import DestinationPage from '../pages/DestinationPage';

function Layout() {
  return (
    <div className="app-container">
      <Header />
      <main>
        <Routes>
          <Route path="/" element={<HomePage />} />
          <Route path="/destination/:id" element={<DestinationPage />} />
        </Routes>
      </main>
      <ChatPanel />
    </div>
  );
}

export default Layout;
