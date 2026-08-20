import { useEffect } from 'react';
import { BrowserRouter, Navigate, Route, Routes, useLocation } from 'react-router-dom';
import LoginPage from './features/pages/components/LoginPage';
import MyPage from './features/pages/components/MyPage';
import NotFoundPage from './features/pages/components/NotFoundPage';
import { NewsDetailPage } from './features/news/newsdetail/NewsDetailPage';
import { NewsListPage } from './features/news/newslist/NewsListPage';
import { RealEstateLoanRulePage } from './features/news/newslist/RealEstateLoanRulePage';
import SearchPage from './features/search/SearchPage';
import { trackPageView } from './analytics';

function AnalyticsTracker() {
  const location = useLocation();
  useEffect(() => {
    trackPageView(`${location.pathname}${location.search}`);
  }, [location.pathname, location.search]);
  return null;
}

export default function App() {
  return (
    <BrowserRouter>
      <AnalyticsTracker />
      <Routes>
        <Route path="/" element={<NewsListPage />} />
        <Route path="/news/real-estate-loan-rule" element={<RealEstateLoanRulePage />} />
        <Route path="/news/:newsId" element={<NewsDetailPage />} />
        <Route path="/search" element={<SearchPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<Navigate to="/login" replace />} />
        <Route path="/mypage" element={<MyPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </BrowserRouter>
  );
}
