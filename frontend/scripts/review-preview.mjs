import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import { createReviewPreview } from './review-preview-data.mjs';

const preview = createReviewPreview();
const server = await createServer({
  root: fileURLToPath(new URL('../', import.meta.url)),
  define: { 'import.meta.env.VITE_API_BASE_URL': JSON.stringify('') },
  server: { host: '127.0.0.1', port: 5199, strictPort: true },
  plugins: [
    {
      name: 'local-review-preview',
      configureServer(vite) {
        vite.middlewares.use(async (req, res, next) => {
          const url = new URL(req.url ?? '/', 'http://127.0.0.1');
          if (!url.pathname.startsWith('/api/')) return next();
          try {
            let raw = '';
            for await (const chunk of req) {
              raw += chunk;
              if (raw.length > 65536) {
                res.statusCode = 413;
                res.end();
                return;
              }
            }
            const result = preview.request(req.method ?? 'GET', url, raw ? JSON.parse(raw) : {});
            res.statusCode = result.status;
            res.setHeader('Content-Type', 'application/json; charset=utf-8');
            res.setHeader('Cache-Control', 'no-store');
            res.end(result.status === 204 ? undefined : JSON.stringify(result.data));
          } catch {
            res.statusCode = 400;
            res.end(JSON.stringify({ message: '목데이터 요청을 확인해 주세요.' }));
          }
        });
      },
    },
  ],
});
await server.listen();
console.log('가상 데이터로 실제 리그 화면 확인: http://127.0.0.1:5199/league');
console.log('실제 복기 페이지: http://127.0.0.1:5199/league/reviews');
for (const signal of ['SIGINT', 'SIGTERM'])
  process.on(signal, async () => {
    await server.close();
    process.exit(0);
  });
