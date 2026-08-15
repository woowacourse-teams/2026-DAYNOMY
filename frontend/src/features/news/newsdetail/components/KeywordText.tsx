import type { ReactNode } from 'react';
import type { RelatedIssue } from '../types.ts';

function highlightKeywords(text: string, issues: RelatedIssue[]) {
  const keywords = issues
    .map((issue) => ({ ...issue, keyword: issue.keyword || issue.title }))
    .filter((issue) => issue.keyword && text.includes(issue.keyword))
    .sort((a, b) => b.keyword.length - a.keyword.length);

  if (!keywords.length) return text;

  const parts: ReactNode[] = [];
  let index = 0;

  while (index < text.length) {
    const match = keywords.find((issue) => text.startsWith(issue.keyword, index));

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

export function KeywordText({ text, issues }: { text: string; issues: RelatedIssue[] }) {
  return <>{highlightKeywords(text, issues)}</>;
}
