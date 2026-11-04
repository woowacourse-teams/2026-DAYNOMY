import { createRoot } from 'react-dom/client';
import { MemoryRouter } from 'react-router-dom';
import { AuthContext } from '../auth/AuthContext';
import { ServiceHeader } from '../features/navigation/ServiceHeader';
import { PortfolioPage } from '../features/portfolio/PortfolioPage';
import { PmfSurveyPrompt } from '../features/pmf-survey/PmfSurveyPrompt';
import { portfolioDemoFetch } from './portfolioDemoData';
import '../index.css';
import '../App.css';

if (import.meta.env.DEV) {
  // Separate development document: no requests reach a backend, and no responses are persisted.
  const json = (value: unknown, status = 200) =>
    new Response(JSON.stringify(value), {
      status,
      headers: { 'Content-Type': 'application/json' },
    });
  let answered = false;
  let failOnce = new URLSearchParams(window.location.search).get('fail');
  window.fetch = async (input, init) => {
    const path = new URL(String(input), window.location.origin).pathname;
    if (path === '/api/pmf-surveys/current')
      return json({ surveyId: 805, eligible: true, responseId: answered ? 21 : null });
    if (path.startsWith('/api/pmf-surveys/')) {
      await new Promise((resolve) => setTimeout(resolve, 400));
      if ((init?.method === 'POST' && failOnce === 'answer') || failOnce === 'reason') {
        if (failOnce !== 'reason' || init?.method === 'PATCH') {
          failOnce = null;
          return json({ message: '미리보기 저장 실패' }, 503);
        }
      }
      if (init?.method === 'POST') {
        answered = true;
        return json({ id: 21 }, 201);
      }
      return new Response(null, { status: 204 });
    }
    return portfolioDemoFetch(input, init);
  };
  createRoot(document.getElementById('root')!).render(
    <AuthContext.Provider value={{ isLoggedIn: false, memberId: null, role: null, loading: false }}>
      <MemoryRouter initialEntries={['/']}>
        <div className="app-shell">
          <ServiceHeader />
          <div className="app-content">
            <PortfolioPage isLoggedIn={false} />
          </div>
          <PmfSurveyPrompt />
          <p style={{ padding: '16px', textAlign: 'center', color: '#6f7481', fontSize: '13px' }}>
            PMF 설문 미리보기 · 응답은 실제 저장되지 않아요.
          </p>
        </div>
      </MemoryRouter>
    </AuthContext.Provider>,
  );
} else {
  window.location.replace('/');
}
