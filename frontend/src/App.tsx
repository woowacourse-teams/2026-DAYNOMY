import { BrowserRouter, Route, Routes } from "react-router-dom";
import LoginPage from "./feature/pages/components/LoginPage";
import MyPage from "./feature/pages/components/MyPage";
import NotFoundPage from "./feature/pages/components/NotFoundPage";
import SignupPage from "./feature/pages/components/SignupPage";
import { NewsDetailPage } from "./features/news/newsdetail/NewsDetailPage";
import { NewsListPage } from "./features/news/newslist/NewsListPage";
import { RealEstateLoanRulePage } from "./features/news/newslist/RealEstateLoanRulePage";
import SearchPage from "./features/search/SearchPage";

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<NewsListPage />} />
        <Route path="/news/real-estate-loan-rule" element={<RealEstateLoanRulePage />} />
        <Route path="/news/:newsId" element={<NewsDetailPage />} />
        <Route path="/search" element={<SearchPage />} />
        <Route path="/login" element={<LoginPage />} />
        <Route path="/signup" element={<SignupPage />} />
        <Route path="/mypage" element={<MyPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Routes>
    </BrowserRouter>
  );
}
