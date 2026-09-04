import { useEffect, type ReactNode } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation, useNavigate } from 'react-router-dom';
import LoginPage from './features/pages/components/LoginPage';
import MyPage from './features/pages/components/MyPage';
import NotFoundPage from './features/pages/components/NotFoundPage';
import { NewsDetailPage } from './features/news/newsdetail/NewsDetailPage';
import { NewsListPage } from './features/news/newslist/NewsListPage';
import { RealEstateLoanRulePage } from './features/news/newslist/RealEstateLoanRulePage';
import SearchPage from './features/search/SearchPage';
import { StockListPage } from './features/stocks/StockListPage';
import { trackPageView } from './analytics';
import { AuthProvider } from './auth/AuthProvider';
import { Header } from './components/Header';
import { useAuth } from './hooks/useLoginStatus';
import { AdminNewsFormPage } from './features/admin/AdminNewsFormPage';
import { AdminNewsPage, AdminAccessDeniedPage } from './features/admin/AdminNewsPage';
import { AdminShell } from './features/admin/components/AdminShell';
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
    location.pathname.startsWith('/mypage');

  return showHeader ? <Header /> : null;
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
        <AppHeader />
        <Routes>
          <Route path="/" element={<NewsListPage />} />
          <Route path="/news/real-estate-loan-rule" element={<RealEstateLoanRulePage />} />
          <Route path="/news/:newsId" element={<NewsDetailPage />} />
          <Route path="/search" element={<SearchPage />} />
          <Route path="/stocks" element={<StockListPage />} />
          <Route path="/login" element={<LoginPage />} />
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
          <Route path="/mypage" element={<MyPage />} />
          <Route path="*" element={<NotFoundPage />} />
        </Routes>
      </AuthProvider>
    </BrowserRouter>
  );
}
