type BookmarkIconProps = {
  selected: boolean;
};

export function BookmarkIcon({ selected }: BookmarkIconProps) {
  return (
    <svg viewBox="0 0 18 26" aria-hidden="true">
      <path
        d="M2.19 0h11.87c1.21 0 2.19.98 2.19 2.19V21c0 .77-.83 1.24-1.49.85l-6.64-3.91-6.63 3.91C.83 22.24 0 21.77 0 21V2.19C0 .98.98 0 2.19 0Z"
        fill={selected ? '#FACC15' : '#D9D9D9'}
      />
    </svg>
  );
}
