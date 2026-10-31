import { fileURLToPath } from 'node:url';
import { createServer } from 'vite';
import { createLeaguePreview } from './league-preview-data.mjs';

const preview = createLeaguePreview();
const previewHtml = `<!doctype html><html lang="ko"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>투자리그 목업 미리보기</title><style>
*{box-sizing:border-box}body{margin:0;height:100dvh;display:flex;flex-direction:column;overflow:hidden;font:14px system-ui,sans-serif;color:#172c48;background:#f5f7fb}
header{display:grid;grid-template-columns:auto 1fr auto;align-items:center;gap:12px;padding:12px 20px;border-bottom:1px solid #d9e2ef;background:white}
strong{white-space:nowrap}nav{display:flex;justify-content:center;gap:6px;flex-wrap:wrap}
button,select{font:inherit;min-height:36px;border:1px solid #cfdaeb;border-radius:8px;padding:6px 10px;color:#17365f;background:#eef3fc;cursor:pointer}button:hover{background:#dce8ff}select{max-width:220px;background:white}label{display:flex;align-items:center;gap:6px;font-size:13px}#scenario{grid-column:1/-1;margin:0;color:#b42318}iframe{flex:1;width:100%;min-height:0;border:0;background:white}
@media(max-width:640px){header{grid-template-columns:1fr auto;gap:8px;padding:10px 12px}nav{grid-column:1/-1;grid-row:2;justify-content:flex-start}button,select{font-size:13px}select{max-width:190px}}
</style><header><strong>리그 목업</strong>
<nav aria-label="목업 화면 선택"><button data-path="/league">순위</button><button data-path="/league/portfolio">내 종목</button><button data-path="/league/preview-me">성과</button><button data-path="/portfolio/publication">참여</button></nav>
<label>예시<select id="scenario-select" aria-label="목업 예시"><option value="reset">기본</option><option value="snapshot">수요일 참여 · 기존 기록</option><option value="partial">신규 참여 · 기록 없음</option><option value="sync">포트폴리오 변경</option><option value="holiday">주말 · 수익률 유지</option><option value="us-wait">미국 대기(국내 휴장)</option><option value="common-wait">개인 성과 선반영 · 순위 유지</option><option value="common-ready">양 시장 준비 · 순위 갱신</option><option value="member-gap">특정 사용자 집계 보류</option></select></label>
<p id="scenario" role="status" hidden></p></header>
<iframe title="실제 투자리그 화면 미리보기" src="/league"></iframe>
<script type="module">
const frame=document.querySelector('iframe');
const select=document.querySelector('#scenario-select');
const status=document.querySelector('#scenario');
document.querySelectorAll('[data-path]').forEach(button=>button.addEventListener('click',()=>{frame.src=button.dataset.path}));
select.addEventListener('change',async()=>{
 select.disabled=true;status.hidden=true;
 try {const result=await fetch('/api/__league-preview/scenario',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({scenario:select.value})});if(!result.ok)throw Error('예시를 불러오지 못했습니다.');
 frame.src=select.value==='sync'?'/league/portfolio':['partial','holiday','us-wait','snapshot'].includes(select.value)?'/league/preview-me':'/league';
 }catch(error){status.textContent=error.message;status.hidden=false}finally{select.disabled=false}
});
</script></html>`;

const server = await createServer({
  root: fileURLToPath(new URL('../', import.meta.url)),
  define: {
    'import.meta.env.VITE_API_BASE_URL': JSON.stringify(''),
    'import.meta.env.GA_MEASUREMENT_ID': JSON.stringify(''),
    'import.meta.env.VITE_POSTHOG_PROJECT_TOKEN': JSON.stringify(''),
    'import.meta.env.VITE_POSTHOG_HOST': JSON.stringify(''),
  },
  server: { host: '127.0.0.1', port: 5174, strictPort: true },
  plugins: [
    {
      name: 'local-league-preview',
      configureServer(vite) {
        vite.middlewares.use(async (req, res, next) => {
          const url = new URL(req.url ?? '/', 'http://localhost');
          if (url.pathname === '/__league-preview') {
            res.setHeader('Content-Type', 'text/html; charset=utf-8');
            res.end(previewHtml);
            return;
          }
          if (!url.pathname.startsWith('/api/')) {
            next();
            return;
          }
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
            const { data, status } = preview.request(
              req.method ?? 'GET',
              url,
              raw ? JSON.parse(raw) : {},
            );
            res.statusCode = status;
            res.setHeader('Content-Type', 'application/json; charset=utf-8');
            res.setHeader('Cache-Control', 'no-store');
            res.end(status === 204 ? undefined : JSON.stringify(data));
          } catch {
            res.statusCode = 400;
            res.end(JSON.stringify({ message: '목업 요청을 확인해 주세요.' }));
          }
        });
      },
    },
  ],
});
await server.listen();
console.log('투자리그 목업: http://127.0.0.1:5174/__league-preview');
for (const signal of ['SIGINT', 'SIGTERM'])
  process.on(signal, async () => {
    await server.close();
    process.exit(0);
  });
