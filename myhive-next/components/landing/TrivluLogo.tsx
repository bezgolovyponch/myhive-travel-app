// The wordmark exactly as drawn in the mockups; sized by the parent
// (.logo svg / .band__logo).
export default function TrivluLogo({ className }: { className?: string }) {
  return (
    <svg viewBox="0 0 230 100" role="img" aria-label="Trivlu" className={className}>
      <text
        x="0"
        y="78"
        fill="currentColor"
        fontFamily="Arial, 'Helvetica Neue', sans-serif"
        fontWeight="800"
        fontSize="88"
        letterSpacing="-4"
      >
        tri
      </text>
      <path d="M79 32 L141 32 L110 82 Z" fill="#534AB7" />
      <text
        x="139"
        y="78"
        fill="currentColor"
        fontFamily="Arial, 'Helvetica Neue', sans-serif"
        fontWeight="800"
        fontSize="88"
        letterSpacing="-4"
      >
        lu
      </text>
    </svg>
  );
}

export function PhoneIcon() {
  return (
    <svg
      width="15"
      height="15"
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      aria-hidden="true"
    >
      <path d="M22 16.9v3a2 2 0 0 1-2.2 2 19.8 19.8 0 0 1-8.6-3.1 19.5 19.5 0 0 1-6-6A19.8 19.8 0 0 1 2.1 4.2 2 2 0 0 1 4.1 2h3a2 2 0 0 1 2 1.7c.1 1 .4 1.9.7 2.8a2 2 0 0 1-.5 2.1L8.1 9.9a16 16 0 0 0 6 6l1.3-1.3a2 2 0 0 1 2.1-.4c.9.3 1.8.6 2.8.7a2 2 0 0 1 1.7 2Z" />
    </svg>
  );
}

export function WhatsAppIcon() {
  return (
    <svg viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
      <path d="M12.04 2C6.6 2 2.17 6.43 2.17 11.87c0 1.94.55 3.75 1.5 5.29L2 22l4.98-1.6a9.8 9.8 0 0 0 5.06 1.4h.01c5.44 0 9.87-4.43 9.87-9.87S17.48 2 12.04 2Zm0 18.06h-.01a8.2 8.2 0 0 1-4.18-1.15l-.3-.18-3.1 1 1.02-3.02-.2-.31a8.17 8.17 0 0 1-1.25-4.36c0-4.52 3.68-8.2 8.21-8.2 2.19 0 4.25.86 5.8 2.4a8.15 8.15 0 0 1 2.4 5.8c0 4.53-3.68 8.02-8.39 8.02Zm4.5-6.13c-.25-.13-1.46-.72-1.68-.8-.23-.08-.39-.13-.56.12-.16.25-.64.8-.78.97-.15.16-.29.18-.53.06-.25-.13-1.04-.39-1.99-1.23-.73-.66-1.23-1.46-1.37-1.71-.15-.25-.02-.38.1-.51.11-.11.25-.29.37-.44.13-.15.17-.25.25-.42.09-.16.04-.31-.02-.44-.06-.12-.56-1.34-.76-1.84-.2-.48-.4-.42-.56-.43h-.47c-.16 0-.43.06-.65.31-.23.25-.86.84-.86 2.05s.88 2.38 1 2.54c.13.17 1.73 2.64 4.2 3.7.58.26 1.04.41 1.4.52.59.19 1.13.16 1.55.1.47-.07 1.46-.6 1.66-1.18.21-.58.21-1.07.15-1.18-.06-.11-.22-.17-.47-.29Z" />
    </svg>
  );
}
