import { ArrowRightIcon } from '@phosphor-icons/react/dist/csr/ArrowRight';
import cryingCat from '../../assets/daynomy-crying-cat.png';
import './stockNewsEmptyState.css';

export function StockNewsEmptyState({ title }: { title: string }) {
  return (
    <div className="stock-news-empty">
      <img src={cryingCat} alt="" width="180" height="198" />
      <div className="stock-news-empty-copy">
        <p className="stock-news-empty-title">{title}</p>
        <p className="stock-news-empty-description">
          기다리는 동안 다른 종목과 시장 소식을 둘러보세요.
        </p>
        <a className="stock-news-empty-link" href="/news">
          다른 소식 둘러보기
          <ArrowRightIcon size={20} aria-hidden="true" />
        </a>
      </div>
    </div>
  );
}
