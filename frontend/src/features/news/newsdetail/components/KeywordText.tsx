import type { ReactNode } from 'react';
import type { KeywordResponse } from '../types.ts';

function highlightKeywords(text: string, keywords: KeywordResponse[]) {
  const matchedKeywords = keywords
    .filter((keyword) => keyword.keyword && text.includes(keyword.keyword))
    .sort((a, b) => b.keyword.length - a.keyword.length);

  if (!matchedKeywords.length) return text;

  const parts: ReactNode[] = [];
  let index = 0;

  while (index < text.length) {
    const match = matchedKeywords.find((keyword) => text.startsWith(keyword.keyword, index));

    if (!match) {
      parts.push(text[index]);
      index += 1;
      continue;
    }

    parts.push(
      <mark
        className="keyword"
        data-tooltip={match.description}
        key={`${match.keyword}-${index}`}
        tabIndex={0}
      >
        {match.keyword}
      </mark>,
    );
    index += match.keyword.length;
  }

  return parts;
}

export function KeywordText({ text, keywords }: { text: string; keywords: KeywordResponse[] }) {
  return <>{highlightKeywords(text, keywords)}</>;
}
