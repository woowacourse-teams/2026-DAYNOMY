import { useEffect, useState } from 'react';
import { Link, useSearchParams } from 'react-router-dom';
import { useFeaturedPeople } from './useFeaturedPeople';
import { createFeed } from '../news/issue/feed';
import { IssuePagination } from '../news/issue/IssuePagination';
import { IssueFeed } from '../news/issue/IssueFeed';
import './people.css';
import '../news/issue/issue.css';

export function PeoplePage() {
  const { people, loading, error, retry } = useFeaturedPeople();
  const [params, setParams] = useSearchParams();
  const [page, setPage] = useState(1);
  const selectedId = params.get('person');

  useEffect(() => {
    setPage(1);
  }, [selectedId]);
  const selected = people.find((person) => String(person.id) === selectedId);
  const matchingPeople = people.filter((person) => !selectedId || String(person.id) === selectedId);
  const items = createFeed([], matchingPeople);
  const totalPages = Math.max(1, Math.ceil(items.length / 12));
  const currentPage = Math.min(page, totalPages);

  return (
    <main className="issue-page issue-people-page">
      <Link className="issue-back" to="/news">
        ← 소식 홈
      </Link>
      <header className="issue-heading issue-people-heading">
        <div>
          <h1>인물의 글</h1>
        </div>
        <span className="issue-period">
          전체 기록
          <span aria-hidden="true"> · </span>
          {items.length}개
        </span>
        {selected && (
          <div className="issue-profile-links">
            {selected.xProfileUrl && (
              <a href={selected.xProfileUrl} target="_blank" rel="noreferrer">
                X 프로필 ↗
              </a>
            )}
            {selected.threadsProfileUrl && (
              <a href={selected.threadsProfileUrl} target="_blank" rel="noreferrer">
                Threads 프로필 ↗
              </a>
            )}
          </div>
        )}
      </header>
      <nav className="issue-person-nav" aria-label="인물 선택">
        {[
          { id: '', name: '전체' },
          ...people.map((person) => ({ id: String(person.id), name: person.name })),
        ].map((person) => (
          <button
            key={person.id}
            aria-pressed={(selectedId ?? '') === person.id}
            onClick={() => {
              const next = new URLSearchParams(params);
              if (person.id) next.set('person', person.id);
              else next.delete('person');
              setParams(next);
              setPage(1);
            }}
          >
            {person.name}
          </button>
        ))}
      </nav>
      {loading ? (
        <p className="issue-state" role="status">
          인물의 글을 불러오는 중입니다.
        </p>
      ) : error ? (
        <p className="issue-state" role="alert">
          {error} <button onClick={retry}>다시 시도</button>
        </p>
      ) : (
        <>
          {selectedId && !selected ? (
            <p className="issue-state">
              해당 인물을 찾을 수 없습니다. <Link to="/people">전체 인물 보기</Link>
            </p>
          ) : items.length ? (
            <IssueFeed
              key={`${selectedId}-${currentPage}`}
              items={items.slice((currentPage - 1) * 12, currentPage * 12)}
            />
          ) : (
            <p className="issue-state">아직 등록된 게시물이 없습니다.</p>
          )}
          {items.length > 0 && (
            <IssuePagination page={currentPage} totalPages={totalPages} onChange={setPage} />
          )}
        </>
      )}
    </main>
  );
}
