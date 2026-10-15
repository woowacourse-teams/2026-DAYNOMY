export const SERVICE_STARTED_KEY = 'daynomy:service-started:v1';

export function hasStartedService() {
  try {
    if (localStorage.getItem(SERVICE_STARTED_KEY) === '1') return true;
  } catch {
    // 영구 저장소가 차단되어도 현재 탭의 기록으로 계속 이용한다.
  }
  try {
    return sessionStorage.getItem(SERVICE_STARTED_KEY) === '1';
  } catch {
    return false;
  }
}

export function rememberServiceStart() {
  try {
    localStorage.setItem(SERVICE_STARTED_KEY, '1');
    return;
  } catch {
    // 영구 저장소를 쓸 수 없으면 현재 탭에서 소개 반복을 막는다.
  }
  try {
    sessionStorage.setItem(SERVICE_STARTED_KEY, '1');
  } catch {
    // 저장이 모두 차단되어도 현재 화면의 서비스 진입은 허용한다.
  }
}
