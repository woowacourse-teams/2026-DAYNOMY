import { lazy, Suspense, useEffect, useLayoutEffect, type ReactNode } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import NotFoundPage from './features/pages/components/NotFoundPage';
import { trackPageView } from './analytics';
import { AuthProvider } from './auth/AuthProvider';
import { safeReturnPath } from './auth/returnPath';
import { ServiceHeader } from './features/navigation/ServiceHeader';
import { LeagueNavigation } from './features/league/LeagueNavigation';
import { Footer } from './components/Footer';
import { useAuth } from './hooks/useLoginStatus';
import { AdminShell } from './features/admin/components/AdminShell';
import { AdminNewsImageGenerationProvider } from './features/admin/AdminNewsImageGeneration';
import { FeedbackWidget } from './features/feedback/FeedbackWidget';
import { PmfSurveyPrompt } from './features/pmf-survey/PmfSurveyPrompt';
import { rememberServiceStart } from './features/landing/startedService';
import { isAdminOnlyHostname, isUserHostname } from './serviceDomain';
import { EmailSubscriptionPrompt } from './features/email-subscription/EmailSubscriptionPrompt';
import './App.css';
import './features/admin/admin.css';

const NewsDetailPage = lazy(() =>
  import('./features/news/newsdetail/NewsDetailPage').then((module) => ({
    default: module.NewsDetailPage,
  })),
);
const NewsListPage = lazy(() =>
  import('./features/news/newslist/NewsListPage').then((module) => ({
    default: module.NewsListPage,
  })),
);
const IssueHomePage = lazy(() =>
  import('./features/news/issue/IssueHomePage').then((module) => ({
    default: module.IssueHomePage,
  })),
);
const RealEstateLoanRulePage = lazy(() =>
  import('./features/news/newslist/RealEstateLoanRulePage').then((module) => ({
    default: module.RealEstateLoanRulePage,
  })),
);
const PortfolioPage = lazy(() =>
  import('./features/portfolio/PortfolioPage').then((module) => ({
    default: module.PortfolioPage,
  })),
);
const LeaguePage = lazy(() =>
  import('./features/league/LeaguePage').then((module) => ({ default: module.LeaguePage })),
);
const InvestorProfilePage = lazy(() =>
  import('./features/league/InvestorProfilePage').then((module) => ({
    default: module.InvestorProfilePage,
  })),
);
const PublicationSettingsPage = lazy(() =>
  import('./features/league/PublicationSettingsPage').then((module) => ({
    default: module.PublicationSettingsPage,
  })),
);
const LeagueProfilePage = lazy(() =>
  import('./features/league/LeagueProfilePage').then((module) => ({
    default: module.LeagueProfilePage,
  })),
);
const SharedPortfolioPage = lazy(() =>
  import('./features/league/SharedPortfolioPage').then((module) => ({
    default: module.SharedPortfolioPage,
  })),
);
const FollowingPage = lazy(() =>
  import('./features/league/FollowingPage').then((module) => ({ default: module.FollowingPage })),
);
const InvestmentReviewsPage = lazy(() =>
  import('./features/league/InvestmentReviewsPage').then((module) => ({
    default: module.InvestmentReviewsPage,
  })),
);
const MyPage = lazy(() =>
  import('./features/account/MyPage').then((module) => ({ default: module.MyPage })),
);
const AdminNewsFormPage = lazy(() =>
  import('./features/admin/AdminNewsFormPage').then((module) => ({
    default: module.AdminNewsFormPage,
  })),
);
const AdminStockContentsPage = lazy(() =>
  import('./features/admin/AdminStockContentsPage').then((module) => ({
    default: module.AdminStockContentsPage,
  })),
);
const AdminStockSyncPage = lazy(() =>
  import('./features/admin/AdminStockSyncPage').then((module) => ({
    default: module.AdminStockSyncPage,
  })),
);
const AdminPeoplePage = lazy(() =>
  import('./features/admin/AdminPeoplePage').then((module) => ({
    default: module.AdminPeoplePage,
  })),
);
const AdminFeedbackPage = lazy(() =>
  import('./features/admin/AdminFeedbackPage').then((module) => ({
    default: module.AdminFeedbackPage,
  })),
);
const PeoplePage = lazy(() =>
  import('./features/people/PeoplePage').then((module) => ({ default: module.PeoplePage })),
);
const InfoPage = lazy(() =>
  import('./features/pages/components/InfoPage').then((module) => ({ default: module.InfoPage })),
);
const LoginPage = lazy(() => import('./features/pages/components/LoginPage'));
const SearchPage = lazy(() => import('./features/search/SearchPage'));
const AdminNewsPage = lazy(() =>
  import('./features/admin/AdminNewsPage').then((module) => ({ default: module.AdminNewsPage })),
);
const AdminAccessDeniedPage = lazy(() =>
  import('./features/admin/AdminNewsPage').then((module) => ({
    default: module.AdminAccessDeniedPage,
  })),
);
const AdminContentReviewsPage = lazy(() =>
  import('./features/admin/AdminContentCollectionPage').then((module) => ({
    default: module.AdminContentReviewsPage,
  })),
);
const AdminContentSourcesPage = lazy(() =>
  import('./features/admin/AdminContentCollectionPage').then((module) => ({
    default: module.AdminContentSourcesPage,
  })),
);

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
    return <main className="route-loading" aria-busy="true" />;
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
      <main className="route-loading" aria-busy="true">
        <div className="portfolio-page-title">
          <h1>내 포트폴리오</h1>
        </div>
      </main>
    );
  }

  return (
    <>
      <PortfolioPage
        key={isLoggedIn ? `member:${memberId}` : 'guest'}
        isLoggedIn={isLoggedIn}
        showIntroWhenEmpty
      />
      <PmfSurveyPrompt />
    </>
  );
}

const NewsHomePage = lazy(() => import('./features/news/issue/NewsHomePreview'));

function NewsHomeRoute() {
  const location = useLocation();
  const preview =
    import.meta.env.DEV && new URLSearchParams(location.search).get('preview') === 'briefing';
  return <NewsHomePage preview={preview} />;
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
  const directServiceVisit =
    /^\/(?:news|league|stocks|people|search|portfolio|mypage|finance)(?:\/|$)/.test(
      location.pathname,
    );
  useEffect(() => {
    if (isLoggedIn || directServiceVisit) rememberServiceStart();
  }, [directServiceVisit, isLoggedIn]);

  function startService() {
    rememberServiceStart();
    window.scrollTo({ top: 0, behavior: 'instant' });
    navigate('/', { replace: true });
  }

  return (
    <>
      <AnalyticsTracker />
      <ScrollToTop />
      <PostLoginRedirect />
      <EmailSubscriptionPrompt />
      <div className="app-shell">
        <AppHeader />
        <LeagueNavigation />
        <div className="app-content">
          <Suspense
            fallback={
              <main className="route-loading" role="status" aria-busy="true">
                화면을 불러오는 중…
              </main>
            }
          >
            <Routes>
              <Route path="/" element={<PortfolioRoute />} />
              <Route
                path="/welcome"
                element={
                  <Suspense
                    fallback={<main aria-busy="true" aria-label="서비스 소개 불러오는 중" />}
                  >
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
          </Suspense>
        </div>
        <AppFooter />
      </div>
    </>
  );
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AdminNewsImageGenerationProvider>
          <ServiceDomainBoundary>
            <AppContent />
          </ServiceDomainBoundary>
        </AdminNewsImageGenerationProvider>
      </AuthProvider>
    </BrowserRouter>
  );
}
