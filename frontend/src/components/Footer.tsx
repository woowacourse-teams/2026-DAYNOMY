import { Link } from 'react-router-dom';
import './Footer.css';

const footerItems = [
  { label: '회사소개', to: '/about' },
  { label: '이용약관', to: '/terms' },
  { label: '개인정보처리방침', to: '/privacy', emphasized: true },
  { label: '문의하기', href: 'mailto:paperchoigo@gmail.com' },
];

export function Footer() {
  return (
    <footer className="daynomy-footer">
      <div className="footer-info">
        <strong className="footer-brand">DAYNOMY</strong>
        <p className="footer-description">
          <span>매일의 경제를 더 선명하게</span>
          <span>© 2026 DAYNOMY</span>
        </p>
      </div>
      <nav className="footer-nav" aria-label="서비스 안내">
        {footerItems.map((item) =>
          item.to ? (
            <Link
              className={item.emphasized ? 'footer-link is-emphasized' : 'footer-link'}
              to={item.to}
              key={item.label}
            >
              {item.label}
            </Link>
          ) : (
            <a className="footer-link" href={item.href} key={item.label}>
              {item.label}
            </a>
          ),
        )}
      </nav>
    </footer>
  );
}
