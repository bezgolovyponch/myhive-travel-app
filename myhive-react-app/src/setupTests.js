// jest-dom adds custom jest matchers for asserting on DOM nodes.
// allows you to do things like:
// expect(element).toHaveTextContent(/react/i)
// learn more: https://github.com/testing-library/jest-dom
import '@testing-library/jest-dom';

// Polyfill crypto for jsdom
import { webcrypto } from 'node:crypto';
Object.defineProperty(globalThis, 'crypto', {
  value: webcrypto,
});
