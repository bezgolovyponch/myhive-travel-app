import { clearQuizFlow, readQuizFlow, writeQuizFlow } from './quizFlow';

const context = {
  setup: {
    destination: { id: 'dest-1', slug: 'prague' },
    travelers: 4,
    startDate: '2026-09-01',
    endDate: '2026-09-03',
    email: 'organizer@example.com',
    budget: 2000,
  },
  responses: [{ questionId: 'q1', answerId: 'a1' }],
};

beforeEach(() => {
  sessionStorage.clear();
});

test('write/read round-trips the context', () => {
  writeQuizFlow(context);
  expect(readQuizFlow()).toEqual(context);
});

test('read returns null when nothing is stored', () => {
  expect(readQuizFlow()).toBeNull();
});

test('clear removes the stored context', () => {
  writeQuizFlow(context);
  clearQuizFlow();
  expect(readQuizFlow()).toBeNull();
});

test('read tolerates malformed JSON', () => {
  sessionStorage.setItem('myhive-quiz-flow', '{not json');
  expect(readQuizFlow()).toBeNull();
});
