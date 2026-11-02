import { lazy, Suspense, useEffect, useLayoutEffect, useState, type ReactNode } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import LoginPage from './features/pages/components/LoginPage';
import NotFoundPage from './features/pages/components/NotFoundPage';
import { InfoPage } from './features/pages/components/InfoPage';
import { NewsDetailPage } from './features/news/newsdetail/NewsDetailPage';
import { NewsListPage } from './features/news/newslist/NewsListPage';
import { IssueHomePage } from './features/news/issue/IssueHomePage';
import { RealEstateLoanRulePage } from './features/news/newslist/RealEstateLoanRulePage';
import SearchPage from './features/search/SearchPage';
import { PortfolioPage } from './features/portfolio/PortfolioPage';
import { LeaguePage } from './features/league/LeaguePage';
import { InvestorProfilePage } from './features/league/InvestorProfilePage';
import { PublicationSettingsPage } from './features/league/PublicationSettingsPage';
import { LeagueProfilePage } from './features/league/LeagueProfilePage';
import { SharedPortfolioPage } from './features/league/SharedPortfolioPage';
import { FollowingPage } from './features/league/FollowingPage';
import { InvestmentReviewsPage } from './features/league/InvestmentReviewsPage';
import { MyPage } from './features/account/MyPage';
import { trackPageView } from './analytics';
import { AuthProvider } from './auth/AuthProvider';
import { safeReturnPath } from './auth/returnPath';
import { ServiceHeader } from './features/navigation/ServiceHeader';
import { LeagueNavigation } from './features/league/LeagueNavigation';
import { Footer } from './components/Footer';
import { useAuth } from './hooks/useLoginStatus';
import { AdminNewsFormPage } from './features/admin/AdminNewsFormPage';
import { AdminNewsPage, AdminAccessDeniedPage } from './features/admin/AdminNewsPage';
import { AdminStockContentsPage } from './features/admin/AdminStockContentsPage';
import { AdminStockSyncPage } from './features/admin/AdminStockSyncPage';
import { AdminPeoplePage } from './features/admin/AdminPeoplePage';
import { AdminFeedbackPage } from './features/admin/AdminFeedbackPage';
import {
  AdminContentReviewsPage,
  AdminContentSourcesPage,
} from './features/admin/AdminContentCollectionPage';
import { AdminShell } from './features/admin/components/AdminShell';
import { PeoplePage } from './features/people/PeoplePage';
import { StockContentPage } from './features/stock-content/StockContentPage';
import { CardDesignPreviewPage } from './features/stock-content/CardDesignPreviewPage';
import { FeedbackWidget } from './features/feedback/FeedbackWidget';
import { hasStartedService, rememberServiceStart } from './features/landing/startedService';
import { loadGuestPortfolioHoldings } from './features/portfolio/portfolioHoldingsStorage';
import { isAdminOnlyHostname, isUserHostname } from './serviceDomain';
import './App.css';
import './features/admin/admin.css';

const LandingPage = lazy(() => import('./features/landing/LandingPage'));

function AnalyticsTracker() {
  const location = useLocation();
  // Stop replay on protected routes before DOM mutation observers record the new screen.
  useLayoutEffect(() => {
    trackPageView(location.pathname);
  }, [location.pathname]);
  return null;
}

function AppHeader() {
  const location = useLocation();
  const showHeader =
    location.pathname === '/' ||
    location.pathname.startsWith('/news') ||
    location.pathname.startsWith('/search') ||
    location.pathname.startsWith('/portfolio') ||
    location.pathname.startsWith('/league') ||
    location.pathname === '/mypage' ||
    location.pathname.startsWith('/stocks/') ||
    location.pathname.startsWith('/people') ||
    location.pathname.startsWith('/about') ||
    location.pathname.startsWith('/terms') ||
    location.pathname.startsWith('/privacy') ||
    location.pathname.startsWith('/standard');

  return showHeader ? <ServiceHeader /> : null;
}

export function ScrollToTop() {
  const { pathname } = useLocation();

  useEffect(() => {
    window.scrollTo({ top: 0 });
  }, [pathname]);

  return null;
}

function AppFooter() {
  const location = useLocation();

  return location.pathname === '/welcome' ||
    location.pathname === '/login' ||
    location.pathname.startsWith('/admin') ? null : (
    <>
      <FeedbackWidget />
      <Footer />
    </>
  );
}

function AdminRoute({ children }: { children: ReactNode }) {
  const { isLoggedIn, loading, role } = useAuth();
  const location = useLocation();

  if (loading) {
    return <main className="admin-state-page" aria-busy="true" />;
  }

  if (!isLoggedIn) {
    return (
      <Navigate
        to={`/login?returnTo=${encodeURIComponent(`${location.pathname}${location.search}${location.hash}`)}`}
        replace
      />
    );
  }

  if (role !== 'ADMIN') {
    return (
      <AdminShell>
        <AdminAccessDeniedPage />
      </AdminShell>
    );
  }

  return (
    <AdminShell>
      {children}
      <FeedbackWidget variant="admin" />
    </AdminShell>
  );
}

function AuthenticatedRoute({ children }: { children: ReactNode }) {
  const { isLoggedIn, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return <main className="portfolio-page" aria-busy="true" />;
  }

  if (!isLoggedIn) {
    const returnTo = `${location.pathname}${location.search}${location.hash}`;
    return <Navigate to={`/login?returnTo=${encodeURIComponent(returnTo)}`} replace />;
  }

  return children;
}

function PortfolioRoute() {
  const { memberId, isLoggedIn, loading } = useAuth();

  if (loading) {
    return (
      <main className="portfolio-page" aria-busy="true">
        <div className="portfolio-page-title">
          <h1>내 포트폴리오</h1>
        </div>
      </main>
    );
  }

  return (
    <PortfolioPage key={isLoggedIn ? `member:${memberId}` : 'guest'} isLoggedIn={isLoggedIn} />
  );
}

const NewsHomePage = lazy(() => import('./features/news/issue/NewsHomePreview'));

function NewsHomeRoute() {
  const location = useLocation();
  const preview =
    import.meta.env.DEV && new URLSearchParams(location.search).get('preview') === 'briefing';
  return <NewsHomePage preview={preview} />;
}

function HomeRoute({ hasStarted, onStart }: { hasStarted: boolean; onStart: () => void }) {
  const { isLoggedIn, loading } = useAuth();

  if (!hasStarted && loading) return <main aria-busy="true" aria-label="서비스 확인 중" />;
  if (!hasStarted && !isLoggedIn)
    return (
      <Suspense fallback={<main aria-busy="true" aria-label="서비스 소개 불러오는 중" />}>
        <LandingPage onStart={onStart} />
      </Suspense>
    );
  return <PortfolioRoute />;
}

function PostLoginRedirect() {
  const location = useLocation();
  const navigate = useNavigate();
  const { isLoggedIn, loading } = useAuth();

  useEffect(() => {
    if (loading || !isLoggedIn) return;

    const storedPath = sessionStorage.getItem('daynomy:post-login-path');
    const targetPath = safeReturnPath(storedPath);
    if (!storedPath || `${location.pathname}${location.search}${location.hash}` === targetPath) {
      sessionStorage.removeItem('daynomy:post-login-path');
      return;
    }

    sessionStorage.removeItem('daynomy:post-login-path');
    navigate(targetPath, { replace: true });
  }, [isLoggedIn, loading, location.pathname, location.search, location.hash, navigate]);

  return null;
}

function StaffAccessDeniedPage() {
  return (
    <main className="admin-state-page">
      <section className="admin-state-panel">
        <h1>팀원 권한이 필요합니다.</h1>
        <p>팀원 계정으로 로그인한 뒤 다시 시도해 주세요.</p>
        <a className="admin-secondary-button" href="https://daynomy.com/">
          사용자 서비스로 이동
        </a>
      </section>
    </main>
  );
}

export function ServiceDomainBoundary({ children }: { children: ReactNode }) {
  const location = useLocation();
  const { isLoggedIn, loading, role } = useAuth();
  const isAdminOnlyDomain = isAdminOnlyHostname(window.location.hostname);
  const isAdminPath = location.pathname === '/admin' || location.pathname.startsWith('/admin/');
  const isPublicPath = ['/login', '/terms', '/privacy'].includes(location.pathname);

  if (isUserHostname(window.location.hostname) && isAdminPath) {
    return <Navigate to="/" replace />;
  }

  if (!isAdminOnlyDomain || isPublicPath) {
    return children;
  }

  if (loading) {
    return <main aria-busy="true" aria-label="팀원 로그인 확인 중" />;
  }

  if (!isLoggedIn) {
    const returnTo = `${location.pathname}${location.search}${location.hash}`;
    return <Navigate to={`/login?returnTo=${encodeURIComponent(returnTo)}`} replace />;
  }

  if (role !== 'ADMIN') {
    return <StaffAccessDeniedPage />;
  }

  return children;
}

function AppContent() {
  const location = useLocation();
  const navigate = useNavigate();
  const { isLoggedIn } = useAuth();
  const [hasStarted, setHasStarted] = useState(
    () => hasStartedService() || loadGuestPortfolioHoldings().length > 0,
  );
  const directServiceVisit =
    /^\/(?:news|league|stocks|people|search|portfolio|mypage|finance)(?:\/|$)/.test(
      location.pathname,
    );
  const awaitingWelcome = location.pathname === '/' && !hasStarted && !isLoggedIn;

  useEffect(() => {
    if (hasStarted || isLoggedIn || directServiceVisit) {
      rememberServiceStart();
      setHasStarted(true);
    }
  }, [directServiceVisit, hasStarted, isLoggedIn]);

  function startService() {
    rememberServiceStart();
    setHasStarted(true);
    window.scrollTo({ top: 0, behavior: 'instant' });
    navigate('/', { replace: true });
  }

  return (
    <>
      <AnalyticsTracker />
      <ScrollToTop />
      <PostLoginRedirect />
      <div className="app-shell">
        {!awaitingWelcome && <AppHeader />}
        <LeagueNavigation />
        <div className="app-content">
          <Routes>
            <Route
              path="/"
              element={<HomeRoute hasStarted={hasStarted} onStart={startService} />}
            />
            <Route
              path="/welcome"
              element={
                <Suspense fallback={<main aria-busy="true" aria-label="서비스 소개 불러오는 중" />}>
                  <LandingPage onStart={startService} />
                </Suspense>
              }
            />

            <Route path="/portfolio" element={<Navigate to="/" replace />} />
            <Route path="/investment-calendar" element={<Navigate to="/news" replace />} />
            <Route
              path="/portfolio/records"
              element={<Navigate to="/league/portfolio" replace />}
            />
            <Route path="/league" element={<LeaguePage />} />
            <Route
              path="/league/profile"
              element={
                <AuthenticatedRoute>
                  <LeagueProfilePage />
                </AuthenticatedRoute>
              }
            />
            <Route
              path="/league/portfolio"
              element={
                <AuthenticatedRoute>
                  <SharedPortfolioPage />
                </AuthenticatedRoute>
              }
            />
            <Route
              path="/portfolio/publication"
              element={
                <AuthenticatedRoute>
                  <PublicationSettingsPage />
                </AuthenticatedRoute>
              }
            />
            <Route
              path="/league/following"
              element={
                <AuthenticatedRoute>
                  <FollowingPage />
                </AuthenticatedRoute>
              }
            />
            <Route
              path="/league/reviews"
              element={
                <AuthenticatedRoute>
                  <InvestmentReviewsPage />
                </AuthenticatedRoute>
              }
            />
            <Route path="/league/:publicId" element={<InvestorProfilePage />} />
            <Route path="/membership" element={<Navigate to="/league" replace />} />
            <Route
              path="/mypage"
              element={
                <AuthenticatedRoute>
                  <MyPage />
                </AuthenticatedRoute>
              }
            />
            <Route
              path="/news"
              element={
                <Suspense fallback={<main aria-busy="true" aria-label="소식 불러오는 중" />}>
                  <NewsHomeRoute />
                </Suspense>
              }
            />
            <Route path="/news/today" element={<NewsListPage />} />
            <Route path="/news/contents" element={<IssueHomePage key="contents" fullFeed />} />
            <Route path="/news/real-estate-loan-rule" element={<RealEstateLoanRulePage />} />
            <Route path="/news/:newsId" element={<NewsDetailPage />} />
            <Route path="/search" element={<SearchPage />} />
            <Route path="/finance/*" element={<Navigate to="/" replace />} />
            <Route path="/stocks" element={<Navigate to="/" replace />} />
            <Route path="/stocks/:assetId/card-preview" element={<CardDesignPreviewPage />} />
            <Route path="/stocks/:assetId" element={<StockContentPage />} />
            <Route path="/people" element={<PeoplePage />} />
            <Route path="/login" element={<LoginPage />} />
            <Route path="/about" element={<InfoPage page="about" />} />
            <Route path="/terms" element={<InfoPage page="terms" />} />
            <Route path="/privacy" element={<InfoPage page="privacy" />} />
            <Route path="/standard" element={<InfoPage page="standard" />} />
            <Route
              path="/admin"
              element={
                <AdminRoute>
                  <Navigate to="/admin/news" replace />
                </AdminRoute>
              }
            />
            <Route
              path="/admin/news"
              element={
                <AdminRoute>
                  <AdminNewsPage />
                </AdminRoute>
              }
            />
            <Route
              path="/admin/news/new"
              element={
                <AdminRoute>
                  <AdminNewsFormPage />
                </AdminRoute>
              }
            />
            <Route
              path="/admin/news/:newsId/edit"
              element={
                <AdminRoute>
                  <AdminNewsFormPage />
                </AdminRoute>
              }
            />
            <Route
              path="/admin/feedbacks"
              element={
                <AdminRoute>
                  <AdminFeedbackPage />
                </AdminRoute>
              }
            />
            <Route
              path="/admin/stocks"
              element={
                <AdminRoute>
                  <AdminStockSyncPage />
                </AdminRoute>
              }
            />
            <Route
              path="/admin/stock-contents"
              element={
                <AdminRoute>
                  <AdminStockContentsPage />
                </AdminRoute>
              }
            />
            <Route
              path="/admin/content-collection"
              element={
                <AdminRoute>
                  <Navigate to="/admin/content-sources" replace />
                </AdminRoute>
              }
            />
            <Route
              path="/admin/content-sources"
              element={
                <AdminRoute>
                  <AdminContentSourcesPage />
                </AdminRoute>
              }
            />
            <Route
              path="/admin/content-reviews"
              element={
                <AdminRoute>
                  <AdminContentReviewsPage />
                </AdminRoute>
              }
            />
            <Route
              path="/admin/people"
              element={
                <AdminRoute>
                  <AdminPeoplePage />
                </AdminRoute>
              }
            />
            <Route path="/signup" element={<Navigate to="/login" replace />} />
            <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </div>
        {!awaitingWelcome && <AppFooter />}
      </div>
    </>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <ServiceDomainBoundary>
          <AppContent />
        </ServiceDomainBoundary>
      </AuthProvider>
    </BrowserRouter>
  );
}
