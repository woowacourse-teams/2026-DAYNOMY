import { useEffect, useMemo, useState, type ChangeEvent, type FormEvent } from 'react';
import { Link, useNavigate, useParams } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { ADMIN_NEWS_CATEGORIES, CATEGORY_LABELS } from './constants';
import { createAdminNews, getAdminNewsDetail, isSupportedNewsImage, updateAdminNews } from './api';
import type { AdminNewsFormValues } from './types';
import './admin.css';

const initialValues: AdminNewsFormValues = {
  title: '',
  content: '',
  sources: [{ name: '', url: '' }],
  category: '',
};

type FormErrors = Partial<Record<'title' | 'content' | 'category' | 'sources' | 'image', string>>;
type SourceErrors = Array<Partial<Record<'name' | 'url', string>>>;

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError || error instanceof Error ? error.message : fallback;
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
  const [errors, setErrors] = useState<FormErrors>({});
  const [sourceErrors, setSourceErrors] = useState<SourceErrors>([]);
  const [loading, setLoading] = useState(isEditing);
  const [submitting, setSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [detailLoadError, setDetailLoadError] = useState<string | null>(null);

  useEffect(() => {
    if (!isEditing || editingId === null) return;

    const controller = new AbortController();
    setLoading(true);
    setDetailLoadError(null);
    getAdminNewsDetail(editingId, controller.signal)
      .then((news) => {
        if (controller.signal.aborted) return;
        setValues({
          title: news.title,
          content: news.content,
          sources: news.sources.length > 0 ? news.sources : [{ name: '', url: '' }],
          category: news.category,
        });
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
    () => imagePreview ?? existingImageUrl,
    [existingImageUrl, imagePreview],
  );

  function updateField(field: Exclude<keyof AdminNewsFormValues, 'sources'>, value: string) {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
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
    const nextImage = event.target.files?.[0] ?? null;
    setImage(nextImage);
    setErrors((current) => ({ ...current, image: undefined }));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isEditing && detailLoadError) return;

    const validation = validateForm(values, image);
    setErrors(validation.errors);
    setSourceErrors(validation.sourceErrors);
    setErrorMessage(null);
    if (Object.keys(validation.errors).length > 0 || !values.category) return;

    setSubmitting(true);
    try {
      const normalizedValues = {
        ...values,
        title: values.title.trim(),
        sources: values.sources.map((source) => ({
          name: source.name.trim(),
          url: source.url.trim(),
        })),
      };
      if (isEditing && editingId !== null) {
        await updateAdminNews(editingId, normalizedValues, image);
      } else {
        await createAdminNews(normalizedValues, image);
      }
      navigate('/admin/news', { replace: true });
    } catch (error) {
      setErrorMessage(
        getErrorMessage(
          error,
          isEditing ? '뉴스를 수정하지 못했습니다.' : '뉴스를 등록하지 못했습니다.',
        ),
      );
    } finally {
      setSubmitting(false);
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
      <main className="admin-content admin-form-content">
        <section className="admin-state-panel" role="alert">
          <p className="admin-kicker">콘텐츠 운영</p>
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
    <main className="admin-content admin-form-content">
      <div className="admin-form-heading">
        <div>
          <Link className="admin-back-link" to="/admin/news">
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

      <form className="admin-news-form" onSubmit={handleSubmit} noValidate>
        <div className="admin-form-main">
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
        </div>

        <aside className="admin-form-side">
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

          <fieldset className="admin-sources">
            <legend>
              출처 <em>*</em>
            </legend>
            {values.sources.map((source, index) => {
              const itemErrors = sourceErrors[index] ?? {};
              const nameErrorId = `source-${index}-name-error`;
              const urlErrorId = `source-${index}-url-error`;

              return (
                <div className="admin-source-row" key={index}>
                  <div className="admin-source-heading">
                    <strong>출처 {index + 1}</strong>
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
            {errors.sources ? <small className="admin-field-error">{errors.sources}</small> : null}
            <button className="admin-source-add" type="button" onClick={addSource}>
              + 출처 추가
            </button>
          </fieldset>

          <div className="admin-field">
            <span>대표 이미지</span>
            <label className="admin-image-upload">
              {previewSource ? (
                <img src={previewSource} alt="뉴스 대표 이미지 미리보기" />
              ) : (
                <span>이미지를 선택해 주세요</span>
              )}
              <input
                type="file"
                accept="image/jpeg,image/png,image/webp"
                onChange={handleImageChange}
              />
            </label>
            <small className="admin-field-hint">JPG, PNG, WEBP · 최대 5MB</small>
            {errors.image ? <small className="admin-field-error">{errors.image}</small> : null}
          </div>

          <div className="admin-form-actions">
            <Link className="admin-secondary-button" to="/admin/news">
              취소
            </Link>
            <button className="admin-primary-button" type="submit" disabled={submitting}>
              {submitting ? '저장 중…' : isEditing ? '수정 저장' : '초안으로 등록'}
            </button>
          </div>
        </aside>
      </form>
    </main>
  );
}
