import React from 'react';
import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

// Mock react-router-dom because Jest 27's resolver does not honor the
// `exports` field used by react-router-dom v7. We provide just enough of
// the API for QuizPage and a minimal Routes/Route harness.
jest.mock('react-router-dom', () => {
  const reactLib = require('react');
  const state = { pathname: '/', locationState: null, params: {}, listeners: [] };
  const notify = () => state.listeners.forEach((fn) => fn());

  const findElement = (children) => {
    const routes = {};
    reactLib.Children.forEach(children, (child) => {
      if (child && child.props && child.props.path) {
        routes[child.props.path] = child.props.element;
      }
    });
    if (routes[state.pathname]) {
      state.params = {};
      return routes[state.pathname];
    }
    for (const pattern of Object.keys(routes)) {
      const patternParts = pattern.split('/');
      const pathParts = state.pathname.split('/');
      if (patternParts.length !== pathParts.length) {
        continue;
      }
      const params = {};
      let matched = true;
      for (let i = 0; i < patternParts.length; i += 1) {
        if (patternParts[i].startsWith(':')) {
          params[patternParts[i].slice(1)] = pathParts[i];
        } else if (patternParts[i] !== pathParts[i]) {
          matched = false;
          break;
        }
      }
      if (matched) {
        state.params = params;
        return routes[pattern];
      }
    }
    return null;
  };

  return {
    __esModule: true,
    MemoryRouter: ({ initialEntries, children }) => {
      const entry = initialEntries[0];
      if (typeof entry === 'string') {
        state.pathname = entry;
        state.locationState = null;
      } else {
        state.pathname = entry.pathname;
        state.locationState = entry.state || null;
      }
      state.params = {};
      return reactLib.createElement(reactLib.Fragment, null, children);
    },
    Routes: ({ children }) => {
      const [, setTick] = reactLib.useState(0);
      const registered = reactLib.useRef(false);
      if (!registered.current) {
        registered.current = true;
        state.listeners.push(() => setTick((n) => n + 1));
      }
      return findElement(children);
    },
    Route: () => null,
    useNavigate: () => (to) => {
      state.pathname = to;
      state.params = {};
      notify();
    },
    useLocation: () => ({ pathname: state.pathname, state: state.locationState }),
    useParams: () => state.params,
  };
}, { virtual: true });

// eslint-disable-next-line import/first
import { MemoryRouter, Route, Routes } from 'react-router-dom';
// eslint-disable-next-line import/first
import QuizPage from './QuizPage';
// eslint-disable-next-line import/first
import voteApi from '../../services/voteApi';

jest.mock('../../services/voteApi');

function renderOrganizer(setup) {
  return render(
    <MemoryRouter initialEntries={[{ pathname: '/vote/new/quiz', state: { setup } }]}>
      <Routes>
        <Route path="/vote/new/quiz" element={<QuizPage />} />
        <Route path="/vote/new/curate" element={<div>curate page</div>} />
        <Route path="/" element={<div>home</div>} />
      </Routes>
    </MemoryRouter>
  );
}

test('organizer: loads quiz, answers question, navigates to curate', async () => {
  voteApi.getPublicQuizForDestination.mockResolvedValue({
    questions: [
      { id: 'q1', prompt: 'Daytime or 4am?', answers: [
        { id: 'a1', label: 'Daytime' },
        { id: 'a2', label: '4am' },
      ] },
    ],
  });
  renderOrganizer({ destination: { id: 'dest1' }, travelers: 2, startDate: '2026-08-01', endDate: '2026-08-10', email: 'a@b.c', budget: 3000 });

  expect(await screen.findByText('Daytime or 4am?')).toBeInTheDocument();
  await userEvent.click(screen.getByText('4am'));

  expect(await screen.findByText('curate page')).toBeInTheDocument();
});

test('organizer: empty quiz auto-skips to curate', async () => {
  voteApi.getPublicQuizForDestination.mockResolvedValue({ questions: [] });
  renderOrganizer({ destination: { id: 'dest1' } });

  expect(await screen.findByText('curate page')).toBeInTheDocument();
});

test('organizer: no setup state → redirects home', async () => {
  render(
    <MemoryRouter initialEntries={['/vote/new/quiz']}>
      <Routes>
        <Route path="/vote/new/quiz" element={<QuizPage />} />
        <Route path="/" element={<div>home</div>} />
      </Routes>
    </MemoryRouter>
  );
  expect(await screen.findByText('home')).toBeInTheDocument();
});
