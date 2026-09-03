import { useEffect, useMemo, useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { ApiError } from '../../api/client';
import { formatDate } from '../news/newslist/utils';
import {
  ADMIN_NEWS_CATEGORY_OPTIONS,
  ADMIN_NEWS_STATUS_OPTIONS,
  CATEGORY_LABELS,
  NEWS_STATUS_LABELS,
} from './constants';
import { deleteAdminNews, getAdminNews } from './api';
import type {
  AdminNewsFilterCategory,
  AdminNewsFilterStatus,
  AdminNewsListItemResponse,
} from './types';
import './admin.css';

function getErrorMessage(error: unknown, fallback: string) {
  return error instanceof ApiError || error instanceof Error ? error.message : fallback;
}

function getPaginationPages(page: number, totalPages: number) {
  const visiblePageCount = Math.min(totalPages, 5);
  const firstPage = Math.min(
    Math.max(page - Math.floor(visiblePageCount / 2), 1),
    Math.max(totalPages - visiblePageCount + 1, 1),
  );

  return Array.from({ length: visiblePageCount }, (_, index) => firstPage + index);
}

function StatusBadge({ status }: { status: AdminNewsListItemResponse['status'] }) {
  return (
    <span className={`admin-status admin-status-${status.toLowerCase()}`}>
      {NEWS_STATUS_LABELS[status]}
    </span>
  );
}

function AdminAccessDeniedPage() {
  return (
    <main className="admin-state-page">
      <section className="admin-state-panel">
        <p className="admin-kicker">DAYNOMY 관리자</p>
        <h1>관리자 권한이 필요합니다.</h1>
        <p>관리자 계정으로 로그인한 뒤 다시 시도해 주세요.</p>
        <Link className="admin-secondary-button" to="/">
          서비스 홈으로
        </Link>
      </section>
    </main>
  );
}

export { AdminAccessDeniedPage };

export function AdminNewsPage() {
  const navigate = useNavigate();
  const [items, setItems] = useState<AdminNewsListItemResponse[]>([]);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [status, setStatus] = useState<AdminNewsFilterStatus>('ALL');
  const [category, setCategory] = useState<AdminNewsFilterCategory>('ALL');
  const [loading, setLoading] = useState(true);
  const [deletingId, setDeletingId] = useState<number | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setErrorMessage(null);

    getAdminNews(page, status, category, controller.signal)
      .then((response) => {
        if (controller.signal.aborted) return;
        setItems(response.items);
        setTotalPages(Math.max(1, response.totalPages));
        setTotalElements(response.totalElements);
      })
      .catch((error) => {
        if (controller.signal.aborted) return;
        setItems([]);
        setTotalPages(1);
        setTotalElements(0);
        setErrorMessage(getErrorMessage(error, '관리자 뉴스 목록을 불러오지 못했습니다.'));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [category, page, reloadKey, status]);

  const paginationPages = useMemo(() => getPaginationPages(page, totalPages), [page, totalPages]);

  function handleStatusChange(nextStatus: AdminNewsFilterStatus) {
    setStatus(nextStatus);
    setPage(1);
  }

  function handleCategoryChange(nextCategory: AdminNewsFilterCategory) {
    setCategory(nextCategory);
    setPage(1);
  }

  async function handleDelete(item: AdminNewsListItemResponse) {
    if (!window.confirm(`'${item.title}' 뉴스를 삭제할까요?`)) return;

    setDeletingId(item.id);
    setErrorMessage(null);
    try {
      await deleteAdminNews(item.id);
      setReloadKey((current) => current + 1);
    } catch (error) {
      setErrorMessage(getErrorMessage(error, '뉴스를 삭제하지 못했습니다.'));
    } finally {
      setDeletingId(null);
    }
  }

  return (
    <main className="admin-content">
      <div className="admin-page-heading">
        <div>
          <p className="admin-kicker">콘텐츠 운영</p>
          <h1>뉴스 관리</h1>
          <p>뉴스를 등록하고 서비스에 노출되는 콘텐츠를 관리하세요.</p>
        </div>
        <Link className="admin-primary-button" to="/admin/news/new">
          새 뉴스 등록
        </Link>
      </div>

      <section className="admin-toolbar" aria-label="뉴스 목록 필터">
        <label>
          <span>상태</span>
          <select
            value={status}
            onChange={(event) => handleStatusChange(event.target.value as AdminNewsFilterStatus)}
          >
            {ADMIN_NEWS_STATUS_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
        <label>
          <span>카테고리</span>
          <select
            value={category}
            onChange={(event) =>
              handleCategoryChange(event.target.value as AdminNewsFilterCategory)
            }
          >
            {ADMIN_NEWS_CATEGORY_OPTIONS.map((option) => (
              <option key={option.value} value={option.value}>
                {option.label}
              </option>
            ))}
          </select>
        </label>
        <p className="admin-total-count" aria-live="polite">
          전체 <strong>{totalElements.toLocaleString('ko-KR')}</strong>건
        </p>
      </section>

      {errorMessage ? (
        <section className="admin-alert" role="alert">
          <strong>{errorMessage}</strong>
          <button type="button" onClick={() => setReloadKey((current) => current + 1)}>
            다시 시도
          </button>
        </section>
      ) : null}

      <section className="admin-table-panel" aria-label="관리자 뉴스 목록">
        {loading ? <div className="admin-table-loading">뉴스 목록을 불러오는 중입니다.</div> : null}
        {!loading && !errorMessage && items.length === 0 ? (
          <div className="admin-empty-state">
            <strong>등록된 뉴스가 없습니다.</strong>
            <p>새 뉴스를 등록하면 이곳에서 콘텐츠를 관리할 수 있습니다.</p>
            <Link className="admin-secondary-button" to="/admin/news/new">
              첫 뉴스 등록하기
            </Link>
          </div>
        ) : null}
        {!loading && items.length > 0 ? (
          <div className="admin-table-scroll">
            <table className="admin-news-table">
              <thead>
                <tr>
                  <th scope="col">뉴스</th>
                  <th scope="col">카테고리</th>
                  <th scope="col">상태</th>
                  <th scope="col">등록일</th>
                  <th scope="col">
                    <span className="sr-only">관리</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => (
                  <tr key={item.id}>
                    <td data-label="뉴스">
                      <Link className="admin-news-title" to={`/admin/news/${item.id}/edit`}>
                        {item.title}
                      </Link>
                      <span className="admin-news-description">
                        {item.description || '설명 없음'}
                      </span>
                    </td>
                    <td data-label="카테고리">{CATEGORY_LABELS[item.category]}</td>
                    <td data-label="상태">
                      <StatusBadge status={item.status} />
                    </td>
                    <td data-label="등록일">{formatDate(item.createdAt)}</td>
                    <td data-label="관리">
                      <div className="admin-row-actions">
                        <button
                          type="button"
                          onClick={() => navigate(`/admin/news/${item.id}/edit`)}
                        >
                          수정
                        </button>
                        <button
                          type="button"
                          className="danger"
                          disabled={deletingId === item.id}
                          onClick={() => handleDelete(item)}
                        >
                          {deletingId === item.id ? '삭제 중' : '삭제'}
                        </button>
                      </div>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        ) : null}
      </section>

      {!loading && items.length > 0 && totalPages > 1 ? (
        <nav className="admin-pagination" aria-label="관리자 뉴스 페이지네이션">
          <button
            type="button"
            aria-label="이전 페이지"
            disabled={page === 1}
            onClick={() => setPage((current) => Math.max(1, current - 1))}
          >
            ‹
          </button>
          {paginationPages.map((pageNumber) => (
            <button
              type="button"
              key={pageNumber}
              className={pageNumber === page ? 'active' : undefined}
              aria-current={pageNumber === page ? 'page' : undefined}
              onClick={() => setPage(pageNumber)}
            >
              {pageNumber}
            </button>
          ))}
          <button
            type="button"
            aria-label="다음 페이지"
            disabled={page === totalPages}
            onClick={() => setPage((current) => Math.min(totalPages, current + 1))}
          >
            ›
          </button>
        </nav>
      ) : null}
    </main>
  );
}
