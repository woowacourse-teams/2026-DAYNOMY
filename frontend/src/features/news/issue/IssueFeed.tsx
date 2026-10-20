import { trackEvent, type AnalyticsParams } from '../../../analytics';
import { feedProperties } from './analytics';
import { useId, useRef, useState } from 'react';
import xMark from '../../../assets/platform-x.svg';
import threadsMark from '../../../assets/platform-threads.svg';
import { Link } from 'react-router-dom';
import { SocialPostEmbed } from '../../people/SocialPostEmbed';
import { getCategoryLabel } from '../newslist/types';
import {
  feedDate,
  isToday,
  sourceLabel,
  timestamp,
  youtubeId,
  youtubeThumbnail,
  type FeedItem,
} from './feed';

type ReadingItem = Exclude<FeedItem, { source: 'DAYNOMY' }>;

function thumbnailImage(item: FeedItem) {
  if ('article' in item) return item.article.imageUrl;
  if (item.source === 'YOUTUBE') {
    return youtubeThumbnail('content' in item ? item.content.url : item.post.url);
  }
  if ('content' in item) return item.content.imageUrl;
  return null;
}

function FeedThumbnail({ item }: { item: FeedItem }) {
  const imageUrl = thumbnailImage(item);
  return (
    <span
      className={`issue-feed-thumbnail issue-feed-thumbnail-${item.source.toLowerCase()}`}
      aria-hidden="true"
    >
      <span className="issue-feed-thumbnail-fallback">
        {item.source !== 'DAYNOMY' && <span className="sr-only">{sourceLabel(item.source)}</span>}
        {item.source === 'X' || item.source === 'THREADS' ? (
          <img src={item.source === 'X' ? xMark : threadsMark} alt="" />
        ) : item.source === 'YOUTUBE' ? (
          <svg viewBox="0 0 28 20" aria-hidden="true">
            <rect x="1" y="1" width="26" height="18" rx="5" fill="currentColor" />
            <path d="m11 6 7 4-7 4z" fill="#f1f3f5" />
          </svg>
        ) : (
          <span>데이노미</span>
        )}
      </span>
      {imageUrl && (
        <img
          src={imageUrl}
          alt=""
          onError={(event) => {
            event.currentTarget.hidden = true;
          }}
        />
      )}
    </span>
  );
}

function InlinePost({ item, analytics }: { item: ReadingItem; analytics: AnalyticsParams }) {
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
          <a
            onClick={() => trackEvent('click_content_original', analytics)}
            className="issue-video-link"
            href={post.url}
            target="_blank"
            rel="noreferrer"
          >
            YouTube에서 원문 보기 ↗
          </a>
        </>
      ) : (
        <SocialPostEmbed analytics={analytics} post={{ ...post, sourceType: post.sourceType }} />
      )}
    </div>
  );
}

export function IssueFeed({
  items,
  surface = 'news_home',
  offset = 0,
}: {
  items: FeedItem[];
  surface?: string;
  offset?: number;
}) {
  const [selected, setSelected] = useState<ReadingItem | null>(null);
  const id = useId();
  const triggers = useRef(new Map<string, HTMLButtonElement>());
  const peopleTimeline = surface === 'people';
  const compactNews = surface === 'news_home' || surface === 'news_contents' || peopleTimeline;
  const showThumbnails = ['news_home', 'news_contents', 'people'].includes(surface);
  return (
    <>
      <ul
        className={`issue-feed${showThumbnails ? ' issue-feed--visual' : ''}${peopleTimeline ? ' people-timeline' : ''}`}
      >
        {items.map((item, index) => (
          <li
            key={item.key}
            className={
              peopleTimeline &&
              (index === 0 || timestamp(items[index - 1].date) !== timestamp(item.date))
                ? 'people-date-start'
                : undefined
            }
          >
            {peopleTimeline &&
              (index === 0 || timestamp(items[index - 1].date) !== timestamp(item.date)) && (
                <div className="people-date-marker">
                  <time dateTime={item.date ?? undefined} title={item.date ?? undefined}>
                    {timestamp(item.date)
                      ? feedDate(item.date, '발행').replace(' 발행', '')
                      : '날짜 미제공'}
                  </time>
                </div>
              )}
            <article
              className={`issue-feed-row${showThumbnails ? ' issue-feed-row--visual' : ''}${compactNews ? ' issue-feed-row--news' : ''}`}
            >
              {showThumbnails && <FeedThumbnail item={item} />}
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
                    onClick={() =>
                      trackEvent('click_person_preview', {
                        surface,
                        person_id: item.person.id,
                        position: offset + index + 1,
                      })
                    }
                    to={`/people?person=${item.person.id}${isToday(item.date) ? '' : '&view=all'}`}
                  >
                    {item.person.name}
                  </Link>
                )}
                {compactNews && item.source === 'DAYNOMY' && item.article.category !== 'ALL' && (
                  <span>{getCategoryLabel(item.article.category)}</span>
                )}
                {!peopleTimeline &&
                  (compactNews ? (
                    <time
                      dateTime={
                        (item.source === 'DAYNOMY'
                          ? item.date
                          : 'post' in item
                            ? item.post.createdAt
                            : item.content.createdAt) ?? undefined
                      }
                    >
                      {item.source === 'DAYNOMY'
                        ? feedDate(item.date, '발행')
                        : feedDate(
                            'post' in item ? item.post.createdAt : item.content.createdAt,
                            '등록',
                          )}
                    </time>
                  ) : (
                    <time dateTime={item.date ?? undefined}>{feedDate(item.date)}</time>
                  ))}
              </div>
              {item.source === 'DAYNOMY' ? (
                <Link
                  onClick={() =>
                    trackEvent('click_content', feedProperties(item, surface, offset + index + 1))
                  }
                  className="issue-row-title"
                  to={`/news/${item.article.id}`}
                >
                  <h3>{item.title}</h3>
                  {compactNews ? (
                    <span className="issue-expand-label">
                      기사 읽기
                      <svg className="issue-expand-chevron" viewBox="0 0 16 16" aria-hidden="true">
                        <path
                          d="M3 8h10m-4-4 4 4-4 4"
                          fill="none"
                          stroke="currentColor"
                          strokeWidth="1.5"
                        />
                      </svg>
                    </span>
                  ) : (
                    <span aria-hidden="true">↗</span>
                  )}
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
                  onClick={() => {
                    if (selected?.key !== item.key)
                      trackEvent(
                        'expand_content',
                        feedProperties(item, surface, offset + index + 1),
                      );
                    setSelected(selected?.key === item.key ? null : item);
                  }}
                >
                  <span className="issue-post-title">{item.title}</span>
                  <span className="issue-expand-label">
                    {selected?.key === item.key
                      ? '접기'
                      : item.source === 'YOUTUBE'
                        ? '영상 보기'
                        : '글 펼치기'}
                    <svg className="issue-expand-chevron" viewBox="0 0 16 16" aria-hidden="true">
                      <path d="m4 6 4 4 4-4" fill="none" stroke="currentColor" strokeWidth="1.5" />
                    </svg>
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
                      <InlinePost
                        item={item}
                        analytics={feedProperties(item, surface, offset + index + 1)}
                      />
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
