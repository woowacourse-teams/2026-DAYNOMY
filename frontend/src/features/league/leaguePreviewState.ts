import type { SourceHolding } from './sharedPortfolioApi';
import { safeReturnPath } from '../../auth/returnPath';

export type PreviewViewer = 'GUEST' | 'MEMBER';
export type PreviewScenario = 'FILLED' | 'EMPTY' | 'ERROR';
const prefix = 'daynomy:league-preview:';

export function canPreviewLeague() {
  return (
    import.meta.env?.DEV && ['localhost', '127.0.0.1', '[::1]'].includes(window.location.hostname)
  );
}

export function isLeaguePreview() {
  return canPreviewLeague() && sessionStorage.getItem(`${prefix}enabled`) === '1';
}

export function enableLeaguePreview() {
  if (!canPreviewLeague()) return;
  sessionStorage.setItem(`${prefix}enabled`, '1');
}

export function getPreviewViewer(): PreviewViewer {
  const value = sessionStorage.getItem(`${prefix}viewer`);
  return value === 'GUEST' ? 'GUEST' : 'MEMBER';
}

export function getPreviewScenario(): PreviewScenario {
  const value = sessionStorage.getItem(`${prefix}scenario`);
  return value === 'EMPTY' || value === 'ERROR' ? value : 'FILLED';
}

export function setPreviewViewer(value: PreviewViewer) {
  sessionStorage.setItem(`${prefix}viewer`, value);
}

export function isPreviewRoute(pathname: string) {
  return (
    pathname === '/league' ||
    pathname.startsWith('/league/') ||
    ['/portfolio/records', '/portfolio/publication', '/membership', '/mypage', '/login'].includes(
      pathname,
    )
  );
}

/** 로그인 복귀는 미리보기 내부 화면만 허용한다. */
export function previewReturnPath(value: string | null): string {
  if (!value?.startsWith('/') || value.startsWith('//')) return '/league';
  try {
    const url = new URL(value, window.location.origin);
    if (
      url.origin !== window.location.origin ||
      !isPreviewRoute(url.pathname) ||
      url.pathname === '/login'
    )
      return '/league';
    url.searchParams.delete('leaguePreview');
    return `${url.pathname}${url.search}${url.hash}`;
  } catch {
    return '/league';
  }
}

export function setPreviewScenario(value: PreviewScenario) {
  sessionStorage.setItem(`${prefix}scenario`, value);
}

export function clearLeaguePreview() {
  Object.keys(sessionStorage)
    .filter((key) => key.startsWith(prefix))
    .forEach((key) => sessionStorage.removeItem(key));
}

/** 실제 OAuth로 이동하기 전에 가상 세션을 종료한다. 데모 투자자는 실제 계정에 없다. */
export function prepareRealLogin(value: string | null) {
  const url = new URL(safeReturnPath(value, '/league'), window.location.origin);
  const realPages = [
    '/league',
    '/league/portfolio',
    '/league/following',
    '/portfolio/records',
    '/portfolio/publication',
    '/mypage',
  ];
  url.searchParams.delete('leaguePreview');
  const returnTo = realPages.includes(url.pathname)
    ? `${url.pathname}${url.search}${url.hash}`
    : '/league';
  clearLeaguePreview();
  sessionStorage.setItem('daynomy:post-login-path', returnTo);
}

/** 원본 포트폴리오 대신 사용할 합성 자산. 실제 시세가 아니다. */
export function previewSourceHoldings(): SourceHolding[] {
  return [
    {
      assetId: 901,
      assetCode: '005930',
      assetName: '삼성전자',
      category: 'STOCK',
      market: 'KOSPI',
      quantity: 10,
      averagePurchasePrice: 70000,
    },
    {
      assetId: 902,
      assetCode: '069500',
      assetName: 'KODEX 200',
      category: 'ETF',
      market: 'KOSPI',
      quantity: 20,
      averagePurchasePrice: 35000,
    },
  ];
}
