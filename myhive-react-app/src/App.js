import './App.css';
import { BrowserRouter as Router } from 'react-router-dom';
import { AppProvider } from './context/AppContext';
import Layout from './components/Layout';

function App() {
  return (
    <Router>
      <AppProvider>
        <Layout />
      </AppProvider>
    </Router>
  );
}

export default App;
