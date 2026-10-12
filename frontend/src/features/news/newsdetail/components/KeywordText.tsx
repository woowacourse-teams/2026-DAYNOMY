import { useEffect, useId, useLayoutEffect, useRef, useState, type ReactNode } from 'react';
import type { KeywordCategory, KeywordResponse } from '../types.ts';

const keywordCategoryLabels: Record<KeywordCategory, string> = {
  PERSON: '인물',
  POLICY: '정책',
  EVENT: '사건',
  TERM: '용어',
  TREND: '흐름',
};

function Keyword({ keyword }: { keyword: KeywordResponse }) {
  const [open, setOpen] = useState(false);
  const tooltipId = useId();
  const containerRef = useRef<HTMLSpanElement>(null);
  const buttonRef = useRef<HTMLButtonElement>(null);
  const tooltipRef = useRef<HTMLSpanElement>(null);
  const pointerInside = useRef(false);
  const closeTimer = useRef<ReturnType<typeof setTimeout> | undefined>(undefined);

  function show() {
    clearTimeout(closeTimer.current);
    setOpen(true);
  }

  function scheduleClose() {
    clearTimeout(closeTimer.current);
    closeTimer.current = setTimeout(() => setOpen(false), 180);
  }

  useEffect(() => () => clearTimeout(closeTimer.current), []);

  useEffect(() => {
    if (!open) return;
    function dismissOutside(event: PointerEvent) {
      if (!containerRef.current?.contains(event.target as Node)) setOpen(false);
    }
    function dismissOnEscape(event: KeyboardEvent) {
      if (event.key === 'Escape') setOpen(false);
    }
    document.addEventListener('pointerdown', dismissOutside);
    document.addEventListener('keydown', dismissOnEscape);
    return () => {
      document.removeEventListener('pointerdown', dismissOutside);
      document.removeEventListener('keydown', dismissOnEscape);
    };
  }, [open]);

  useLayoutEffect(() => {
    if (!open) return;
    function positionTooltip() {
      const button = buttonRef.current;
      const tooltip = tooltipRef.current;
      if (!button || !tooltip) return;
      const anchor = button.getBoundingClientRect();
      const { width, height } = tooltip.getBoundingClientRect();
      const left = Math.max(16, Math.min(anchor.left, window.innerWidth - width - 16));
      const below = anchor.bottom + 12;
      const top =
        below + height <= window.innerHeight - 16 ? below : Math.max(16, anchor.top - height - 12);
      tooltip.style.left = `${left}px`;
      tooltip.style.top = `${top}px`;
    }
    positionTooltip();
    window.addEventListener('resize', positionTooltip);
    window.addEventListener('scroll', positionTooltip, true);
    return () => {
      window.removeEventListener('resize', positionTooltip);
      window.removeEventListener('scroll', positionTooltip, true);
    };
  }, [open]);

  return (
    <span
      ref={containerRef}
      onPointerDown={() => {
        pointerInside.current = true;
      }}
      onPointerEnter={(event) => {
        pointerInside.current = true;
        if (event.pointerType !== 'touch') show();
      }}
      onPointerLeave={(event) => {
        pointerInside.current = false;
        if (event.pointerType !== 'touch') scheduleClose();
      }}
      onBlur={(event) => {
        if (
          !event.currentTarget.contains(event.relatedTarget) &&
          (event.relatedTarget !== null || !pointerInside.current)
        )
          setOpen(false);
      }}
    >
      <button
        ref={buttonRef}
        type="button"
        className="keyword"
        aria-expanded={open}
        aria-describedby={open ? tooltipId : undefined}
        onFocus={show}
        onClick={show}
      >
        {keyword.keyword}
      </button>
      {open && (
        <span ref={tooltipRef} id={tooltipId} className="keyword-tooltip" role="tooltip">
          <span className="keyword-tooltip-heading">
            <span className="keyword-tooltip-badge">{keywordCategoryLabels[keyword.category]}</span>
            <strong>{keyword.keyword}</strong>
          </span>
          <span className="keyword-tooltip-details">
            <span className="keyword-tooltip-detail">
              <b>뜻</b>
              <span>{keyword.definition}</span>
            </span>
            <span className="keyword-tooltip-detail">
              <b>기사 맥락</b>
              <span>{keyword.context}</span>
            </span>
            <span className="keyword-tooltip-detail">
              <b>확인할 점</b>
              <span>{keyword.watchPoint}</span>
            </span>
          </span>
        </span>
      )}
    </span>
  );
}

function highlightKeywords(
  text: string,
  keywords: KeywordResponse[],
  highlightedKeywords: Set<string>,
) {
  const matches = keywords
    .filter(
      (keyword) =>
        keyword.keyword &&
        !highlightedKeywords.has(keyword.keyword) &&
        text.includes(keyword.keyword),
    )
    .sort((a, b) => b.keyword.length - a.keyword.length);

  if (!matches.length) return text;

  const parts: ReactNode[] = [];
  let index = 0;

  while (index < text.length) {
    const match = matches.find(
      (keyword) =>
        !highlightedKeywords.has(keyword.keyword) && text.startsWith(keyword.keyword, index),
    );

    if (!match) {
      parts.push(text[index]);
      index += 1;
      continue;
    }

    parts.push(<Keyword key={`${match.keyword}-${index}`} keyword={match} />);
    highlightedKeywords.add(match.keyword);
    index += match.keyword.length;
  }

  return parts;
}

export function KeywordText({
  paragraphs,
  keywords,
}: {
  paragraphs: string[];
  keywords: KeywordResponse[];
}) {
  const highlightedKeywords = new Set<string>();

  return (
    <>
      {paragraphs.map((paragraph, index) => (
        <p key={`${index}-${paragraph}`}>
          {highlightKeywords(paragraph, keywords, highlightedKeywords)}
        </p>
      ))}
    </>
  );
}
