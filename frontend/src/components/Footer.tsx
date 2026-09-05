import { Link } from 'react-router-dom';
import './Footer.css';

const footerItems = [
  { label: '회사소개', to: '/about' },
  { label: '이용약관', to: '/terms' },
  { label: '메일문의', href: 'mailto:paperchoigo@gmail.com' },
  { label: '개인정보처리방침', to: '/privacy' },
  { label: 'DAYNOMY Std.', to: '/standard' },
];

export function Footer() {
  return (
    <footer className="daynomy-footer">
      <nav className="footer-nav" aria-label="서비스 안내">
        {footerItems.map((item) =>
          item.to ? (
            <Link className="footer-link" to={item.to} key={item.label}>
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
