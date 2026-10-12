import { useEffect, useRef } from 'react';
import { platformLabel, type FeaturedPersonPost } from './types';

const X_EMBED_SCRIPT_ID = 'daynomy-x-embed-script';
const THREADS_EMBED_SCRIPT_ID = 'daynomy-threads-embed-script';

function ArrowUpRightIcon() {
  return (
    <svg viewBox="0 0 16 16" aria-hidden="true">
      <path d="M4.5 11.5 11.75 4.25M6.25 4.25h5.5v5.5" />
    </svg>
  );
}

export function SocialPostEmbed({
  post,
}: {
  post: Pick<FeaturedPersonPost, 'sourceType' | 'title' | 'url'>;
}) {
  const rootRef = useRef<HTMLElement>(null);

  useEffect(() => {
    if (post.sourceType === 'X') {
      const load = () => {
        const xWindow = window as typeof window & {
          twttr?: { widgets?: { load: (element?: HTMLElement) => void } };
        };
        xWindow.twttr?.widgets?.load(rootRef.current ?? undefined);
      };
      const existingScript = document.getElementById(X_EMBED_SCRIPT_ID) as HTMLScriptElement | null;
      if (existingScript) {
        load();
        existingScript.addEventListener('load', load, { once: true });
        return () => existingScript.removeEventListener('load', load);
      }
      const script = document.createElement('script');
      script.id = X_EMBED_SCRIPT_ID;
      script.src = 'https://platform.x.com/widgets.js';
      script.async = true;
      script.addEventListener('load', load, { once: true });
      document.body.append(script);
      return () => script.removeEventListener('load', load);
    }

    document.getElementById(THREADS_EMBED_SCRIPT_ID)?.remove();
    const script = document.createElement('script');
    script.id = THREADS_EMBED_SCRIPT_ID;
    script.src = 'https://www.threads.com/embed.js';
    script.async = true;
    document.body.append(script);
  }, [post.sourceType, post.url]);

  return (
    <article className="people-social-embed" ref={rootRef}>
      {post.sourceType === 'THREADS' ? (
        <blockquote
          className="text-post-media"
          data-text-post-permalink={post.url}
          data-text-post-version="0"
        >
          <a href={post.url} target="_blank" rel="noreferrer">
            {post.title}
          </a>
        </blockquote>
      ) : (
        <blockquote className="twitter-tweet" data-dnt="true">
          <a href={post.url} target="_blank" rel="noreferrer">
            {post.title}
          </a>
        </blockquote>
      )}
      <a className="people-social-fallback" href={post.url} target="_blank" rel="noreferrer">
        <span>{platformLabel(post.sourceType)}에서 원문 보기</span>
        <ArrowUpRightIcon />
      </a>
    </article>
  );
}
