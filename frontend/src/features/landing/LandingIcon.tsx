const paths = {
  ArrowUpRight: 'M7 17 17 7M7 7h10v10',
  ChevronDown: 'm6 9 6 6 6-6',
  ChevronUp: 'm6 15 6-6 6 6',
  X: 'm6 6 12 12M6 18 18 6',
  Search: 'M21 21l-4.3-4.3M19 11a8 8 0 1 1-16 0 8 8 0 0 1 16 0',
  Signal: 'M3 20v-2m5 2v-6m5 6V9m5 11V4',
  Wifi: 'M2 8a17 17 0 0 1 20 0M5 12a11 11 0 0 1 14 0m-11 4a6 6 0 0 1 8 0m-4 4h.01',
  BatteryFull:
    'M22 10v4M4 7h13a2 2 0 0 1 2 2v6a2 2 0 0 1-2 2H4a2 2 0 0 1-2-2V9a2 2 0 0 1 2-2m3 3v4m4-4v4m4-4v4',
  UserRound: 'M20 21v-2a8 8 0 0 0-16 0v2M16 7a4 4 0 1 1-8 0 4 4 0 0 1 8 0',
};

export function LandingIcon({ name, size = 24 }: { name: keyof typeof paths; size?: number }) {
  return (
    <svg
      width={size}
      height={size}
      viewBox="0 0 24 24"
      fill="none"
      stroke="currentColor"
      strokeWidth="2"
      strokeLinecap="round"
      strokeLinejoin="round"
      aria-hidden="true"
    >
      <path d={paths[name]} />
    </svg>
  );
}
