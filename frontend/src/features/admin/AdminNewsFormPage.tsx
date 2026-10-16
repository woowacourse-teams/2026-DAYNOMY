import {
  useEffect,
  useMemo,
  useRef,
  useState,
  type ChangeEvent,
  type FormEvent,
  type MouseEvent,
} from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { ADMIN_NEWS_CATEGORIES, CATEGORY_LABELS } from './constants';
import {
  createAdminNews,
  generateAdminNewsImage,
  getAdminNewsDetail,
  isSupportedNewsImage,
  searchWikimediaImages,
  updateAdminNews,
} from './api';
import { searchStocks } from '../stocks/api';
import type { StockSearchItem } from '../stocks/types';
import type { AdminNewsFormValues, AdminNewsStatus, AdminWikimediaImageCandidate } from './types';
import './admin.css';

const initialValues: AdminNewsFormValues = {
  title: '',
  content: '',
  sources: [{ name: '', url: '' }],
  category: '',
  imageSelection: null,
  assetIds: [],
};

type FormErrors = Partial<Record<'title' | 'content' | 'category' | 'sources' | 'image', string>>;
type SourceErrors = Array<Partial<Record<'name' | 'url', string>>>;

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError || error instanceof Error ? error.message : fallback;
}

function getSafeSourceUrl(value: string) {
  try {
    const url = new URL(value.trim());
    return url.protocol === 'http:' || url.protocol === 'https:' ? url.href : null;
  } catch {
    return null;
  }
}

function validateForm(values: AdminNewsFormValues, image: File | null) {
  const errors: FormErrors = {};
  const sourceErrors: SourceErrors = values.sources.map((source) => {
    const itemErrors: Partial<Record<'name' | 'url', string>> = {};
    if (!source.name.trim()) itemErrors.name = '출처명을 입력해 주세요.';
    if (!source.url.trim()) {
      itemErrors.url = '출처 URL을 입력해 주세요.';
    } else {
      try {
        const url = new URL(source.url.trim());
        if (url.protocol !== 'http:' && url.protocol !== 'https:') throw new Error();
      } catch {
        itemErrors.url = 'http:// 또는 https://로 시작하는 URL을 입력해 주세요.';
      }
    }
    return itemErrors;
  });
  if (values.sources.length === 0) errors.sources = '출처를 하나 이상 추가해 주세요.';
  if (sourceErrors.some((sourceError) => Object.keys(sourceError).length > 0)) {
    errors.sources = '출처명과 URL을 모두 입력해 주세요.';
  }
  if (!values.title.trim()) errors.title = '제목을 입력해 주세요.';
  if (!values.content.trim()) errors.content = '본문을 입력해 주세요.';
  if (!values.category) errors.category = '카테고리를 선택해 주세요.';
  if (image && !isSupportedNewsImage(image)) {
    errors.image = 'JPG, PNG, WEBP 형식의 5MB 이하 이미지만 업로드할 수 있습니다.';
  }
  return { errors, sourceErrors };
}

export function AdminNewsFormPage() {
  const navigate = useNavigate();
  const { newsId } = useParams();
  const editingId = newsId ? Number(newsId) : null;
  const isEditing = editingId !== null && Number.isInteger(editingId) && editingId > 0;
  const [values, setValues] = useState<AdminNewsFormValues>(initialValues);
  const [image, setImage] = useState<File | null>(null);
  const [imagePreview, setImagePreview] = useState<string | null>(null);
  const [existingImageUrl, setExistingImageUrl] = useState<string | null>(null);
  const [imageInputKey, setImageInputKey] = useState(0);
  const [imageKeyword, setImageKeyword] = useState('');
  const [imageCandidates, setImageCandidates] = useState<AdminWikimediaImageCandidate[]>([]);
  const [selectedImageCandidate, setSelectedImageCandidate] =
    useState<AdminWikimediaImageCandidate | null>(null);
  const [imageSearchStatus, setImageSearchStatus] = useState<
    'idle' | 'loading' | 'success' | 'error'
  >('idle');
  const [imageSearchError, setImageSearchError] = useState<string | null>(null);
  const [errors, setErrors] = useState<FormErrors>({});
  const [sourceErrors, setSourceErrors] = useState<SourceErrors>([]);
  const [loading, setLoading] = useState(isEditing);
  const [submitting, setSubmitting] = useState(false);
  const [bodyMode, setBodyMode] = useState<'write' | 'preview'>('write');
  const [imageMode, setImageMode] = useState<'upload' | 'search' | 'ai'>('upload');
  const [generatingImage, setGeneratingImage] = useState(false);
  const [imageNotice, setImageNotice] = useState<string | null>(null);
  const [newsStatus, setNewsStatus] = useState<AdminNewsStatus>('DRAFT');
  const persistedNewsId = useRef<number | null>(editingId);
  const [assetSearched, setAssetSearched] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [detailLoadError, setDetailLoadError] = useState<string | null>(null);
  const [assetSearchKeyword, setAssetSearchKeyword] = useState('');
  const [assetSearchResults, setAssetSearchResults] = useState<StockSearchItem[]>([]);
  const [selectedAssets, setSelectedAssets] = useState<StockSearchItem[]>([]);
  const [assetSearchLoading, setAssetSearchLoading] = useState(false);
  const [assetSearchError, setAssetSearchError] = useState<string | null>(null);
  const originalValues = useRef<AdminNewsFormValues>(initialValues);
  const imageSearchRequestId = useRef(0);
  const imageSearchController = useRef<AbortController | null>(null);
  const assetSearchRequestId = useRef(0);
  const assetSearchController = useRef<AbortController | null>(null);

  useEffect(() => {
    return () => {
      imageSearchController.current?.abort();
      assetSearchController.current?.abort();
    };
  }, []);

  useEffect(() => {
    if (!isEditing || editingId === null) return;

    const controller = new AbortController();
    setLoading(true);
    setDetailLoadError(null);
    getAdminNewsDetail(editingId, controller.signal)
      .then((news) => {
        if (controller.signal.aborted) return;
        const loadedValues: AdminNewsFormValues = {
          title: news.title,
          content: news.content,
          sources: news.sources.length > 0 ? news.sources : [{ name: '', url: '' }],
          category: news.category,
          imageSelection: null,
          assetIds: (news.relatedAssets ?? []).map((asset) => asset.assetId),
        };
        persistedNewsId.current = news.id;
        setNewsStatus(news.status);
        originalValues.current = loadedValues;
        setValues(loadedValues);
        setSelectedAssets(news.relatedAssets ?? []);
        setExistingImageUrl(news.imageUrl);
      })
      .catch((error) => {
        if (!controller.signal.aborted) {
          setDetailLoadError(getErrorMessage(error, '뉴스 정보를 불러오지 못했습니다.'));
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [editingId, isEditing]);

  useEffect(() => {
    if (!image) {
      setImagePreview(null);
      return;
    }

    const previewUrl = URL.createObjectURL(image);
    setImagePreview(previewUrl);
    return () => URL.revokeObjectURL(previewUrl);
  }, [image]);

  const previewSource = useMemo(
    () => imagePreview ?? selectedImageCandidate?.thumbnailUrl ?? existingImageUrl,
    [existingImageUrl, imagePreview, selectedImageCandidate],
  );

  const hasChanges =
    image !== null || JSON.stringify(values) !== JSON.stringify(originalValues.current);

  useEffect(() => {
    if (!hasChanges) return;
    const warnBeforeUnload = (event: BeforeUnloadEvent) => {
      event.preventDefault();
      event.returnValue = '';
    };
    window.addEventListener('beforeunload', warnBeforeUnload);
    return () => window.removeEventListener('beforeunload', warnBeforeUnload);
  }, [hasChanges]);

  function handleLeave(event: MouseEvent<HTMLAnchorElement>) {
    if (
      submitting ||
      (hasChanges &&
        !window.confirm(
          '저장하지 않은 내용이 있습니다. 변경 내용을 버리고 뉴스 목록으로 돌아갈까요?',
        ))
    ) {
      event.preventDefault();
    }
  }

  function updateField(
    field: Exclude<keyof AdminNewsFormValues, 'sources' | 'assetIds'>,
    value: string,
  ) {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  }

  async function searchRelatedAssets() {
    const keyword = assetSearchKeyword.trim();
    const requestId = assetSearchRequestId.current + 1;
    assetSearchRequestId.current = requestId;
    assetSearchController.current?.abort();
    assetSearchController.current = null;

    if (!keyword) {
      setAssetSearchError('종목명 또는 종목코드를 입력해 주세요.');
      setAssetSearchResults([]);
      setAssetSearchLoading(false);
      return;
    }

    setAssetSearchLoading(true);
    setAssetSearchError(null);
    const controller = new AbortController();
    assetSearchController.current = controller;
    try {
      const results = await searchStocks(keyword, controller.signal);
      if (requestId !== assetSearchRequestId.current) return;
      setAssetSearchResults(results);
      setAssetSearched(true);
    } catch (error) {
      if (controller.signal.aborted || requestId !== assetSearchRequestId.current) return;
      setAssetSearchResults([]);
      setAssetSearchError(getErrorMessage(error, '관련 종목을 검색하지 못했습니다.'));
    } finally {
      if (requestId === assetSearchRequestId.current) {
        setAssetSearchLoading(false);
        assetSearchController.current = null;
      }
    }
  }

  function addRelatedAsset(asset: StockSearchItem) {
    if (selectedAssets.some((selected) => selected.assetId === asset.assetId)) return;
    setSelectedAssets((current) => [...current, asset]);
    setValues((current) => ({
      ...current,
      assetIds: [...(current.assetIds ?? []), asset.assetId],
    }));
  }

  function removeRelatedAsset(assetId: number) {
    setSelectedAssets((current) => current.filter((asset) => asset.assetId !== assetId));
    setValues((current) => ({
      ...current,
      assetIds: (current.assetIds ?? []).filter((selectedId) => selectedId !== assetId),
    }));
  }

  function updateSource(index: number, field: 'name' | 'url', value: string) {
    setValues((current) => ({
      ...current,
      sources: current.sources.map((source, sourceIndex) =>
        sourceIndex === index ? { ...source, [field]: value } : source,
      ),
    }));
    setSourceErrors((current) =>
      current.map((sourceError, sourceIndex) =>
        sourceIndex === index ? { ...sourceError, [field]: undefined } : sourceError,
      ),
    );
    setErrors((current) => ({ ...current, sources: undefined }));
  }

  function addSource() {
    setValues((current) => ({
      ...current,
      sources: [...current.sources, { name: '', url: '' }],
    }));
    setSourceErrors((current) => [...current, {}]);
  }

  function removeSource(index: number) {
    if (values.sources.length === 1) return;
    setValues((current) => ({
      ...current,
      sources: current.sources.filter((_, sourceIndex) => sourceIndex !== index),
    }));
    setSourceErrors((current) => current.filter((_, sourceIndex) => sourceIndex !== index));
    setErrors((current) => ({ ...current, sources: undefined }));
  }

  function handleImageChange(event: ChangeEvent<HTMLInputElement>) {
    const nextImage = event.target.files?.[0];
    if (!nextImage) return;

    setImage(nextImage);
    setSelectedImageCandidate(null);
    setValues((current) => ({ ...current, imageSelection: null }));
    setErrors((current) => ({ ...current, image: undefined }));
  }

  async function handleWikimediaSearch() {
    const keyword = imageKeyword.trim();
    const requestId = imageSearchRequestId.current + 1;
    imageSearchRequestId.current = requestId;
    imageSearchController.current?.abort();
    imageSearchController.current = null;

    if (keyword.length < 2) {
      setImageSearchStatus('error');
      setImageSearchError('검색어를 두 글자 이상 입력해 주세요.');
      setImageCandidates([]);
      return;
    }

    setImageSearchStatus('loading');
    setImageSearchError(null);
    const controller = new AbortController();
    imageSearchController.current = controller;
    try {
      const candidates = await searchWikimediaImages(keyword, controller.signal);
      if (requestId !== imageSearchRequestId.current) return;
      setImageCandidates(candidates);
      setImageSearchStatus('success');
      if (candidates.length === 0) {
        setImageSearchError('사용할 수 있는 이미지가 없습니다. 다른 검색어를 입력해 주세요.');
      }
    } catch (error) {
      if (controller.signal.aborted || requestId !== imageSearchRequestId.current) return;
      setImageCandidates([]);
      setImageSearchStatus('error');
      setImageSearchError(getErrorMessage(error, 'Wikimedia Commons 검색에 실패했습니다.'));
    } finally {
      if (requestId === imageSearchRequestId.current) {
        imageSearchController.current = null;
      }
    }
  }

  function selectWikimediaImage(candidate: AdminWikimediaImageCandidate) {
    setImage(null);
    setImageInputKey((current) => current + 1);
    setSelectedImageCandidate(candidate);
    setValues((current) => ({
      ...current,
      imageSelection: { title: candidate.title },
    }));
    setErrors((current) => ({ ...current, image: undefined }));
  }

  function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    void saveNews(event.currentTarget);
  }

  async function saveNews(form: HTMLFormElement, generateImage = false) {
    if (submitting || (isEditing && detailLoadError)) return;

    if (generateImage && newsStatus !== 'DRAFT' && newsStatus !== 'PUBLISHED') return;
    const validation = validateForm(values, image);
    setErrors(validation.errors);
    setSourceErrors(validation.sourceErrors);
    setErrorMessage(null);
    if (Object.keys(validation.errors).length > 0 || !values.category) {
      setBodyMode('write');
      setErrorMessage('입력 내용을 확인해 주세요. 표시된 항목을 수정하면 저장할 수 있습니다.');
      setTimeout(() => form.querySelector<HTMLElement>('[aria-invalid="true"]')?.focus(), 0);
      return;
    }

    if (
      generateImage &&
      previewSource &&
      !window.confirm('현재 내용을 저장하고 대표 이미지를 AI 이미지로 교체할까요?')
    )
      return;
    setSubmitting(true);
    setImageNotice(null);
    let savedId: number | null = null;
    try {
      const normalizedValues = {
        ...values,
        title: values.title.trim(),
        sources: values.sources.map((source) => ({
          name: source.name.trim(),
          url: source.url.trim(),
        })),
      };
      const savedNews =
        persistedNewsId.current !== null
          ? await updateAdminNews(persistedNewsId.current, normalizedValues, image)
          : await createAdminNews(normalizedValues, image);
      savedId = savedNews.id;
      persistedNewsId.current = savedId;
      if (generateImage) {
        const savedValues = { ...normalizedValues, imageSelection: null };
        originalValues.current = savedValues;
        setValues(savedValues);
        setImage(null);
        setSelectedImageCandidate(null);
        setImageInputKey((current) => current + 1);
        setExistingImageUrl(savedNews.imageUrl);
        setGeneratingImage(true);
        const generatedNews = await generateAdminNewsImage(savedId);
        setExistingImageUrl(generatedNews.imageUrl);
        setImageNotice('AI 이미지를 생성해 대표 이미지로 저장했습니다.');
      } else {
        navigate('/admin/news', { replace: true });
      }
    } catch (error) {
      setErrorMessage(
        (generateImage && savedId !== null
          ? '뉴스 내용은 저장되었습니다. 이미지 생성 실패: '
          : '') +
          getErrorMessage(
            error,
            generateImage && savedId !== null
              ? '뉴스는 저장했지만 AI 이미지를 생성하지 못했습니다. 다시 시도해 주세요.'
              : isEditing
                ? '뉴스를 수정하지 못했습니다.'
                : '뉴스를 등록하지 못했습니다.',
          ),
      );
    } finally {
      setSubmitting(false);
      setGeneratingImage(false);
      if (generateImage && savedId !== null && !isEditing) {
        navigate(`/admin/news/${savedId}/edit`, { replace: true });
      }
    }
  }

  if (loading) {
    return (
      <main className="admin-content">
        <div className="admin-table-loading">뉴스 정보를 불러오는 중입니다.</div>
      </main>
    );
  }

  if (isEditing && detailLoadError) {
    return (
      <main className="admin-content admin-form-content admin-editor-page">
        <section className="admin-state-panel" role="alert">
          <h1>뉴스 정보를 불러오지 못했습니다.</h1>
          <p>{detailLoadError}</p>
          <Link className="admin-secondary-button" to="/admin/news">
            뉴스 관리로 돌아가기
          </Link>
        </section>
      </main>
    );
  }

  return (
    <main className="admin-content admin-form-content admin-editor-page">
      <div className="admin-form-heading">
        <div>
          <Link
            className="admin-back-link"
            to="/admin/news"
            onClick={handleLeave}
            aria-disabled={submitting}
          >
            ← 뉴스 관리
          </Link>
          <h1>{isEditing ? '뉴스 수정' : '새 뉴스 등록'}</h1>
          <p>
            {isEditing
              ? '뉴스 내용을 확인하고 수정하세요.'
              : '서비스에 등록할 뉴스 내용을 작성하세요.'}
          </p>
        </div>
      </div>

      {errorMessage ? (
        <section className="admin-alert" role="alert">
          <strong>{errorMessage}</strong>
        </section>
      ) : null}

      <nav className="admin-editor-outline" aria-label="뉴스 편집 항목">
        <a href="#news-writing">뉴스 내용</a>
        <a href="#news-sources">
          출처{' '}
          <span>
            {
              values.sources.filter((source) => source.name.trim() && getSafeSourceUrl(source.url))
                .length
            }
          </span>
        </a>
        <a href="#news-image">
          대표 이미지 <span>{previewSource ? '선택됨' : '선택 사항'}</span>
        </a>
        <a href="#news-assets">
          관련 종목 <span>{selectedAssets.length}</span>
        </a>
      </nav>
      <form className="admin-news-form" onSubmit={handleSubmit} noValidate>
        <fieldset className="admin-editor-fields" disabled={submitting}>
          <legend className="sr-only">뉴스 편집</legend>
          <div className="admin-form-main" id="news-writing">
            <div className="admin-editor-section-heading">
              <h2 className="admin-section-title">뉴스 내용</h2>
              <span>제목·카테고리·본문은 필수입니다</span>
            </div>
            <label className="admin-field">
              <span>
                제목 <em>*</em>
              </span>
              <input
                value={values.title}
                onChange={(event) => updateField('title', event.target.value)}
                placeholder="뉴스 제목을 입력해 주세요"
                aria-invalid={Boolean(errors.title)}
                aria-describedby={errors.title ? 'title-error' : undefined}
              />
              {errors.title ? (
                <small id="title-error" className="admin-field-error">
                  {errors.title}
                </small>
              ) : null}
            </label>

            <label className="admin-field">
              <span>
                카테고리 <em>*</em>
              </span>
              <select
                value={values.category}
                onChange={(event) => updateField('category', event.target.value)}
                aria-invalid={Boolean(errors.category)}
              >
                <option value="">카테고리를 선택해 주세요</option>
                {ADMIN_NEWS_CATEGORIES.map((option) => (
                  <option key={option.value} value={option.value}>
                    {CATEGORY_LABELS[option.value]}
                  </option>
                ))}
              </select>
              {errors.category ? (
                <small className="admin-field-error">{errors.category}</small>
              ) : null}
            </label>
            <div className="admin-body-toolbar">
              <div className="admin-workspace-switch" role="group" aria-label="본문 보기 방식">
                <button
                  type="button"
                  aria-pressed={bodyMode === 'write'}
                  onClick={() => setBodyMode('write')}
                >
                  작성
                </button>
                <button
                  type="button"
                  aria-pressed={bodyMode === 'preview'}
                  onClick={() => setBodyMode('preview')}
                >
                  미리보기
                </button>
              </div>
              <span>{values.content.length.toLocaleString()}자</span>
            </div>
            <label className="admin-field" hidden={bodyMode !== 'write'}>
              <span>
                본문 <em>*</em>
              </span>
              <textarea
                value={values.content}
                onChange={(event) => updateField('content', event.target.value)}
                placeholder="뉴스 본문을 입력해 주세요"
                rows={16}
                aria-invalid={Boolean(errors.content)}
                aria-describedby={errors.content ? 'content-error' : undefined}
              />
              {errors.content ? (
                <small id="content-error" className="admin-field-error">
                  {errors.content}
                </small>
              ) : null}
            </label>
            {bodyMode === 'preview' ? (
              <article className="admin-article-preview" aria-label="뉴스 본문 미리보기">
                <h2>{values.title.trim() || '제목을 입력해 주세요'}</h2>
                {values.content.trim() ? (
                  values.content
                    .split('\n')
                    .filter(Boolean)
                    .map((paragraph, index) => <p key={index}>{paragraph}</p>)
                ) : (
                  <p className="admin-field-hint">작성한 본문이 여기에 표시됩니다.</p>
                )}
              </article>
            ) : (
              <p className="admin-field-hint admin-editor-body-help">
                줄바꿈으로 문단을 나누세요. 미리보기에서 읽는 흐름을 확인할 수 있습니다.
              </p>
            )}
            <fieldset className="admin-sources" id="news-sources">
              <legend>
                출처 <em>*</em>
              </legend>
              <p className="admin-field-hint">내용을 확인할 수 있는 원문을 하나 이상 연결하세요.</p>
              {values.sources.map((source, index) => {
                const itemErrors = sourceErrors[index] ?? {};
                const nameErrorId = `source-${index}-name-error`;
                const urlErrorId = `source-${index}-url-error`;
                const sourceUrl = getSafeSourceUrl(source.url);

                return (
                  <div className="admin-source-row" key={index}>
                    <div className="admin-source-heading">
                      <strong>출처 {index + 1}</strong>
                      <div className="admin-source-actions">
                        {sourceUrl ? (
                          <a
                            className="admin-source-link"
                            href={sourceUrl}
                            target="_blank"
                            rel="noopener noreferrer"
                          >
                            원문 보기
                          </a>
                        ) : null}
                        <button
                          className="admin-source-remove"
                          type="button"
                          onClick={() => removeSource(index)}
                          disabled={values.sources.length === 1}
                          aria-label={`출처 ${index + 1} 삭제`}
                        >
                          삭제
                        </button>
                      </div>
                    </div>
                    <label className="admin-field">
                      <span>출처명</span>
                      <input
                        value={source.name}
                        onChange={(event) => updateSource(index, 'name', event.target.value)}
                        placeholder="예: 한국은행"
                        aria-invalid={Boolean(itemErrors.name)}
                        aria-describedby={itemErrors.name ? nameErrorId : undefined}
                      />
                      {itemErrors.name ? (
                        <small id={nameErrorId} className="admin-field-error">
                          {itemErrors.name}
                        </small>
                      ) : null}
                    </label>
                    <label className="admin-field">
                      <span>출처 URL</span>
                      <input
                        type="url"
                        value={source.url}
                        onChange={(event) => updateSource(index, 'url', event.target.value)}
                        placeholder="https://example.com/news"
                        aria-invalid={Boolean(itemErrors.url)}
                        aria-describedby={itemErrors.url ? urlErrorId : undefined}
                      />
                      {itemErrors.url ? (
                        <small id={urlErrorId} className="admin-field-error">
                          {itemErrors.url}
                        </small>
                      ) : null}
                    </label>
                  </div>
                );
              })}
              {errors.sources ? (
                <small className="admin-field-error">{errors.sources}</small>
              ) : null}
              <button className="admin-source-add" type="button" onClick={addSource}>
                + 출처 추가
              </button>
            </fieldset>
          </div>

          <aside className="admin-form-side">
            <section className="admin-editor-panel" id="news-image">
              <h2 className="admin-section-title">대표 이미지</h2>
              <p className="admin-field-hint">
                직접 올리거나 검색하고, AI로 생성할 수도 있습니다. 이미지는 선택 사항입니다.
              </p>

              <div className="admin-field">
                <div className="admin-editor-image-preview">
                  {previewSource ? (
                    <img src={previewSource} alt="뉴스 대표 이미지 미리보기" />
                  ) : (
                    <span>이미지를 선택해 주세요</span>
                  )}
                </div>
                <div
                  className="admin-method-switch"
                  role="group"
                  aria-label="대표 이미지 선택 방법"
                >
                  <button
                    type="button"
                    aria-pressed={imageMode === 'upload'}
                    onClick={() => setImageMode('upload')}
                  >
                    파일 업로드
                  </button>
                  <button
                    type="button"
                    aria-pressed={imageMode === 'search'}
                    onClick={() => setImageMode('search')}
                  >
                    이미지 검색
                  </button>
                  <button
                    type="button"
                    aria-pressed={imageMode === 'ai'}
                    onClick={() => setImageMode('ai')}
                  >
                    AI 생성
                  </button>
                </div>
                <label className="admin-editor-file" hidden={imageMode !== 'upload'}>
                  <span>이미지 파일 선택</span>
                  <input
                    key={imageInputKey}
                    type="file"
                    accept="image/jpeg,image/png,image/webp"
                    onChange={handleImageChange}
                  />
                </label>
                <small className="admin-field-hint">JPG, PNG, WEBP · 최대 5MB</small>
                {errors.image ? <small className="admin-field-error">{errors.image}</small> : null}
              </div>

              {imageNotice ? (
                <p className="admin-field-hint" role="status">
                  {imageNotice}
                </p>
              ) : null}
              <section
                className="admin-image-search"
                hidden={imageMode !== 'ai'}
                aria-labelledby="ai-image-title"
              >
                <strong id="ai-image-title">뉴스 내용으로 AI 이미지 생성</strong>
                <p>
                  제목·본문·카테고리·출처를 먼저 저장한 뒤 이미지를 생성합니다. 기존 대표 이미지는
                  교체되며, 발행된 뉴스에도 바로 반영됩니다.
                </p>
                {newsStatus === 'DRAFT' || newsStatus === 'PUBLISHED' ? (
                  <button
                    className="admin-secondary-button"
                    type="button"
                    onClick={(event) => {
                      if (event.currentTarget.form) void saveNews(event.currentTarget.form, true);
                    }}
                  >
                    {generatingImage
                      ? 'AI 이미지 생성 중…'
                      : submitting
                        ? '뉴스 저장 중…'
                        : '저장하고 AI 이미지 생성'}
                  </button>
                ) : (
                  <p className="admin-field-hint">
                    초안 또는 발행된 뉴스에서만 AI 이미지를 생성할 수 있습니다.
                  </p>
                )}
                {generatingImage ? (
                  <p className="admin-field-hint" role="status">
                    이미지를 만들고 있습니다. 완료되면 미리보기에 표시됩니다.
                  </p>
                ) : null}
              </section>

              <section
                className="admin-image-search"
                hidden={imageMode !== 'search'}
                aria-labelledby="wikimedia-image-search-title"
              >
                <div>
                  <strong id="wikimedia-image-search-title">Wikimedia Commons에서 찾기</strong>
                  <p>상업적 이용이 가능한 CC0·Public domain·CC BY 이미지만 검색합니다.</p>
                </div>
                <div className="admin-image-search-controls">
                  <label className="admin-field">
                    <span className="sr-only">이미지 검색어</span>
                    <input
                      value={imageKeyword}
                      onChange={(event) => setImageKeyword(event.target.value)}
                      onKeyDown={(event) => {
                        if (event.key === 'Enter') {
                          event.preventDefault();
                          void handleWikimediaSearch();
                        }
                      }}
                      placeholder="예: Seoul skyline, semiconductor factory"
                    />
                  </label>
                  <button
                    className="admin-secondary-button"
                    type="button"
                    onClick={() => void handleWikimediaSearch()}
                    disabled={imageSearchStatus === 'loading'}
                  >
                    {imageSearchStatus === 'loading' ? '검색 중…' : '검색'}
                  </button>
                </div>
                {imageSearchStatus === 'loading' ? (
                  <p className="admin-field-hint" role="status">
                    이미지를 검색하고 있습니다.
                  </p>
                ) : null}
                {imageSearchError ? (
                  <p className="admin-field-error" role="alert">
                    {imageSearchError}
                  </p>
                ) : null}
                {imageCandidates.length > 0 ? (
                  <div className="admin-image-candidates" aria-label="Wikimedia Commons 검색 결과">
                    {imageCandidates.map((candidate) => (
                      <button
                        className={`admin-image-candidate${
                          selectedImageCandidate?.title === candidate.title ? ' is-selected' : ''
                        }`}
                        type="button"
                        key={candidate.title}
                        aria-label={`Wikimedia 이미지 ${candidate.title} · ${
                          candidate.author || '저작자 정보 없음'
                        } · ${candidate.license}`}
                        onClick={() => selectWikimediaImage(candidate)}
                      >
                        <img src={candidate.thumbnailUrl} alt="" />
                        <span>{candidate.author || '저작자 정보 없음'}</span>
                        <small>{candidate.license}</small>
                      </button>
                    ))}
                  </div>
                ) : null}
                {selectedImageCandidate ? (
                  <p className="admin-field-hint">
                    선택됨: {selectedImageCandidate.author || selectedImageCandidate.title} ·{' '}
                    {selectedImageCandidate.license}
                  </p>
                ) : null}
              </section>
            </section>
            <fieldset className="admin-related-assets admin-editor-panel" id="news-assets">
              <legend>관련 종목</legend>
              <p className="admin-field-hint">
                발행 후 해당 종목의 정보 허브에 이 뉴스가 자동으로 표시됩니다.
              </p>
              <div className="admin-related-asset-search">
                <label className="admin-field">
                  <span className="sr-only">관련 종목 검색</span>
                  <input
                    value={assetSearchKeyword}
                    onChange={(event) => setAssetSearchKeyword(event.target.value)}
                    onKeyDown={(event) => {
                      if (event.key === 'Enter') {
                        event.preventDefault();
                        void searchRelatedAssets();
                      }
                    }}
                    placeholder="삼성전자 또는 005930"
                  />
                </label>
                <button
                  className="admin-secondary-button"
                  type="button"
                  onClick={() => void searchRelatedAssets()}
                  disabled={assetSearchLoading}
                >
                  {assetSearchLoading ? '종목 검색 중…' : '종목 검색'}
                </button>
              </div>
              {assetSearchError ? (
                <small className="admin-field-error">{assetSearchError}</small>
              ) : null}
              {assetSearched &&
              !assetSearchLoading &&
              !assetSearchError &&
              assetSearchResults.length === 0 ? (
                <p className="admin-field-hint">
                  검색 결과가 없습니다. 종목명이나 코드를 바꿔 검색해 주세요.
                </p>
              ) : null}
              <p className="admin-field-hint">선택한 종목 {selectedAssets.length} / 20</p>
              {assetSearchResults.length > 0 ? (
                <ul className="admin-related-asset-results">
                  {assetSearchResults.map((asset) => {
                    const selected = selectedAssets.some(
                      (selectedAsset) => selectedAsset.assetId === asset.assetId,
                    );
                    return (
                      <li key={asset.assetId}>
                        <button
                          type="button"
                          onClick={() => addRelatedAsset(asset)}
                          disabled={selected || selectedAssets.length >= 20}
                        >
                          <strong>{asset.name}</strong>
                          <span>
                            {asset.assetCode} · {asset.market}
                          </span>
                          <em>{selected ? '선택됨' : '추가'}</em>
                        </button>
                      </li>
                    );
                  })}
                </ul>
              ) : null}
              {selectedAssets.length > 0 ? (
                <ul className="admin-selected-assets" aria-label="선택한 관련 종목">
                  {selectedAssets.map((asset) => (
                    <li key={asset.assetId}>
                      <span>
                        {asset.name} · {asset.assetCode}
                      </span>
                      <button
                        type="button"
                        onClick={() => removeRelatedAsset(asset.assetId)}
                        aria-label={`${asset.name} 관련 종목 선택 해제`}
                      >
                        ×
                      </button>
                    </li>
                  ))}
                </ul>
              ) : null}
            </fieldset>
          </aside>
          <div className="admin-form-actions">
            <p className="admin-save-hint">
              {isEditing
                ? '변경한 내용을 저장합니다.'
                : '초안으로 저장됩니다. 발행은 뉴스 목록에서 진행하세요.'}
            </p>
            <Link
              className="admin-secondary-button"
              to="/admin/news"
              onClick={handleLeave}
              aria-disabled={submitting}
            >
              취소
            </Link>
            <button className="admin-primary-button" type="submit" disabled={submitting}>
              {submitting ? '저장 중…' : isEditing ? '수정 저장' : '초안으로 등록'}
            </button>
          </div>
        </fieldset>
      </form>
    </main>
  );
}
