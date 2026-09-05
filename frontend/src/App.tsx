import { useEffect, type ReactNode } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import LoginPage from './features/pages/components/LoginPage';
import MyPage from './features/pages/components/MyPage';
import NotFoundPage from './features/pages/components/NotFoundPage';
import { InfoPage } from './features/pages/components/InfoPage';
import { NewsDetailPage } from './features/news/newsdetail/NewsDetailPage';
import { NewsListPage } from './features/news/newslist/NewsListPage';
import { RealEstateLoanRulePage } from './features/news/newslist/RealEstateLoanRulePage';
import SearchPage from './features/search/SearchPage';
import { StockListPage } from './features/stocks/StockListPage';
import { trackPageView } from './analytics';
import { AuthProvider } from './auth/AuthProvider';
import { Header } from './components/Header';
import { Footer } from './components/Footer';
import { useAuth } from './hooks/useLoginStatus';
import { AdminNewsFormPage } from './features/admin/AdminNewsFormPage';
import { AdminNewsPage, AdminAccessDeniedPage } from './features/admin/AdminNewsPage';
import { AdminShell } from './features/admin/components/AdminShell';
import './App.css';
import './features/admin/admin.css';

function AnalyticsTracker() {
  const location = useLocation();
  useEffect(() => {
    trackPageView(`${location.pathname}${location.search}`);
  }, [location.pathname, location.search]);
  return null;
}

function AppHeader() {
  const location = useLocation();
  const showHeader =
    location.pathname === '/' ||
    location.pathname.startsWith('/news') ||
    location.pathname.startsWith('/search') ||
    location.pathname.startsWith('/stocks') ||
    location.pathname.startsWith('/mypage') ||
    location.pathname.startsWith('/about') ||
    location.pathname.startsWith('/terms') ||
    location.pathname.startsWith('/privacy') ||
    location.pathname.startsWith('/standard');

  return showHeader ? <Header /> : null;
}

function AppFooter() {
  const location = useLocation();

  return location.pathname.startsWith('/admin') ? null : <Footer />;
}

function RequireAuth({ children }: { children: ReactNode }) {
  const { isLoggedIn, loading } = useAuth();

  if (loading) {
    return null;
  }

  if (!isLoggedIn) {
    return <Navigate to="/login" replace />;
  }

  return children;
}

function AdminRoute({ children }: { children: ReactNode }) {
  const { isLoggedIn, loading, role } = useAuth();

  if (loading) {
    return <main className="admin-state-page" aria-busy="true" />;
  }

  if (!isLoggedIn) {
    return <LoginPage />;
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

function PostLoginRedirect() {
  const location = useLocation();
  const navigate = useNavigate();
  const { isLoggedIn, loading } = useAuth();

  useEffect(() => {
    if (loading || !isLoggedIn) return;

    const targetPath = sessionStorage.getItem('daynomy:post-login-path');
    if (!targetPath || location.pathname === targetPath) return;

    sessionStorage.removeItem('daynomy:post-login-path');
    navigate(targetPath, { replace: true });
  }, [isLoggedIn, loading, location.pathname, navigate]);

  return null;
}

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AnalyticsTracker />
        <PostLoginRedirect />
        <div className="app-shell">
          <AppHeader />
          <div className="app-content">
            <Routes>
              <Route path="/" element={<NewsListPage />} />
              <Route path="/news/real-estate-loan-rule" element={<RealEstateLoanRulePage />} />
              <Route path="/news/:newsId" element={<NewsDetailPage />} />
              <Route path="/search" element={<SearchPage />} />
              <Route path="/stocks" element={<StockListPage />} />
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
              <Route path="/signup" element={<Navigate to="/login" replace />} />
              <Route
                path="/mypage"
                element={
                  <RequireAuth>
                    <MyPage />
                  </RequireAuth>
                }
              />
              <Route path="*" element={<NotFoundPage />} />
            </Routes>
          </div>
          <AppFooter />
        </div>
      </AuthProvider>
    </BrowserRouter>
  );
}
