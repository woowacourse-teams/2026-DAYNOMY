import { useEffect, type ReactNode } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import LoginPage from './features/pages/components/LoginPage';
import NotFoundPage from './features/pages/components/NotFoundPage';
import { InfoPage } from './features/pages/components/InfoPage';
import { NewsDetailPage } from './features/news/newsdetail/NewsDetailPage';
import { NewsListPage } from './features/news/newslist/NewsListPage';
import { RealEstateLoanRulePage } from './features/news/newslist/RealEstateLoanRulePage';
import SearchPage from './features/search/SearchPage';
import { PortfolioPage } from './features/portfolio/PortfolioPage';
import { LeaguePage } from './features/league/LeaguePage';
import { InvestorProfilePage } from './features/league/InvestorProfilePage';
import { PublicationSettingsPage } from './features/league/PublicationSettingsPage';
import { InvestmentRecordsPage } from './features/league/InvestmentRecordsPage';
import { FollowingPage } from './features/league/FollowingPage';
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
import { AdminStockSyncPage } from './features/admin/AdminStockSyncPage';
import { AdminShell } from './features/admin/components/AdminShell';
import './App.css';
import './features/admin/admin.css';

function AnalyticsTracker() {
  const location = useLocation();
  useEffect(() => {
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

  return location.pathname.startsWith('/admin') ? null : <Footer />;
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
        to={`/login?returnTo=${encodeURIComponent(`${location.pathname}${location.search}`)}`}
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

  return <AdminShell>{children}</AdminShell>;
}

function UserRoute({ children }: { children: ReactNode }) {
  const { isLoggedIn, loading } = useAuth();
  const location = useLocation();

  if (loading) {
    return <main className="league-page league-state" aria-busy="true" />;
  }

  if (!isLoggedIn) {
    return (
      <Navigate
        to={`/login?returnTo=${encodeURIComponent(`${location.pathname}${location.search}`)}`}
        replace
      />
    );
  }

  return children;
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

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AnalyticsTracker />
        <ScrollToTop />
        <PostLoginRedirect />
        <div className="app-shell">
          <AppHeader />
          <LeagueNavigation />
          <div className="app-content">
            <Routes>
              <Route path="/" element={<PortfolioPage />} />
              <Route path="/portfolio" element={<Navigate to="/" replace />} />
              <Route
                path="/portfolio/records"
                element={<Navigate to="/league/portfolio" replace />}
              />
              <Route
                path="/league/portfolio"
                element={
                  <UserRoute>
                    <InvestmentRecordsPage />
                  </UserRoute>
                }
              />
              <Route
                path="/portfolio/publication"
                element={
                  <UserRoute>
                    <PublicationSettingsPage />
                  </UserRoute>
                }
              />
              <Route path="/league" element={<LeaguePage />} />
              <Route
                path="/league/following"
                element={
                  <UserRoute>
                    <FollowingPage />
                  </UserRoute>
                }
              />
              <Route path="/league/:publicId" element={<InvestorProfilePage />} />
              <Route path="/membership" element={<Navigate to="/league" replace />} />
              <Route
                path="/mypage"
                element={
                  <UserRoute>
                    <MyPage />
                  </UserRoute>
                }
              />
              <Route path="/news" element={<NewsListPage />} />
              <Route path="/news/real-estate-loan-rule" element={<RealEstateLoanRulePage />} />
              <Route path="/news/:newsId" element={<NewsDetailPage />} />
              <Route path="/search" element={<SearchPage />} />
              <Route path="/romi/*" element={<Navigate to="/" replace />} />
              <Route path="/finance/*" element={<Navigate to="/" replace />} />
              <Route path="/stocks" element={<Navigate to="/" replace />} />
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
                path="/admin/stocks"
                element={
                  <AdminRoute>
                    <AdminStockSyncPage />
                  </AdminRoute>
                }
              />
              <Route path="/signup" element={<Navigate to="/login" replace />} />
              <Route path="*" element={<NotFoundPage />} />
            </Routes>
          </div>
          <AppFooter />
        </div>
      </AuthProvider>
    </BrowserRouter>
  );
}
