import { useId, useState } from 'react';
import { ApiError } from '../../api/client';
import { updatePortfolioShareExplanation } from './api';
import type { PortfolioAnalysisOwnerShare } from './types';

export function PortfolioShareExplanation({
  share,
  disabled,
  onChange,
}: {
  share: PortfolioAnalysisOwnerShare;
  disabled: boolean;
  onChange: (share: PortfolioAnalysisOwnerShare) => void;
}) {
  const id = useId();
  const [editing, setEditing] = useState(false);
  const [draft, setDraft] = useState('');
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const content = draft.trim();
  const invalid = content.length > 2000 || /[<>]/.test(content);
  const changed = content !== (share.additionalExplanation ?? '');

  async function save() {
    if (saving || disabled || invalid || !changed) return;
    setSaving(true);
    setError('');
    try {
      onChange(await updatePortfolioShareExplanation(share.id, content));
      setEditing(false);
      setNotice(content ? '공유 페이지에 추가 설명을 공개했어요.' : '추가 설명을 내렸어요.');
    } catch (caught) {
      setError(
        caught instanceof ApiError
          ? caught.message
          : '추가 설명을 저장하지 못했어요. 다시 시도해 주세요.',
      );
    } finally {
      setSaving(false);
    }
  }

  return (
    <section className="portfolio-share-explanation-editor" aria-labelledby={`${id}-title`}>
      <header>
        <div>
          <h4 id={`${id}-title`}>추가 설명</h4>
          <p id={`${id}-visibility`}>
            {share.status === 'ACTIVE'
              ? '링크를 가진 모든 사람에게 공개돼요.'
              : '공유가 종료되어 추가 설명을 수정할 수 없어요.'}
          </p>
        </div>
        {!editing && share.status === 'ACTIVE' ? (
          <button
            type="button"
            disabled={disabled}
            onClick={() => {
              setDraft(share.additionalExplanation ?? '');
              setError('');
              setNotice('');
              setEditing(true);
            }}
          >
            {share.additionalExplanation ? '설명 수정' : '설명 추가'}
          </button>
        ) : null}
      </header>
      {editing ? (
        <form
          onSubmit={(event) => {
            event.preventDefault();
            void save();
          }}
        >
          <label htmlFor={`${id}-content`}>추가 설명 내용</label>
          <textarea
            id={`${id}-content`}
            autoFocus
            rows={4}
            maxLength={2000}
            value={draft}
            disabled={saving || disabled}
            aria-describedby={`${id}-visibility ${id}-hint`}
            aria-invalid={invalid}
            onChange={(event) => setDraft(event.target.value)}
            placeholder="투자 기간이나 목표처럼 함께 참고할 내용을 남겨보세요."
          />
          <div className="portfolio-share-explanation-meta">
            <small id={`${id}-hint`} className={invalid ? 'is-error' : undefined}>
              {invalid
                ? '<, > 없이 2,000자 이내로 입력해 주세요.'
                : share.additionalExplanation
                  ? '내용을 비우고 저장하면 공개된 설명이 내려가요.'
                  : '공유 페이지의 질문 아래에 표시돼요.'}
            </small>
            <small>{content.length.toLocaleString()}/2,000자</small>
          </div>
          {error ? (
            <p className="portfolio-share-error" role="alert">
              {error}
            </p>
          ) : null}
          <div className="portfolio-share-actions">
            <button
              type="button"
              className="is-secondary"
              disabled={saving}
              onClick={() => setEditing(false)}
            >
              취소
            </button>
            <button type="submit" disabled={saving || disabled || invalid || !changed}>
              {saving
                ? '저장 중'
                : content || !share.additionalExplanation
                  ? '추가 설명 공개'
                  : '설명 내리기'}
            </button>
          </div>
        </form>
      ) : share.additionalExplanation ? (
        <p className="portfolio-share-explanation-copy">{share.additionalExplanation}</p>
      ) : null}
      {notice ? (
        <p className="portfolio-share-notice" role="status">
          {notice}
        </p>
      ) : null}
    </section>
  );
}
