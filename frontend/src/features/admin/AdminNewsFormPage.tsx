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
  description: '',
  sourceUrl: '',
  category: '',
};

type FormErrors = Partial<Record<keyof AdminNewsFormValues | 'image', string>>;

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError || error instanceof Error ? error.message : fallback;
}

function validateForm(values: AdminNewsFormValues, image: File | null): FormErrors {
  const errors: FormErrors = {};
  if (!values.title.trim()) errors.title = '제목을 입력해 주세요.';
  if (!values.content.trim()) errors.content = '본문을 입력해 주세요.';
  if (!values.sourceUrl.trim()) {
    errors.sourceUrl = '원문 URL을 입력해 주세요.';
  } else {
    try {
      const url = new URL(values.sourceUrl.trim());
      if (url.protocol !== 'http:' && url.protocol !== 'https:') throw new Error();
    } catch {
      errors.sourceUrl = 'http:// 또는 https://로 시작하는 URL을 입력해 주세요.';
    }
  }
  if (!values.category) errors.category = '카테고리를 선택해 주세요.';
  if (image && !isSupportedNewsImage(image)) {
    errors.image = 'JPG, PNG, WEBP 형식의 5MB 이하 이미지만 업로드할 수 있습니다.';
  }
  return errors;
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
          description: news.description ?? '',
          sourceUrl: news.sourceUrl,
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

  function updateField(field: keyof AdminNewsFormValues, value: string) {
    setValues((current) => ({ ...current, [field]: value }));
    setErrors((current) => ({ ...current, [field]: undefined }));
  }

  function handleImageChange(event: ChangeEvent<HTMLInputElement>) {
    const nextImage = event.target.files?.[0] ?? null;
    setImage(nextImage);
    setErrors((current) => ({ ...current, image: undefined }));
  }

  async function handleSubmit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (isEditing && detailLoadError) return;

    const nextErrors = validateForm(values, image);
    setErrors(nextErrors);
    setErrorMessage(null);
    if (Object.keys(nextErrors).length > 0 || !values.category) return;

    setSubmitting(true);
    try {
      const normalizedValues = {
        ...values,
        title: values.title.trim(),
        sourceUrl: values.sourceUrl.trim(),
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
            <span>요약 설명</span>
            <input
              value={values.description}
              onChange={(event) => updateField('description', event.target.value)}
              placeholder="목록에 보여줄 뉴스 요약을 입력해 주세요"
            />
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

          <label className="admin-field">
            <span>
              원문 URL <em>*</em>
            </span>
            <input
              type="url"
              value={values.sourceUrl}
              onChange={(event) => updateField('sourceUrl', event.target.value)}
              placeholder="https://example.com/news"
              aria-invalid={Boolean(errors.sourceUrl)}
              aria-describedby={errors.sourceUrl ? 'source-url-error' : undefined}
            />
            {errors.sourceUrl ? (
              <small id="source-url-error" className="admin-field-error">
                {errors.sourceUrl}
              </small>
            ) : null}
          </label>

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
