import { createServer } from 'node:http';
import { readFile, stat } from 'node:fs/promises';
import { extname, join, resolve, sep } from 'node:path';
import { fileURLToPath } from 'node:url';

const defaultDist = fileURLToPath(new URL('../dist/', import.meta.url));
const imageFallback = '/daynomy-favicon.png';
const privateRoutes = new Set([
  '/',
  '/index.html',
  '/portfolio',
  '/portfolio/records',
  '/portfolio/publication',
  '/investment-calendar',
  '/league',
  '/league/profile',
  '/league/portfolio',
  '/league/following',
  '/league/reviews',
  '/membership',
  '/mypage',
  '/news/today',
  '/news/contents',
  '/news/real-estate-loan-rule',
  '/search',
  '/stocks',
  '/people',
  '/login',
  '/about',
  '/terms',
  '/privacy',
  '/standard',
  '/signup',
  '/admin',
]);
const mimeTypes = {
  '.css': 'text/css',
  '.gif': 'image/gif',
  '.html': 'text/html',
  '.ico': 'image/x-icon',
  '.jpeg': 'image/jpeg',
  '.jpg': 'image/jpeg',
  '.js': 'text/javascript',
  '.json': 'application/json',
  '.png': 'image/png',
  '.svg': 'image/svg+xml',
  '.txt': 'text/plain',
  '.webp': 'image/webp',
  '.woff': 'font/woff',
  '.woff2': 'font/woff2',
};

function escapeHtml(value) {
  return String(value).replace(
    /[&<>"']/g,
    (character) =>
      ({
        '&': '&amp;',
        '<': '&lt;',
        '>': '&gt;',
        '"': '&quot;',
        "'": '&#39;',
      })[character],
  );
}

function description(value) {
  return Array.from(String(value).replace(/\s+/g, ' ').trim()).slice(0, 160).join('');
}

function safeImage(value, siteOrigin) {
  if (typeof value !== 'string') return `${siteOrigin}${imageFallback}`;
  try {
    const url = new URL(value);
    return ['http:', 'https:'].includes(url.protocol) ? url.href : `${siteOrigin}${imageFallback}`;
  } catch {
    return `${siteOrigin}${imageFallback}`;
  }
}

function safeLink(value) {
  if (typeof value !== 'string') return null;
  try {
    const url = new URL(value);
    return ['http:', 'https:'].includes(url.protocol) ? url.href : null;
  } catch {
    return null;
  }
}

function pageHtml(template, page, siteOrigin, indexable) {
  const canonical = `${siteOrigin}${page.path}`;
  const title = `${page.title} | DAYNOMY`;
  const summary = description(page.description);
  const image = safeImage(page.image, siteOrigin);
  const meta = [
    `<meta name="description" content="${escapeHtml(summary)}" />`,
    `<link rel="canonical" href="${escapeHtml(canonical)}" />`,
    '<meta property="og:site_name" content="DAYNOMY" />',
    `<meta property="og:type" content="${page.article ? 'article' : 'website'}" />`,
    `<meta property="og:url" content="${escapeHtml(canonical)}" />`,
    `<meta property="og:title" content="${escapeHtml(title)}" />`,
    `<meta property="og:description" content="${escapeHtml(summary)}" />`,
    `<meta property="og:image" content="${escapeHtml(image)}" />`,
    '<meta name="twitter:card" content="summary_large_image" />',
  ].join('\n    ');
  return template
    .replace('<title>DAYNOMY</title>', `<title>${escapeHtml(title)}</title>`)
    .replace(
      'content="noindex,nofollow"',
      `content="${indexable && page.indexable ? 'index,follow' : 'noindex,follow'}"`,
    )
    .replace('</head>', `${meta}\n  </head>`)
    .replace('<div id="root"></div>', `<div id="root">${page.body}</div>`);
}

async function apiJson(apiOrigin, path) {
  const response = await fetch(`${apiOrigin}${path}`, {
    headers: { Accept: 'application/json' },
    signal: AbortSignal.timeout(5000),
  });
  if (response.status === 404) throw Object.assign(new Error('Not found'), { status: 404 });
  if (!response.ok) throw new Error('Public API unavailable');
  return response.json();
}

function validNewsList(value) {
  return (
    value &&
    Array.isArray(value.items) &&
    typeof value.hasNext === 'boolean' &&
    value.items.every(
      (item) => Number.isSafeInteger(item.id) && item.id > 0 && typeof item.title === 'string',
    )
  );
}

async function publicPage(pathname, apiOrigin, siteOrigin) {
  if (pathname === '/welcome') {
    return {
      path: pathname,
      title: '서비스 소개',
      description: 'DAYNOMY에서 내 포트폴리오, 경제 소식과 투자 리그를 살펴보세요.',
      body: '<main><h1>DAYNOMY</h1><p>내 포트폴리오와 경제 소식을 한곳에서 살펴보세요.</p><a href="/news">최신 소식 보기</a></main>',
      indexable: true,
    };
  }
  if (pathname === '/news') {
    const news = await apiJson(apiOrigin, '/api/news?page=1&size=20');
    if (!validNewsList(news)) throw new Error('Invalid public news list');
    const links = news.items
      .map((item) => `<li><a href="/news/${item.id}">${escapeHtml(item.title)}</a></li>`)
      .join('');
    return {
      path: pathname,
      title: '소식',
      description: 'DAYNOMY의 최신 경제·투자 소식을 확인하세요.',
      body: `<main><h1>소식</h1><ul>${links}</ul></main>`,
      indexable: true,
    };
  }
  const newsMatch = /^\/news\/([^/]+)$/.exec(pathname);
  if (newsMatch) {
    if (!/^[1-9]\d*$/.test(newsMatch[1]) || !Number.isSafeInteger(Number(newsMatch[1])))
      throw Object.assign(new Error('Not found'), { status: 404 });
    const news = await apiJson(apiOrigin, `/api/news/${newsMatch[1]}`);
    if (
      !news ||
      typeof news.title !== 'string' ||
      typeof news.content !== 'string' ||
      !Number.isSafeInteger(news.id) ||
      news.id !== Number(newsMatch[1])
    )
      throw new Error('Invalid public news');
    const paragraphs = news.content
      .split(/\n\s*\n/)
      .filter(Boolean)
      .map((part) => `<p>${escapeHtml(part).replace(/\n/g, '<br>')}</p>`)
      .join('');
    const image = safeImage(news.imageUrl, siteOrigin);
    return {
      path: `/news/${news.id}`,
      title: news.title,
      description: news.content,
      image,
      body: `<main><article><h1>${escapeHtml(news.title)}</h1><img src="${escapeHtml(image)}" alt="" />${paragraphs}</article></main>`,
      article: true,
      indexable: true,
    };
  }
  const stockMatch = /^\/stocks\/([^/]+)$/.exec(pathname);
  if (stockMatch) {
    if (!/^[1-9]\d*$/.test(stockMatch[1]) || !Number.isSafeInteger(Number(stockMatch[1])))
      throw Object.assign(new Error('Not found'), { status: 404 });
    const stock = await apiJson(apiOrigin, `/api/assets/${stockMatch[1]}/contents`);
    if (
      !stock ||
      !Number.isSafeInteger(stock.assetId) ||
      stock.assetId !== Number(stockMatch[1]) ||
      typeof stock.assetName !== 'string' ||
      !Array.isArray(stock.contents)
    )
      throw new Error('Invalid public stock');
    const links = stock.contents
      .filter((item) => typeof item.title === 'string' && safeLink(item.url))
      .slice(0, 20)
      .map(
        (item) =>
          `<li><a href="${escapeHtml(safeLink(item.url))}" rel="noopener noreferrer">${escapeHtml(item.title)}</a></li>`,
      )
      .join('');
    return {
      path: `/stocks/${stock.assetId}`,
      title: `${stock.assetName} 소식`,
      description: `${stock.assetName} 관련 경제·투자 소식을 확인하세요.`,
      body: `<main><h1>${escapeHtml(stock.assetName)} 소식</h1><ul>${links}</ul></main>`,
      indexable: true,
    };
  }
  const profileMatch = /^\/league\/([^/]+)$/.exec(pathname);
  if (profileMatch) {
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/.test(profileMatch[1]))
      throw Object.assign(new Error('Not found'), { status: 404 });
    const profile = await apiJson(apiOrigin, `/api/league/investors/${profileMatch[1]}`);
    if (
      !profile ||
      profile.publicId !== profileMatch[1] ||
      typeof profile.displayName !== 'string' ||
      !(profile.bio === null || typeof profile.bio === 'string')
    )
      throw new Error('Invalid public profile');
    return {
      path: pathname,
      title: `${profile.displayName} 공개 프로필`,
      description: profile.bio || `${profile.displayName}의 DAYNOMY 공개 프로필`,
      image: profile.profileImageUrl,
      body: `<main><h1>${escapeHtml(profile.displayName)} 공개 프로필</h1>${profile.bio ? `<p>${escapeHtml(profile.bio)}</p>` : ''}</main>`,
      indexable: false,
    };
  }
  return null;
}

function knownSpaRoute(pathname) {
  return (
    privateRoutes.has(pathname) ||
    pathname.startsWith('/finance/') ||
    /^\/admin\/(news(?:\/new|\/\d+\/edit)?|feedbacks|stocks|stock-contents|people)$/.test(
      pathname,
    ) ||
    /^\/stocks\/[1-9]\d*\/card-preview$/.test(pathname)
  );
}

function send(response, method, status, body, type, cacheControl = 'no-store') {
  response.writeHead(status, {
    'Content-Type': `${type}; charset=utf-8`,
    'Cache-Control': cacheControl,
    'X-Content-Type-Options': 'nosniff',
  });
  response.end(method === 'HEAD' ? undefined : body);
}

export function createFrontendServer({
  distDir = defaultDist,
  siteOrigin = 'http://localhost:5173',
  apiOrigin = 'http://localhost:8080',
  production = false,
} = {}) {
  const publicOrigin = new URL(siteOrigin).origin;
  const backendOrigin = new URL(apiOrigin).origin;
  const root = resolve(distDir);
  let sitemap = '';
  let sitemapExpiresAt = 0;
  return createServer(async (request, response) => {
    const method = request.method;
    if (method !== 'GET' && method !== 'HEAD') {
      send(response, method, 405, 'Method Not Allowed', 'text/plain');
      return;
    }
    const isStaff = request.headers.host?.split(':')[0] === 'staff.daynomy.com';
    try {
      const pathname = new URL(request.url, 'http://localhost').pathname;
      if (pathname === '/robots.txt') {
        const body =
          production && !isStaff
            ? `User-agent: *\nAllow: /\nSitemap: ${publicOrigin}/sitemap.xml\n`
            : 'User-agent: *\nDisallow: /\n';
        send(response, method, 200, body, 'text/plain', 'no-cache');
        return;
      }
      if (pathname === '/sitemap.xml') {
        if (!production || isStaff) {
          send(response, method, 404, '', 'text/plain');
          return;
        }
        if (!sitemap || Date.now() >= sitemapExpiresAt) {
          const urls = new Set([`${publicOrigin}/welcome`, `${publicOrigin}/news`]);
          let page = 1;
          let hasNext = true;
          // shortcut: one sitemap covers up to 50,000 published news; add a sitemap index above that size.
          while (hasNext && urls.size < 50000 && page <= 500) {
            const news = await apiJson(backendOrigin, `/api/news?page=${page++}&size=100`);
            if (!validNewsList(news) || (news.hasNext && news.items.length === 0))
              throw new Error('Invalid public news list');
            for (const item of news.items) {
              if (urls.size >= 50000) break;
              urls.add(`${publicOrigin}/news/${item.id}`);
            }
            hasNext = news.hasNext;
          }
          sitemap = `<?xml version="1.0" encoding="UTF-8"?>\n<urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">${Array.from(urls, (url) => `<url><loc>${escapeHtml(url)}</loc></url>`).join('')}</urlset>`;
          sitemapExpiresAt = Date.now() + 5 * 60_000;
        }
        send(response, method, 200, sitemap, 'application/xml');
        return;
      }
      const requestedFile = resolve(root, `.${decodeURIComponent(pathname)}`);
      if (requestedFile !== root && requestedFile.startsWith(root + sep)) {
        const file = await stat(requestedFile).catch(() => null);
        if (file?.isFile()) {
          const bytes = await readFile(requestedFile);
          const hashed = /^\/assets\/.+-[A-Za-z0-9_-]{8}\.[^/]+$/.test(pathname);
          send(
            response,
            method,
            200,
            bytes,
            mimeTypes[extname(pathname)] || 'application/octet-stream',
            hashed ? 'public, max-age=31536000, immutable' : 'no-cache',
          );
          return;
        }
      }
      if (pathname.startsWith('/assets/') || pathname.includes('.')) {
        send(response, method, 404, 'Not Found', 'text/plain');
        return;
      }
      const template = await readFile(join(root, 'index.html'), 'utf8');
      if (!isStaff && !privateRoutes.has(pathname)) {
        const page = await publicPage(pathname, backendOrigin, publicOrigin);
        if (page) {
          send(
            response,
            method,
            200,
            pageHtml(template, page, publicOrigin, production),
            'text/html',
          );
          return;
        }
      }
      if (knownSpaRoute(pathname) || isStaff) {
        send(response, method, 200, template, 'text/html', 'no-cache');
        return;
      }
      send(response, method, 404, template, 'text/html');
    } catch (error) {
      if (error?.status === 404 || error instanceof URIError) {
        const template = await readFile(join(root, 'index.html'), 'utf8');
        send(response, method, 404, template, 'text/html');
        return;
      }
      console.error('Public HTML render failed', error?.code || error?.name || 'unknown');
      send(
        response,
        method,
        503,
        '<!doctype html><html lang="ko"><head><meta name="robots" content="noindex"></head><body><main><h1>잠시 후 다시 시도해 주세요.</h1></main></body></html>',
        'text/html',
      );
    }
  });
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  const server = createFrontendServer({
    distDir: process.env.FRONTEND_DIST_DIR || defaultDist,
    siteOrigin: process.env.SITE_ORIGIN || 'http://localhost:5173',
    apiOrigin: process.env.API_ORIGIN || 'http://localhost:8080',
    production: process.env.DEPLOY_ENV === 'production',
  });
  process.on('SIGTERM', () => server.close());
  process.on('SIGINT', () => server.close());
  server.listen(Number(process.env.PORT) || 80, '0.0.0.0');
}
