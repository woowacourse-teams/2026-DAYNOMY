import { useState } from 'react';
import defaultNewsImage from '../../assets/default-news-real-estate.webp';
import './card-design-preview.css';

type PreviewCardType = 'issue' | 'youtube';

type PreviewCard = {
  type: PreviewCardType;
  title: string;
  meta: string;
  image: string;
};

type PreviewDesign = {
  number: string;
  name: string;
  description: string;
  className: string;
};

const previewCards: PreviewCard[] = [
  {
    type: 'issue',
    title: '한미 정상회담서 군함 건조 협력 부각…한화오션·조선 방산주 수주 기대',
    meta: 'DAYNOMY 이슈 · 10분 전',
    image: defaultNewsImage,
  },
  {
    type: 'youtube',
    title: '삼성전자 005930 뉴스 요약 #Shorts',
    meta: 'YouTube · 반도체 주식 전문',
    image: 'https://i.ytimg.com/vi/5qRYLp9z05M/hqdefault.jpg',
  },
];

const previewDesigns: PreviewDesign[] = [
  { number: '01', name: '매거진', description: '큰 이미지와 단정한 캡션', className: 'editorial' },
  {
    number: '02',
    name: '이미지 오버레이',
    description: '제목을 이미지 위에 배치',
    className: 'overlay',
  },
  { number: '03', name: '미니멀 라인', description: '얇은 선과 낮은 대비', className: 'minimal' },
  { number: '04', name: '다크 미디어', description: '영상 콘텐츠에 집중', className: 'dark' },
  { number: '05', name: '스플릿', description: '미디어와 텍스트를 좌우 분리', className: 'split' },
  { number: '06', name: '태그 강조', description: '자료 종류를 먼저 인식', className: 'tagged' },
  { number: '07', name: '번호 매김', description: '자료를 순서대로 탐색', className: 'numbered' },
  {
    number: '08',
    name: '액션 버튼',
    description: '읽기와 재생을 명확하게 유도',
    className: 'action',
  },
  { number: '09', name: '프레임드', description: '브랜드 보드처럼 정돈', className: 'framed' },
  {
    number: '10',
    name: '피처드',
    description: '콘텐츠를 크게 보여주는 구성',
    className: 'featured',
  },
];

function PreviewMedia({
  card,
  design,
  index,
}: {
  card: PreviewCard;
  design: PreviewDesign;
  index: number;
}) {
  const [source, setSource] = useState(card.image);

  return (
    <div className="card-preview-media">
      <img
        src={source}
        alt=""
        onError={() => {
          if (source !== defaultNewsImage) setSource(defaultNewsImage);
        }}
      />
      {card.type === 'youtube' ? (
        <span className="card-preview-play" aria-hidden="true">
          ▶
        </span>
      ) : null}
      {design.className === 'numbered' ? (
        <span className="card-preview-index">{String(index + 1).padStart(2, '0')}</span>
      ) : null}
    </div>
  );
}

function PreviewCardItem({
  card,
  design,
  index,
}: {
  card: PreviewCard;
  design: PreviewDesign;
  index: number;
}) {
  return (
    <article className={`card-preview-card card-preview-card--${design.className}`}>
      <PreviewMedia card={card} design={design} index={index} />
      <div className="card-preview-copy">
        <span className="card-preview-eyebrow">
          {card.type === 'issue' ? 'DAYNOMY 이슈' : 'YouTube'}
        </span>
        <strong>{card.title}</strong>
        <span className="card-preview-meta">{card.meta}</span>
        {design.className === 'action' ? (
          <span className="card-preview-action">
            {card.type === 'issue' ? '이슈 읽기' : '영상 보기'} →
          </span>
        ) : null}
      </div>
    </article>
  );
}

export function CardDesignPreviewPage() {
  return (
    <main className="card-preview-page">
      <div className="card-preview-container">
        <header className="card-preview-header">
          <span className="card-preview-kicker">DAYNOMY · CARD LAB</span>
          <h1>카드 디자인 10가지</h1>
          <p>같은 이슈와 영상을 서로 다른 카드 시스템으로 비교해보세요.</p>
        </header>

        <div className="card-preview-list">
          {previewDesigns.map((design) => (
            <section className="card-preview-option" key={design.className}>
              <div className="card-preview-option-heading">
                <div>
                  <span>{design.number}</span>
                  <h2>{design.name}</h2>
                </div>
                <p>{design.description}</p>
              </div>
              <div className={`card-preview-grid card-preview-grid--${design.className}`}>
                {previewCards.map((card, index) => (
                  <PreviewCardItem card={card} design={design} index={index} key={card.type} />
                ))}
              </div>
            </section>
          ))}
        </div>
      </div>
    </main>
  );
}
