/** 서버·OAuth 주소나 외부 사이트 대신 같은 서비스의 화면으로만 돌아간다. */
export function safeReturnPath(value: string | null, fallback = '/') {
  if (
    !value?.startsWith('/') ||
    value.startsWith('//') ||
    value.includes('\\') ||
    /\p{Cc}/u.test(value)
  )
    return fallback;
  try {
    const url = new URL(value, window.location.origin);
    if (
      url.origin !== window.location.origin ||
      /^\/(?:api|oauth2|login)(?:\/|$)/.test(url.pathname)
    )
      return fallback;
    return `${url.pathname}${url.search}${url.hash}`;
  } catch {
    return fallback;
  }
}
