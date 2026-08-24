type BookmarkIconProps = {
  selected: boolean;
};

export function BookmarkIcon({ selected }: BookmarkIconProps) {
  return (
    <svg viewBox="0 0 24 24" aria-hidden="true">
      <path
        d="m12 3.2 2.72 5.51 6.08.88-4.4 4.29 1.04 6.06L12 17.08l-5.44 2.86 1.04-6.06-4.4-4.29 6.08-.88L12 3.2Z"
        fill={selected ? 'currentColor' : 'none'}
      />
    </svg>
  );
}
