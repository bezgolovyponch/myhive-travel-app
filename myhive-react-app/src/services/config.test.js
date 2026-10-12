import {resolveDestinationSlugFromHost, FALLBACK_DESTINATION_SLUG} from './config';

test('uses the first label of a destination subdomain', () => {
    expect(resolveDestinationSlugFromHost('prague.trivlu.com')).toBe('prague');
    expect(resolveDestinationSlugFromHost('barcelona.trivlu.com')).toBe('barcelona');
});

test('falls back on apex and www', () => {
    expect(resolveDestinationSlugFromHost('trivlu.com')).toBe(FALLBACK_DESTINATION_SLUG);
    expect(resolveDestinationSlugFromHost('www.trivlu.com')).toBe(FALLBACK_DESTINATION_SLUG);
});

test('falls back on localhost and onrender hosts', () => {
    expect(resolveDestinationSlugFromHost('localhost')).toBe(FALLBACK_DESTINATION_SLUG);
    expect(resolveDestinationSlugFromHost('myhive-frontend.onrender.com')).toBe(FALLBACK_DESTINATION_SLUG);
});

test('falls back on empty or missing host', () => {
    expect(resolveDestinationSlugFromHost('')).toBe(FALLBACK_DESTINATION_SLUG);
    expect(resolveDestinationSlugFromHost(undefined)).toBe(FALLBACK_DESTINATION_SLUG);
});

test('an address is not a destination: a LAN IP, a loopback, an IPv6 host fall back', () => {
    expect(resolveDestinationSlugFromHost('192.168.0.216')).toBe(FALLBACK_DESTINATION_SLUG);
    expect(resolveDestinationSlugFromHost('10.0.0.7')).toBe(FALLBACK_DESTINATION_SLUG);
    expect(resolveDestinationSlugFromHost('127.0.0.1')).toBe(FALLBACK_DESTINATION_SLUG);
    expect(resolveDestinationSlugFromHost('[::1]')).toBe(FALLBACK_DESTINATION_SLUG);
    expect(resolveDestinationSlugFromHost('2-preview.example.com')).toBe(FALLBACK_DESTINATION_SLUG);
});
