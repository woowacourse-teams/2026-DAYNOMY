import './Footer.css';

const footerItems = ['회사소개', '이용약관', '메일문의', '개인정보처리방침', 'DAYNOMY Std.'];

export function Footer() {
  return (
    <footer className="daynomy-footer">
      <nav className="footer-nav" aria-label="서비스 안내">
        {footerItems.map((item) =>
          item === '메일문의' ? (
            <a className="footer-link" href="mailto:paperchoigo@gmail.com" key={item}>
              {item}
            </a>
          ) : (
            <span className="footer-link" key={item}>
              {item}
            </span>
          ),
        )}
      </nav>
    </footer>
  );
}
