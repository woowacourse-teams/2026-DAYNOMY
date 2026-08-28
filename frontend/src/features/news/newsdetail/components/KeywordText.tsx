import type { ReactNode } from 'react';
import type { KeywordCategory, KeywordResponse } from '../types.ts';

const keywordCategoryLabels: Record<KeywordCategory, string> = {
  PERSON: '인물',
  POLICY: '정책',
  EVENT: '사건',
  TERM: '용어',
  TREND: '흐름',
};

function highlightKeywords(text: string, keywords: KeywordResponse[]) {
  const matches = keywords
    .filter((keyword) => keyword.keyword && text.includes(keyword.keyword))
    .sort((a, b) => b.keyword.length - a.keyword.length);

  if (!matches.length) return text;

  const parts: ReactNode[] = [];
  let index = 0;

  while (index < text.length) {
    const match = matches.find((keyword) => text.startsWith(keyword.keyword, index));

    if (!match) {
      parts.push(text[index]);
      index += 1;
      continue;
    }

    parts.push(
      <mark className="keyword" key={`${match.keyword}-${index}`} tabIndex={0}>
        {match.keyword}
        <span className="keyword-tooltip" role="tooltip">
          <span className="keyword-tooltip-badge">{keywordCategoryLabels[match.category]}</span>
          <strong>{match.keyword}</strong>
          {match.points.map((point, index) => (
            <span key={point}>
              <b>{`POINT ${index + 1}`}</b>
              {point}
            </span>
          ))}
        </span>
      </mark>,
    );
    index += match.keyword.length;
  }

  return parts;
}

export function KeywordText({ text, keywords }: { text: string; keywords: KeywordResponse[] }) {
  return <>{highlightKeywords(text, keywords)}</>;
}
