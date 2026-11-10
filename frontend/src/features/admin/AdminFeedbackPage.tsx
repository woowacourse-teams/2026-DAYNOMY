import { useEffect, useMemo, useRef, useState } from 'react';
import { ApiError } from '../../api/client';
import { deleteAdminFeedback, getAdminFeedbacks, updateAdminFeedbackStatus } from '../feedback/api';
import type { AdminFeedback, FeedbackCategory, FeedbackStatus } from '../feedback/types';
import { formatDate } from '../news/newslist/utils';
import './admin.css';

const STATUS_LABELS: Record<FeedbackStatus, string> = {
  PENDING: '접수',
  IN_PROGRESS: '처리 중',
  RESOLVED: '처리완료',
};

const CATEGORY_LABELS: Record<FeedbackCategory, string> = {
  BUG: '오류',
  INCONVENIENCE: '사용 불편',
  FEATURE_REQUEST: '기능 제안',
};

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

function StatusBadge({ status }: { status: FeedbackStatus }) {
  return (
    <span className={`admin-status admin-status-${status.toLowerCase()}`}>
      {STATUS_LABELS[status]}
    </span>
  );
}

export function AdminFeedbackPage() {
  const detailDialogRef = useRef<HTMLDialogElement>(null);
  const [items, setItems] = useState<AdminFeedback[]>([]);
  const [selectedItem, setSelectedItem] = useState<AdminFeedback | null>(null);
  const [page, setPage] = useState(1);
  const [totalPages, setTotalPages] = useState(1);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [processingId, setProcessingId] = useState<number | null>(null);
  const [errorMessage, setErrorMessage] = useState<string | null>(null);
  const [reloadKey, setReloadKey] = useState(0);

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setErrorMessage(null);

    getAdminFeedbacks(page, controller.signal)
      .then((response) => {
        if (controller.signal.aborted) return;
        const nextTotalPages = Math.max(1, response.totalPages);
        if (page > nextTotalPages) {
          setPage(nextTotalPages);
          return;
        }
        setItems(response.items);
        setTotalPages(nextTotalPages);
        setTotalElements(response.totalElements);
      })
      .catch((error) => {
        if (controller.signal.aborted) return;
        setItems([]);
        setTotalPages(1);
        setTotalElements(0);
        setErrorMessage(getErrorMessage(error, '피드백 목록을 불러오지 못했습니다.'));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });

    return () => controller.abort();
  }, [page, reloadKey]);

  useEffect(() => {
    const dialog = detailDialogRef.current;
    if (selectedItem && dialog && !dialog.open) dialog.showModal();
  }, [selectedItem]);

  const paginationPages = useMemo(() => getPaginationPages(page, totalPages), [page, totalPages]);

  async function handleStatusChange(item: AdminFeedback, nextStatus: FeedbackStatus) {
    if (nextStatus === item.status) return;
    setProcessingId(item.id);
    setErrorMessage(null);
    try {
      await updateAdminFeedbackStatus(item.id, nextStatus);
      setReloadKey((current) => current + 1);
    } catch (error) {
      setErrorMessage(getErrorMessage(error, '피드백 상태를 변경하지 못했습니다.'));
    } finally {
      setProcessingId(null);
    }
  }

  async function handleDelete(item: AdminFeedback) {
    if (!window.confirm('이 피드백을 삭제할까요? 삭제 후에는 복구할 수 없습니다.')) return;

    setProcessingId(item.id);
    setErrorMessage(null);
    try {
      await deleteAdminFeedback(item.id);
      setReloadKey((current) => current + 1);
    } catch (error) {
      setErrorMessage(getErrorMessage(error, '피드백을 삭제하지 못했습니다.'));
    } finally {
      setProcessingId(null);
    }
  }

  return (
    <main className="admin-content">
      <div className="admin-page-heading">
        <div>
          <h1>피드백 관리</h1>
          <p>사용자가 남긴 의견을 확인하고 처리 상태를 관리하세요.</p>
        </div>
      </div>

      <div className="admin-feedback-summary" aria-live="polite">
        전체 <strong>{totalElements.toLocaleString('ko-KR')}</strong>건
      </div>

      {errorMessage ? (
        <section className="admin-alert" role="alert">
          <strong>{errorMessage}</strong>
          <button type="button" onClick={() => setReloadKey((current) => current + 1)}>
            다시 시도
          </button>
        </section>
      ) : null}

      <section className="admin-table-panel" aria-label="피드백 목록">
        {loading ? <div className="admin-table-loading">피드백을 불러오는 중입니다.</div> : null}
        {!loading && !errorMessage && items.length === 0 ? (
          <div className="admin-empty-state">
            <strong>아직 접수된 피드백이 없습니다.</strong>
            <p>사용자가 의견을 보내면 이곳에서 확인할 수 있습니다.</p>
          </div>
        ) : null}
        {!loading && items.length > 0 ? (
          <div className="admin-table-scroll">
            <table className="admin-news-table admin-feedback-table">
              <thead>
                <tr>
                  <th scope="col">피드백</th>
                  <th scope="col">유형</th>
                  <th scope="col">작성자</th>
                  <th scope="col">작성 화면</th>
                  <th scope="col">상태</th>
                  <th scope="col">접수일</th>
                  <th scope="col">
                    <span className="sr-only">관리</span>
                  </th>
                </tr>
              </thead>
              <tbody>
                {items.map((item) => {
                  const isProcessing = processingId === item.id;
                  return (
                    <tr key={item.id}>
                      <td data-label="피드백">
                        <button
                          type="button"
                          className="admin-feedback-content"
                          disabled={isProcessing}
                          onClick={() => setSelectedItem(item)}
                        >
                          {item.content}
                        </button>
                      </td>
                      <td data-label="유형">
                        {item.category ? CATEGORY_LABELS[item.category] : '일반 의견'}
                      </td>
                      <td data-label="작성자">{item.authorNickname ?? '사용자'}</td>
                      <td data-label="작성 화면" className="admin-feedback-path">
                        {item.pagePath}
                      </td>
                      <td data-label="상태">
                        <label className="admin-feedback-status-select">
                          <span className="sr-only">피드백 상태</span>
                          <select
                            value={item.status}
                            disabled={isProcessing}
                            onChange={(event) =>
                              handleStatusChange(item, event.target.value as FeedbackStatus)
                            }
                          >
                            {Object.entries(STATUS_LABELS).map(([value, label]) => (
                              <option key={value} value={value}>
                                {label}
                              </option>
                            ))}
                          </select>
                        </label>
                      </td>
                      <td data-label="접수일">{formatDate(item.createdAt)}</td>
                      <td data-label="관리">
                        <div className="admin-row-actions">
                          <button
                            type="button"
                            className="danger"
                            disabled={isProcessing}
                            onClick={() => handleDelete(item)}
                          >
                            삭제
                          </button>
                        </div>
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>
        ) : null}
      </section>

      {!loading && items.length > 0 && totalPages > 1 ? (
        <nav className="admin-pagination" aria-label="피드백 페이지네이션">
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

      <dialog
        ref={detailDialogRef}
        className="admin-feedback-dialog"
        aria-labelledby="admin-feedback-detail-title"
        onCancel={() => setSelectedItem(null)}
        onClose={() => setSelectedItem(null)}
        onClick={(event) => {
          if (event.target === event.currentTarget) event.currentTarget.close();
        }}
      >
        {selectedItem ? (
          <div>
            <div className="admin-feedback-dialog-heading">
              <h2 id="admin-feedback-detail-title">피드백 상세</h2>
              <button
                type="button"
                aria-label="피드백 상세 닫기"
                onClick={() => detailDialogRef.current?.close()}
              >
                ×
              </button>
            </div>
            <dl>
              <div>
                <dt>유형</dt>
                <dd>
                  {selectedItem.category ? CATEGORY_LABELS[selectedItem.category] : '일반 의견'}
                </dd>
              </div>
              <div>
                <dt>작성자</dt>
                <dd>{selectedItem.authorNickname ?? '사용자'}</dd>
              </div>
              <div>
                <dt>상태</dt>
                <dd>
                  <StatusBadge status={selectedItem.status} />
                </dd>
              </div>
              <div>
                <dt>작성 화면</dt>
                <dd>{selectedItem.pagePath}</dd>
              </div>
              <div>
                <dt>접수일</dt>
                <dd>{formatDate(selectedItem.createdAt)}</dd>
              </div>
            </dl>
            <p>{selectedItem.content}</p>
          </div>
        ) : null}
      </dialog>
    </main>
  );
}
