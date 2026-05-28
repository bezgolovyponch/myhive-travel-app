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

// Polyfill TextEncoder/TextDecoder for jsdom (react-router v7 needs them)
import { TextDecoder, TextEncoder } from 'node:util';
if (typeof globalThis.TextEncoder === 'undefined') {
  globalThis.TextEncoder = TextEncoder;
  globalThis.TextDecoder = TextDecoder;
}
