import type { Metadata } from 'next';

export const metadata: Metadata = {
  title: 'Trivlu — Group Travel Made Easy',
  description:
    'Turn group travel chaos into epic adventures with zero stress. Trivlu is the first AI trip maker for multi-traveler experiences.',
};

export default function RootLayout({ children }: { children: React.ReactNode }) {
  return (
    <html lang="en">
      <body>{children}</body>
    </html>
  );
}
