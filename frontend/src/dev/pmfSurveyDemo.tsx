import { useState } from 'react';
import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { AuthContext } from '../auth/AuthContext';
import { ServiceHeader } from '../features/navigation/ServiceHeader';
import { PortfolioPage } from '../features/portfolio/PortfolioPage';
import { PmfSurveyPrompt } from '../features/pmf-survey/PmfSurveyPrompt';
import { nextKoreanMidnight, surveyStorageKey } from '../features/pmf-survey/storage';
import { portfolioDemoFetch } from './portfolioDemoData';
import '../index.css';
import '../App.css';

let answered = false;
let hiddenUntil: string | null = null;
let failOnce = new URLSearchParams(window.location.search).get('fail');

export function PmfSurveyDemo() {
  const [version, setVersion] = useState(0);
  return (
    <AuthContext.Provider value={{ isLoggedIn: false, memberId: null, role: null, loading: false }}>
      <MemoryRouter initialEntries={['/']}>
        <div className="app-shell">
          <ServiceHeader />
          <div className="app-content">
            <PortfolioPage isLoggedIn={false} />
          </div>
          <PmfSurveyPrompt key={version} />
          <p
            style={{
              position: 'fixed',
              bottom: 0,
              left: 0,
              background: 'white',
              padding: '16px',
              textAlign: 'center',
              color: '#6f7481',
              fontSize: '13px',
            }}
          >
            PMF 설문 미리보기 · 응답은 실제 저장되지 않아요.{' '}
            <button
              type="button"
              onClick={() => {
                answered = false;
                hiddenUntil = null;
                localStorage.removeItem(surveyStorageKey(805, null));
                setVersion((value) => value + 1);
              }}
            >
              미리보기 초기화
            </button>
          </p>
        </div>
      </MemoryRouter>
    </AuthContext.Provider>
  );
}

if (import.meta.env.DEV) {
  // Separate development document: no requests reach a backend, and no responses are persisted.
  const json = (value: unknown, status = 200) =>
    new Response(JSON.stringify(value), {
      status,
      headers: { 'Content-Type': 'application/json' },
    });
  window.fetch = async (input, init) => {
    const path = new URL(String(input), window.location.origin).pathname;
    if (path === '/api/pmf-surveys/current')
      return json({
        surveyId: 805,
        eligible: true,
        responseId: answered ? 21 : null,
        isFirstVisit: new URLSearchParams(window.location.search).has('firstVisit'),
        hiddenUntil,
      });
    if (path.startsWith('/api/pmf-surveys/')) {
      await new Promise((resolve) => setTimeout(resolve, 400));
      if (failOnce === 'answer' || (failOnce === 'snooze' && path.endsWith('/snooze'))) {
        failOnce = null;
        return json({ message: '미리보기 저장 실패' }, 503);
      }
      if (path.endsWith('/snooze')) {
        hiddenUntil = new Date(nextKoreanMidnight()).toISOString();
        return new Response(null, { status: 204 });
      }
      if (init?.method === 'POST') {
        answered = true;
        return json({ id: 21 }, 201);
      }
      return new Response(null, { status: 204 });
    }
    return portfolioDemoFetch(input, init);
  };
  const root = createRoot(document.getElementById('root')!);
  root.render(<PmfSurveyDemo />);
  import.meta.hot?.dispose(() => root.unmount());
} else {
  window.location.replace('/');
}
