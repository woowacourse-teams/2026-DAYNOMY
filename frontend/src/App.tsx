import { useEffect, type ReactNode } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
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

export default function App() {
  return (
    <BrowserRouter>
      <AuthProvider>
        <AnalyticsTracker />
        <AppHeader />
        <Routes>
          <Route path="/" element={<NewsListPage />} />
          <Route path="/news/real-estate-loan-rule" element={<RealEstateLoanRulePage />} />
          <Route path="/news/:newsId" element={<NewsDetailPage />} />
          <Route path="/search" element={<SearchPage />} />
          <Route path="/stocks" element={<StockListPage />} />
          <Route path="/login" element={<LoginPage />} />
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
      </AuthProvider>
    </BrowserRouter>
  );
}
