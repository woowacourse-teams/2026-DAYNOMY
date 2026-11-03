import { after, before, test } from 'node:test';
import assert from 'node:assert/strict';
import { createServer, get } from 'node:http';
import { mkdtemp, mkdir, readFile, rm, writeFile } from 'node:fs/promises';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createFrontendServer } from '../../server/index.mjs';

const profileId = '12345678-1234-1234-1234-123456789abc';
let backend;
let frontend;
let distDir;
let forwardedCookie = false;
let newsPageRequests = 0;
let assetPageRequests = 0;
let assetIndexUnavailable = false;

function listen(server) {
  return new Promise((resolve) =>
    server.listen(0, '127.0.0.1', () => resolve(server.address().port)),
  );
}

before(async () => {
  distDir = await mkdtemp(join(tmpdir(), 'daynomy-public-html-'));
  await writeFile(
    join(distDir, 'index.html'),
    await readFile(new URL('../../index.html', import.meta.url)),
  );
  await mkdir(join(distDir, 'assets'));
  await writeFile(join(distDir, 'assets/app-AbCd0001.js'), 'console.log("app")');

  backend = createServer((request, response) => {
    forwardedCookie ||= Boolean(request.headers.cookie);
    response.setHeader('Content-Type', 'application/json');
    const path = new URL(request.url, 'http://localhost').pathname;
    if (path === '/api/news') {
      newsPageRequests += 1;
      const page = Number(new URL(request.url, 'http://localhost').searchParams.get('page'));
      response.end(
        JSON.stringify({
          items:
            page === 1 ? [{ id: 1, title: '뉴스 <제목>' }] : [{ id: 2, title: '두 번째 뉴스' }],
          hasNext: page === 1,
        }),
      );
    } else if (path === '/api/news/1') {
      response.end(
        JSON.stringify({
          id: 1,
          title: '뉴스 <제목>',
          content: '공개 내용 & 설명\n\n두 번째 문단',
          imageUrl: 'javascript:alert(1)',
        }),
      );
    } else if (path === '/api/news/2') {
      response.end(
        JSON.stringify({
          id: 2,
          title: '두 번째 뉴스',
          content: '공개 본문',
          imageUrl: 'https://example.com/news.webp',
        }),
      );
    } else if (path === '/api/contents/assets') {
      assetPageRequests += 1;
      if (assetIndexUnavailable) {
        response.writeHead(404);
        response.end('{}');
        return;
      }
      const page = Number(new URL(request.url, 'http://localhost').searchParams.get('page'));
      response.end(JSON.stringify({ assetIds: page === 1 ? [1] : [3], hasNext: page === 1 }));
    } else if (path === '/api/assets/1/contents') {
      response.end(
        JSON.stringify({
          assetId: 1,
          assetName: '삼성 <전자>',
          contents: [
            { title: '공개 자료', url: 'https://example.com/article' },
            { title: '위험한 자료', url: 'javascript:alert(1)' },
          ],
        }),
      );
    } else if (path === '/api/assets/2/contents') {
      response.end(JSON.stringify({ assetId: 2, assetName: '빈 종목', contents: [] }));
    } else if (path === '/api/assets/3/contents') {
      response.end(
        JSON.stringify({
          assetId: 3,
          assetName: '공개 종목',
          contents: [{ title: '공개 자료', url: 'https://example.com/stock-3' }],
        }),
      );
    } else if (path === `/api/league/investors/${profileId}`) {
      response.end(
        JSON.stringify({
          publicId: profileId,
          displayName: '공개 <투자자>',
          bio: '소개 & 내용',
          profileImageUrl: null,
          privateAmount: '1000000',
        }),
      );
    } else {
      response.writeHead(404);
      response.end('{}');
    }
  });
  const backendPort = await listen(backend);
  frontend = createFrontendServer({
    distDir,
    siteOrigin: 'https://daynomy.com',
    apiOrigin: `http://127.0.0.1:${backendPort}`,
    production: true,
  });
  await listen(frontend);
});

after(async () => {
  await Promise.all([
    new Promise((resolve) => frontend.close(resolve)),
    new Promise((resolve) => backend.close(resolve)),
  ]);
  await rm(distDir, { recursive: true, force: true });
});

function url(path) {
  return `http://127.0.0.1:${frontend.address().port}${path}`;
}

function getWithHost(path, host) {
  return new Promise((resolve, reject) => {
    get(url(path), { headers: { Host: host } }, (response) => {
      let body = '';
      response.setEncoding('utf8');
      response.on('data', (chunk) => {
        body += chunk;
      });
      response.on('end', () => resolve({ status: response.statusCode, body }));
    }).on('error', reject);
  });
}

test('공개 페이지의 첫 HTML에 본문, canonical, 공유 메타데이터가 들어간다', async () => {
  const landing = await fetch(url('/welcome'));
  const landingHtml = await landing.text();
  assert.equal(landing.status, 200);
  assert.match(landingHtml, /<h1>DAYNOMY<\/h1>/);
  assert.match(landingHtml, /<link rel="canonical" href="https:\/\/daynomy.com\/welcome"/);
  assert.match(landingHtml, /<meta name="robots" content="index,follow"/);

  const news = await fetch(url('/news/1'), { headers: { Cookie: 'access_token=private' } });
  const newsHtml = await news.text();
  assert.equal(news.status, 200);
  assert.equal(news.headers.get('cache-control'), 'no-store');
  assert.match(newsHtml, /<h1>뉴스 &lt;제목&gt;<\/h1>/);
  assert.match(newsHtml, /<p>공개 내용 &amp; 설명<\/p>/);
  assert.match(newsHtml, /<div id="root"><noscript><main>/);
  assert.match(newsHtml, /<\/main><\/noscript><\/div>/);
  assert.match(newsHtml, /property="og:url" content="https:\/\/daynomy.com\/news\/1"/);
  assert.match(newsHtml, /property="og:image" content="https:\/\/daynomy.com\/daynomy-og.png"/);
  assert.doesNotMatch(newsHtml, /javascript:alert|access_token/);
  assert.equal(forwardedCookie, false);
});

test('기존 기사 이미지는 공유 미리보기에서도 유지한다', async () => {
  const response = await fetch(url('/news/2'));
  const html = await response.text();
  assert.equal(response.status, 200);
  assert.match(html, /<img src="https:\/\/example.com\/news.webp"/);
  assert.match(html, /property="og:image" content="https:\/\/example.com\/news.webp"/);
});

test('종목·공개 프로필은 공개 필드만 노출하고 보호 경로는 빈 SPA HTML이다', async () => {
  const stock = await fetch(url('/stocks/1'));
  const stockHtml = await stock.text();
  assert.equal(stock.status, 200);
  assert.match(stockHtml, /<h1>삼성 &lt;전자&gt; 소식<\/h1>/);
  assert.match(stockHtml, /content="index,follow"/);
  assert.match(stockHtml, /https:\/\/example.com\/article/);
  assert.doesNotMatch(stockHtml, /위험한 자료|javascript:alert/);

  const emptyStock = await fetch(url('/stocks/2'));
  assert.equal(emptyStock.status, 200);
  assert.match(await emptyStock.text(), /content="noindex,follow"/);

  const profile = await fetch(url(`/league/${profileId}`));
  const profileHtml = await profile.text();
  assert.equal(profile.status, 200);
  assert.match(profileHtml, /공개 &lt;투자자&gt; 공개 프로필/);
  assert.match(profileHtml, /content="noindex,follow"/);
  assert.doesNotMatch(profileHtml, /1000000/);

  const privatePage = await fetch(url('/league/profile'));
  assert.equal(privatePage.status, 200);
  assert.match(await privatePage.text(), /<div id="root"><\/div>/);
  const staffHtml = await getWithHost('/news/1', 'staff.daynomy.com');
  assert.equal(staffHtml.status, 200);
  assert.match(staffHtml.body, /content="noindex,nofollow"/);
  assert.match(staffHtml.body, /<div id="root"><\/div>/);
});

test('없는 공개 URL은 404이고 sitemap은 발행 뉴스와 공개 자료가 있는 종목을 나열한다', async () => {
  for (const path of [
    '/news/0',
    '/news/9',
    '/stocks/not-an-id',
    `/league/${profileId.slice(0, 8)}`,
    '/league/abcdefab-1234-1234-1234-123456789abc',
    '/bad%zz',
    '/unknown',
  ]) {
    const response = await fetch(url(path));
    assert.equal(response.status, 404, path);
    assert.match(await response.text(), /content="noindex,nofollow"/);
  }
  const sitemap = await fetch(url('/sitemap.xml'));
  const xml = await sitemap.text();
  assert.equal(sitemap.status, 200);
  assert.match(xml, /https:\/\/daynomy.com\/news\/1/);
  assert.match(xml, /https:\/\/daynomy.com\/news\/2/);
  assert.match(xml, /https:\/\/daynomy.com\/stocks\/1/);
  assert.match(xml, /https:\/\/daynomy.com\/stocks\/3/);
  assert.doesNotMatch(xml, /stocks\/2/);
  assert.doesNotMatch(xml, /league|portfolio|login/);
  const requestsAfterFirstSitemap = newsPageRequests;
  const assetRequestsAfterFirstSitemap = assetPageRequests;
  assert.equal(await (await fetch(url('/sitemap.xml'))).text(), xml);
  assert.equal(newsPageRequests, requestsAfterFirstSitemap);
  assert.equal(assetPageRequests, assetRequestsAfterFirstSitemap);
  assert.match(
    await (await fetch(url('/robots.txt'))).text(),
    /Sitemap: https:\/\/daynomy.com\/sitemap.xml/,
  );
  assert.match(
    (await getWithHost('/robots.txt', 'staff.daynomy.com')).body,
    /^User-agent: \*\nDisallow: \/\n$/,
  );
});

test('개발 환경은 공개 본문을 제공해도 검색을 허용하지 않는다', async () => {
  const staging = createFrontendServer({
    distDir,
    siteOrigin: 'https://dev.daynomy.com',
    apiOrigin: `http://127.0.0.1:${backend.address().port}`,
  });
  const port = await listen(staging);
  try {
    const landing = await fetch(`http://127.0.0.1:${port}/welcome`);
    assert.match(await landing.text(), /content="noindex,follow"/);
    assert.match(
      await (await fetch(`http://127.0.0.1:${port}/robots.txt`)).text(),
      /^User-agent: \*\nDisallow: \/\n$/,
    );
    assert.equal((await fetch(`http://127.0.0.1:${port}/sitemap.xml`)).status, 404);
  } finally {
    await new Promise((resolve) => staging.close(resolve));
  }
});

test('백엔드가 아직 배포되지 않았어도 뉴스 sitemap을 제공한다', async () => {
  assetIndexUnavailable = true;
  const beforeBackend = createFrontendServer({
    distDir,
    siteOrigin: 'https://daynomy.com',
    apiOrigin: `http://127.0.0.1:${backend.address().port}`,
    production: true,
  });
  const port = await listen(beforeBackend);
  try {
    const sitemap = await fetch(`http://127.0.0.1:${port}/sitemap.xml`);
    assert.equal(sitemap.status, 200);
    const xml = await sitemap.text();
    assert.match(xml, /daynomy.com\/news\/1/);
    assert.doesNotMatch(xml, /daynomy.com\/stocks\//);
  } finally {
    assetIndexUnavailable = false;
    await new Promise((resolve) => beforeBackend.close(resolve));
  }
});

test('해시 자산과 보호 페이지의 캐시 정책을 유지한다', async () => {
  const asset = await fetch(url('/assets/app-AbCd0001.js'));
  assert.equal(asset.headers.get('cache-control'), 'public, max-age=31536000, immutable');
  assert.equal(asset.headers.get('content-type'), 'text/javascript; charset=utf-8');
  const login = await fetch(url('/login'));
  assert.equal(login.headers.get('cache-control'), 'no-cache');
  assert.match(await login.text(), /content="noindex,nofollow"/);
});
