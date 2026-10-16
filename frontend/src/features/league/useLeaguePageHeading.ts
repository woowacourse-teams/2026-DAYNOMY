import { useEffect, useRef } from 'react';

export function useLeaguePageHeading(title: string | null, ready = true) {
  const heading = useRef<HTMLHeadingElement>(null);

  useEffect(() => {
    if (!title) return;
    const previousTitle = document.title;
    document.title = `${title} | DAYNOMY`;
    return () => {
      document.title = previousTitle;
    };
  }, [title]);

  useEffect(() => {
    if (title && ready) heading.current?.focus({ preventScroll: true });
  }, [title, ready]);

  return heading;
}
