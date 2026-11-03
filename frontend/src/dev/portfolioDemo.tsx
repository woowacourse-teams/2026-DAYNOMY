import { createRoot } from 'react-dom/client';
import { PortfolioPage } from '../features/portfolio/PortfolioPage';
import { portfolioDemoFetch } from './portfolioDemoData';
import '../index.css';
import '../App.css';
import '../features/admin/admin.css';
import './portfolioDemo.css';

if (import.meta.env.DEV) {
  // This document has its own fetch handler. The normal application and storage are untouched.
  window.fetch = portfolioDemoFetch;
  createRoot(document.getElementById('root')!).render(
    <>
      <aside className="portfolio-demo-notice" aria-label="데모 안내">
        <div>
          <strong>데모 데이터</strong>
          <span>가격·수량·거래 내역은 가상 예시예요. 필터·정렬·상세보기를 눌러보세요.</span>
        </div>
        <a href="/">실제 화면으로</a>
      </aside>
      <PortfolioPage />
    </>,
  );
} else {
  window.location.replace('/');
}
