import {render, screen} from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import {MemoryRouter} from 'react-router-dom';
import LanguageSwitcher from './LanguageSwitcher';
import {LocaleProvider} from '../i18n';
import en from '../i18n/messages/en.json';
import de from '../i18n/messages/de.json';

function renderSwitcher(initialEntry, locale, messages) {
  return render(
    <LocaleProvider locale={locale} messages={messages}>
      <MemoryRouter initialEntries={[initialEntry]}>
        <LanguageSwitcher/>
      </MemoryRouter>
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
