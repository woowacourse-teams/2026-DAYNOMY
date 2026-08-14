import { BrowserRouter, Route, Routes } from "react-router-dom";
import { Header } from "./components/Header";
import LoginPage from "./feature/pages/components/LoginPage";
import MyPage from "./feature/pages/components/MyPage";
import NotFoundPage from "./feature/pages/components/NotFoundPage";
import SignupPage from "./feature/pages/components/SignupPage";
import { NewsDetailPage } from "./features/news/newsdetail/NewsDetailPage";
import SearchPage from "./features/search/SearchPage";

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<Header />} />
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
