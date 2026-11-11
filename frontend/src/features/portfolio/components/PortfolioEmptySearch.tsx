import { ArrowRightIcon } from '@phosphor-icons/react/dist/csr/ArrowRight';
import { MagnifyingGlassIcon } from '@phosphor-icons/react/dist/csr/MagnifyingGlass';
import './portfolioEmptySearch.css';

export function PortfolioEmptySearch({
  disabled,
  onOpen,
}: {
  disabled: boolean;
  onOpen: () => void;
}) {
  return (
    <section className="portfolio-empty-search" aria-labelledby="portfolio-empty-search-title">
      <MagnifyingGlassIcon className="portfolio-empty-search-mark" size={30} aria-hidden="true" />
      <h3 id="portfolio-empty-search-title">보유 중인 자산이 없어요</h3>
      <button
        type="button"
        className="portfolio-empty-search-field"
        aria-haspopup="dialog"
        disabled={disabled}
        onClick={(event) => {
          event.currentTarget.focus();
          onOpen();
        }}
      >
        <MagnifyingGlassIcon size={18} aria-hidden="true" />
        <span>종목명 또는 티커 검색</span>
        <ArrowRightIcon size={18} aria-hidden="true" />
      </button>
    </section>
  );
}
