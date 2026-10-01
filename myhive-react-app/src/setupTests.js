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

// Polyfill Web Streams for jsdom (assistant-ui's assistant-stream builds
// TransformStreams at import time)
import { ReadableStream, TransformStream, WritableStream } from 'node:stream/web';
if (typeof globalThis.TransformStream === 'undefined') {
  globalThis.ReadableStream = ReadableStream;
  globalThis.TransformStream = TransformStream;
  globalThis.WritableStream = WritableStream;
}

// jsdom has no ResizeObserver or Element.scrollTo; assistant-ui's thread
// viewport uses both to keep the newest message in view.
if (typeof globalThis.ResizeObserver === 'undefined') {
  globalThis.ResizeObserver = class {
    observe() {}
    unobserve() {}
    disconnect() {}
  };
}
if (typeof Element.prototype.scrollTo !== 'function') {
  Element.prototype.scrollTo = function scrollTo() {};
}
