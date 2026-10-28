# Design QA — 소식 홈 #695 트렌딩 하단 정렬

- source visual truth path: `/private/tmp/news-spacing-after.png`
- implementation URL: `http://127.0.0.1:4173/news?preview=briefing&data=example`
- implementation screenshot path: `/private/tmp/news-trending-aligned.png`
- source pixels: 1596 × 1000
- implementation pixels: 1596 × 1000
- CSS viewport: 1227 × 769, device pixel ratio 1.3, 동일 브라우저·동일 상태에서 전후 캡처
- state: 예시 데이터가 표시된 소식 홈 상단

## Full-view comparison evidence

- 변경 전·후 캡처를 같은 비교 입력에서 함께 확인했다.
- 변경 전에는 트렌딩 01 하단이 트렌딩 03 하단보다 약 43px 아래에 있었다.
- 변경 후 트렌딩 01 이미지 카드와 트렌딩 03 카드의 하단이 동일 선상에 놓이고, 다음 `내 종목 소식` 영역과의 여백도 좌우에서 균일하다.

## Focused region comparison evidence

- 브라우저 좌표 측정 결과 트렌딩 01과 트렌딩 03의 `bottom` 차이는 0.00002px 미만이다.
- 데스크톱 트렌딩 01 높이는 고정 450px에서 오른쪽 기사 묶음의 자연 높이인 약 406.6px로 줄었다.
- 600px 이하 반응형에서는 기존 360px 최소 높이 규칙이 유지된다.

## Required fidelity surfaces

- fonts and typography: 글꼴, 크기, 굵기, 줄바꿈 규칙을 변경하지 않았다.
- spacing and layout rhythm: 데스크톱 고정 최소 높이만 제거해 두 열의 콘텐츠 하단을 맞췄다. 카드 내부 패딩과 열 간격은 유지했다.
- colors and visual tokens: 색상, 오버레이, 구분선 토큰을 변경하지 않았다.
- image quality and asset fidelity: 원본 이미지와 `object-fit: cover`를 유지하고 카드 프레임 높이만 줄였다.
- copy and content: 기사 제목, 설명, 메타데이터를 변경하지 않았다.

## Findings

- 남은 P0/P1/P2 불일치 없음.

## Comparison history

1. 변경 전 데스크톱 카드가 450px 고정 최소 높이를 사용해 오른쪽 트렌딩 03보다 약 43px 더 아래로 내려가는 P2 정렬 차이가 있었다.
2. 데스크톱 트렌딩 그리드와 메인 카드의 고정 최소 높이를 제거하고 오른쪽 콘텐츠의 자연 높이를 따르게 했다.
3. 후속 캡처와 DOM 좌표 측정에서 두 카드 하단 차이가 사실상 0px임을 확인했다.

## Implementation checklist

- [x] 트렌딩 01 높이를 오른쪽 기사 묶음에 맞춤
- [x] 트렌딩 03 하단과 좌표 비교
- [x] 모바일 최소 높이 유지
- [x] 브라우저 콘솔 오류 없음

final result: passed
