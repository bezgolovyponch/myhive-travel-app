import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter} from 'react-router-dom';
import LanguageSwitcher from './LanguageSwitcher';
import {LocaleProvider, PageAlternatesContext} from '../i18n';
import en from '../i18n/messages/en.json';
import de from '../i18n/messages/de.json';

function renderSwitcher(initialEntry, locale, messages, alternates) {
  const switcher = (
    <MemoryRouter initialEntries={[initialEntry]}>
      <LanguageSwitcher/>
    </MemoryRouter>
  );
  return render(
    <LocaleProvider locale={locale} messages={messages}>
      {alternates ? (
        <PageAlternatesContext.Provider value={alternates}>{switcher}</PageAlternatesContext.Provider>
      ) : switcher}
    </LocaleProvider>
  );
}

test('links the German activity page to its English pair', async () => {
  renderSwitcher('/jga-prag/aktivitaeten/beer-spa', 'de', de);
  await userEvent.click(screen.getByRole('button'));
  expect(screen.getByRole('link', {name: 'English'}))
    .toHaveAttribute('href', '/prague-stag-do/activities/beer-spa');
});

test('links the English hub to the German hub', async () => {
  renderSwitcher('/prague-stag-do?tab=activities', 'en', en);
  await userEvent.click(screen.getByRole('button'));
  expect(screen.getByRole('link', {name: 'Deutsch'}))
    .toHaveAttribute('href', '/de/jga-prag?tab=activities');
});

test('usesPageAlternatesWhenProvided', async () => {
  renderSwitcher('/prague-stag-do/activities/axe-throwing', 'en', en,
    {de: '/de/jga-prag/aktivitaeten/axtwerfen'});
  await userEvent.click(screen.getByRole('button'));
  expect(screen.getByRole('link', {name: 'Deutsch'}))
    .toHaveAttribute('href', '/de/jga-prag/aktivitaeten/axtwerfen');
});

test('fallsBackToSegmentTranslation', async () => {
  renderSwitcher('/prague-stag-do/activities/axe-throwing', 'en', en);
  await userEvent.click(screen.getByRole('button'));
  expect(screen.getByRole('link', {name: 'Deutsch'}))
    .toHaveAttribute('href', '/de/jga-prag/aktivitaeten/axe-throwing');
});
