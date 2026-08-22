import { getCookie } from './cookies';

test('reads a cookie by name', () => {
  Object.defineProperty(document, 'cookie', { value: '_fbp=fb.1.1.2; other=x; _fbc=fb.1.1.abc', configurable: true });
  expect(getCookie('_fbp')).toBe('fb.1.1.2');
  expect(getCookie('_fbc')).toBe('fb.1.1.abc');
  expect(getCookie('missing')).toBeNull();
});
