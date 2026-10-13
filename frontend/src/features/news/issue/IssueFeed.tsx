import { useId, useRef, useState } from 'react';
import xMark from '../../../assets/platform-x.svg';
import threadsMark from '../../../assets/platform-threads.svg';
import { Link } from 'react-router-dom';
import { SocialPostEmbed } from '../../people/SocialPostEmbed';
import { feedDate, isToday, sourceLabel, youtubeId, type FeedItem } from './feed';

type ReadingItem = Exclude<FeedItem, { source: 'DAYNOMY' }>;

function InlinePost({ item }: { item: ReadingItem }) {
  const post = 'post' in item ? item.post : item.content;
  const video = post.sourceType === 'YOUTUBE' ? youtubeId(post.url) : null;
  return (
    <div className="issue-reading-embed">
      {post.sourceType === 'YOUTUBE' ? (
        <>
          {video ? (
            <iframe
              className="issue-video"
              title={item.title}
              src={`https://www.youtube-nocookie.com/embed/${video}`}
              allow="accelerometer; autoplay; clipboard-write; encrypted-media; gyroscope; picture-in-picture; web-share"
              allowFullScreen
            />
          ) : (
            <p>이 영상은 YouTube에서 확인해 주세요.</p>
          )}
          <a className="issue-video-link" href={post.url} target="_blank" rel="noreferrer">
            YouTube에서 원문 보기 ↗
          </a>
        </>
      ) : (
        <SocialPostEmbed post={{ ...post, sourceType: post.sourceType }} />
      )}
    </div>
  );
}

export function IssueFeed({ items }: { items: FeedItem[] }) {
  const [selected, setSelected] = useState<ReadingItem | null>(null);
  const id = useId();
  const triggers = useRef(new Map<string, HTMLButtonElement>());
  return (
    <>
      <ul className="issue-feed">
        {items.map((item) => (
          <li key={item.key}>
            <article className="issue-feed-row">
              <span
                className={`issue-platform-mark issue-platform-${item.source.toLowerCase()}`}
                aria-hidden="true"
              >
                {item.source === 'DAYNOMY' ? (
                  'D'
                ) : item.source === 'YOUTUBE' ? (
                  <svg viewBox="0 0 28 20">
                    <path
                      fill="#f03"
                      d="M27.4 3.1A3.5 3.5 0 0 0 24.9.6C22.7 0 14 0 14 0S5.3 0 3.1.6A3.5 3.5 0 0 0 .6 3.1C0 5.3 0 10 0 10s0 4.7.6 6.9a3.5 3.5 0 0 0 2.5 2.5C5.3 20 14 20 14 20s8.7 0 10.9-.6a3.5 3.5 0 0 0 2.5-2.5C28 14.7 28 10 28 10s0-4.7-.6-6.9Z"
                    />
                    <path fill="#fff" d="m11.2 14.3 7.3-4.3-7.3-4.3z" />
                  </svg>
                ) : (
                  <img src={item.source === 'THREADS' ? threadsMark : xMark} alt="" />
                )}
              </span>
              <div className="issue-meta">
                <span className="issue-source">{sourceLabel(item.source)}</span>
                {'person' in item && (
                  <Link
                    to={`/people?person=${item.person.id}${isToday(item.date) ? '' : '&view=all'}`}
                  >
                    {item.person.name}
                  </Link>
                )}
                <time dateTime={item.date ?? undefined}>{feedDate(item.date)}</time>
              </div>
              {item.source === 'DAYNOMY' ? (
                <Link className="issue-row-title" to={`/news/${item.article.id}`}>
                  <h3>{item.title}</h3>
                  <span aria-hidden="true">↗</span>
                </Link>
              ) : (
                <button
                  ref={(node) => {
                    if (node) triggers.current.set(item.key, node);
                    else triggers.current.delete(item.key);
                  }}
                  id={`${id}-${item.key}-trigger`}
                  className="issue-row-title"
                  type="button"
                  aria-expanded={selected?.key === item.key}
                  aria-controls={`${id}-${item.key}`}
                  onClick={() => setSelected(selected?.key === item.key ? null : item)}
                >
                  <span className="issue-post-title">{item.title}</span>
                  <span className="issue-expand-label">
                    {selected?.key === item.key
                      ? '접기 −'
                      : item.source === 'YOUTUBE'
                        ? '영상 보기 +'
                        : '글 펼치기 +'}
                  </span>
                </button>
              )}
              {item.source !== 'DAYNOMY' && (
                <div
                  id={`${id}-${item.key}`}
                  hidden={selected?.key !== item.key}
                  className="issue-inline-post"
                  role="region"
                  aria-labelledby={`${id}-${item.key}-trigger`}
                >
                  {selected?.key === item.key && (
                    <>
                      <InlinePost item={item} />
                      <button
                        className="issue-inline-close"
                        onClick={() => {
                          setSelected(null);
                          triggers.current.get(item.key)?.focus();
                        }}
                      >
                        글 접기 ↑
                      </button>
                    </>
                  )}
                </div>
              )}
            </article>
          </li>
        ))}
      </ul>
    </>
  );
}
