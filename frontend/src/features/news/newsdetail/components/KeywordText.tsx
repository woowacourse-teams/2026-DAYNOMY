import type { ReactNode } from 'react';
import type { KeywordResponse } from '../types.ts';

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
          <span className="keyword-tooltip-badge">정책</span>
          <strong>{match.keyword}</strong>
          <span>{match.description}</span>
          <span>
            <b>POINT 1. 왜 중요해요?</b>
            금리와 물가 흐름을 이해하는 기준이 될 수 있어요.
          </span>
          <span>
            <b>POINT 2. 어떤 기대?</b>
            시장이 앞으로 움직일 가능성을 예상하는 단서예요.
          </span>
          <span>
            <b>POINT 3. 왜 주목해요?</b>
            자산 가격과 투자 판단에 영향을 줄 수 있어요.
          </span>
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
