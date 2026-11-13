import { useEffect, useState, type FormEvent } from 'react';
import { searchStocks } from '../stocks/api';
import type { StockSearchItem } from '../stocks/types';
import {
  addAdminIndustryAsset,
  createAdminIndustry,
  getAdminIndustryAssets,
  getAdminIndustries,
  removeAdminIndustryAsset,
  updateAdminIndustry,
} from './api';
import type { AdminIndustry } from './types';
import './admin.css';

type IndustryForm = { name: string; displayOrder: string; active: boolean };

function errorMessage(error: unknown, fallback: string) {
  return error instanceof Error ? error.message : fallback;
}

export function AdminIndustriesPage() {
  const [industries, setIndustries] = useState<AdminIndustry[]>([]);
  const [loading, setLoading] = useState(true);
  const [loadAttempt, setLoadAttempt] = useState(0);
  const [error, setError] = useState('');
  const [notice, setNotice] = useState('');
  const [creating, setCreating] = useState(false);
  const [editingId, setEditingId] = useState<number | null>(null);
  const [editing, setEditing] = useState<IndustryForm>({
    name: '',
    displayOrder: '0',
    active: true,
  });
  const [savingIndustry, setSavingIndustry] = useState(false);
  const [stockQuery, setStockQuery] = useState('');
  const [stocks, setStocks] = useState<StockSearchItem[]>([]);
  const [stockSearching, setStockSearching] = useState(false);
  const [stockSearched, setStockSearched] = useState(false);
  const [linkedAssets, setLinkedAssets] = useState<StockSearchItem[]>([]);
  const [assetsLoading, setAssetsLoading] = useState(false);
  const [assetsLoaded, setAssetsLoaded] = useState(false);
  const [assetsPage, setAssetsPage] = useState(1);
  const [assetsTotalPages, setAssetsTotalPages] = useState(1);
  const [assetsTotalElements, setAssetsTotalElements] = useState(0);
  const [assetLoadAttempt, setAssetLoadAttempt] = useState(0);
  const [changingAssetId, setChangingAssetId] = useState<number | null>(null);
  const [assetError, setAssetError] = useState('');

  useEffect(() => {
    const controller = new AbortController();
    setLoading(true);
    setError('');
    getAdminIndustries(controller.signal)
      .then(setIndustries)
      .catch((requestError) => {
        if (!controller.signal.aborted)
          setError(errorMessage(requestError, '업종을 불러오지 못했습니다.'));
      })
      .finally(() => {
        if (!controller.signal.aborted) setLoading(false);
      });
    return () => controller.abort();
  }, [loadAttempt]);

  useEffect(() => {
    if (editingId === null) {
      setLinkedAssets([]);
      setAssetsLoaded(false);
      return;
    }
    const controller = new AbortController();
    setAssetsLoading(true);
    setAssetsLoaded(false);
    setAssetError('');
    getAdminIndustryAssets(editingId, assetsPage, controller.signal)
      .then((response) => {
        const nextTotalPages = Math.max(1, response.totalPages);
        if (assetsPage > nextTotalPages) {
          setAssetsPage(nextTotalPages);
          return;
        }
        setLinkedAssets(response.items);
        setAssetsTotalPages(nextTotalPages);
        setAssetsTotalElements(response.totalElements);
        setAssetsLoaded(true);
      })
      .catch((requestError) => {
        if (!controller.signal.aborted) {
          setLinkedAssets([]);
          setAssetsTotalPages(1);
          setAssetsTotalElements(0);
          setAssetError(errorMessage(requestError, '연결 종목을 불러오지 못했습니다.'));
        }
      })
      .finally(() => {
        if (!controller.signal.aborted) setAssetsLoading(false);
      });
    return () => controller.abort();
  }, [editingId, assetsPage, assetLoadAttempt]);

  function selectIndustry(industry: AdminIndustry) {
    setEditingId(industry.id);
    setAssetsPage(1);
    setEditing({
      name: industry.name,
      displayOrder: String(industry.displayOrder),
      active: industry.active,
    });
    setNotice('');
  }

  async function createIndustry(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const formElement = event.currentTarget;
    const form = new FormData(formElement);
    const code = String(form.get('code') ?? '').trim();
    const name = String(form.get('name') ?? '').trim();
    const displayOrder = Number(form.get('displayOrder'));
    if (
      !/^[A-Z][A-Z0-9_]{0,49}$/.test(code) ||
      !name ||
      !Number.isSafeInteger(displayOrder) ||
      displayOrder < 0
    ) {
      setError('업종 코드, 이름과 노출 순서를 확인해 주세요.');
      return;
    }
    setCreating(true);
    setError('');
    setNotice('');
    try {
      const created = await createAdminIndustry({ code, name, displayOrder });
      setIndustries((current) =>
        [...current, created].sort((a, b) => a.displayOrder - b.displayOrder || a.id - b.id),
      );
      selectIndustry(created);
      formElement.reset();
      setNotice('업종을 추가했습니다.');
    } catch (requestError) {
      setError(errorMessage(requestError, '업종을 추가하지 못했습니다.'));
    } finally {
      setCreating(false);
    }
  }

  async function saveIndustry(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    if (editingId === null) return;
    const displayOrder = Number(editing.displayOrder);
    if (!editing.name.trim() || !Number.isSafeInteger(displayOrder) || displayOrder < 0) {
      setError('업종 이름과 0 이상의 노출 순서를 확인해 주세요.');
      return;
    }
    setSavingIndustry(true);
    setError('');
    setNotice('');
    try {
      const updated = await updateAdminIndustry(editingId, {
        name: editing.name.trim(),
        displayOrder,
        active: editing.active,
      });
      setIndustries((current) =>
        current
          .map((industry) => (industry.id === updated.id ? updated : industry))
          .sort((a, b) => a.displayOrder - b.displayOrder || a.id - b.id),
      );
      setNotice('업종 정보를 저장했습니다.');
    } catch (requestError) {
      setError(errorMessage(requestError, '업종 정보를 저장하지 못했습니다.'));
    } finally {
      setSavingIndustry(false);
    }
  }

  async function search(event: FormEvent<HTMLFormElement>) {
    event.preventDefault();
    const keyword = stockQuery.trim();
    if (!keyword) return;
    setStockSearching(true);
    setStockSearched(true);
    setAssetError('');
    try {
      const [domestic, us] = await Promise.all([
        searchStocks(keyword, undefined, 'KR'),
        searchStocks(keyword, undefined, 'US'),
      ]);
      setStocks([
        ...domestic,
        ...us.filter(
          (stock) => !domestic.some((domesticStock) => domesticStock.assetId === stock.assetId),
        ),
      ]);
    } catch (requestError) {
      setStocks([]);
      setAssetError(errorMessage(requestError, '종목을 검색하지 못했습니다.'));
    } finally {
      setStockSearching(false);
    }
  }

  async function addAsset(stock: StockSearchItem) {
    if (editingId === null) return;
    setChangingAssetId(stock.assetId);
    setAssetError('');
    setNotice('');
    try {
      await addAdminIndustryAsset(editingId, stock.assetId);
      if (assetsPage === 1) setAssetLoadAttempt((value) => value + 1);
      else setAssetsPage(1);
      setNotice(`${stock.name} 종목을 연결했습니다.`);
    } catch (requestError) {
      setAssetError(errorMessage(requestError, '종목을 연결하지 못했습니다.'));
    } finally {
      setChangingAssetId(null);
    }
  }

  async function removeAsset(asset: StockSearchItem) {
    if (editingId === null) return;
    setChangingAssetId(asset.assetId);
    setAssetError('');
    setNotice('');
    try {
      await removeAdminIndustryAsset(editingId, asset.assetId);
      if (linkedAssets.length === 1 && assetsPage > 1) {
        setAssetsPage((page) => page - 1);
      } else {
        setAssetLoadAttempt((value) => value + 1);
      }
      setNotice(`${asset.name} 종목 연결을 해제했습니다.`);
    } catch (requestError) {
      setAssetError(errorMessage(requestError, '종목 연결을 해제하지 못했습니다.'));
    } finally {
      setChangingAssetId(null);
    }
  }

  return (
    <main className="admin-content admin-industries-page">
      <div className="admin-page-heading">
        <div>
          <p className="admin-kicker">콘텐츠 분류</p>
          <h1>업종 관리</h1>
          <p>업종 기준정보를 관리하고 종목에 여러 업종을 지정합니다.</p>
        </div>
      </div>

      {error ? (
        <p className="admin-alert" role="alert">
          {error}{' '}
          {!loading && !industries.length ? (
            <button type="button" onClick={() => setLoadAttempt((value) => value + 1)}>
              다시 시도
            </button>
          ) : null}
        </p>
      ) : null}
      {notice ? (
        <p className="admin-success-panel" role="status">
          {notice}
        </p>
      ) : null}

      <div className="admin-industry-grid">
        <section className="admin-industry-panel" aria-labelledby="industry-master-title">
          <div>
            <h2 id="industry-master-title">업종 기준정보</h2>
            <p>코드는 생성 후 바꿀 수 없으며 삭제 대신 비활성화합니다.</p>
          </div>
          <form className="admin-industry-create" onSubmit={(event) => void createIndustry(event)}>
            <label className="admin-field">
              <span>업종 코드</span>
              <input name="code" maxLength={50} pattern="[A-Z][A-Z0-9_]*" required />
            </label>
            <label className="admin-field">
              <span>업종 이름</span>
              <input name="name" maxLength={50} required />
            </label>
            <label className="admin-field">
              <span>노출 순서</span>
              <input name="displayOrder" type="number" min="0" defaultValue="0" required />
            </label>
            <button className="admin-primary-button" type="submit" disabled={creating}>
              {creating ? '추가 중…' : '업종 추가'}
            </button>
          </form>

          {loading ? (
            <p className="admin-picker-empty" role="status">
              업종을 불러오는 중입니다.
            </p>
          ) : null}
          {!loading && !industries.length ? (
            <p className="admin-picker-empty">등록된 업종이 없습니다.</p>
          ) : null}
          {industries.length ? (
            <div className="admin-industry-editor">
              <ul className="admin-industry-list" aria-label="등록된 업종">
                {industries.map((industry) => (
                  <li key={industry.id}>
                    <button
                      type="button"
                      aria-pressed={editingId === industry.id}
                      disabled={changingAssetId !== null}
                      onClick={() => selectIndustry(industry)}
                    >
                      <strong>{industry.name}</strong>
                      <span>
                        {industry.code} · 순서 {industry.displayOrder}
                      </span>
                      {!industry.active ? <em>비활성</em> : null}
                    </button>
                  </li>
                ))}
              </ul>
              {editingId !== null ? (
                <form
                  className="admin-industry-edit"
                  onSubmit={(event) => void saveIndustry(event)}
                >
                  <label className="admin-field">
                    <span>업종 이름</span>
                    <input
                      value={editing.name}
                      maxLength={50}
                      onChange={(event) => setEditing({ ...editing, name: event.target.value })}
                      required
                    />
                  </label>
                  <label className="admin-field">
                    <span>노출 순서</span>
                    <input
                      type="number"
                      min="0"
                      value={editing.displayOrder}
                      onChange={(event) =>
                        setEditing({ ...editing, displayOrder: event.target.value })
                      }
                      required
                    />
                  </label>
                  <label className="admin-industry-active">
                    <input
                      type="checkbox"
                      checked={editing.active}
                      onChange={(event) => setEditing({ ...editing, active: event.target.checked })}
                    />
                    공개 소식과 AI 분류에서 사용
                  </label>
                  <button className="admin-primary-button" type="submit" disabled={savingIndustry}>
                    {savingIndustry ? '저장 중…' : '변경 저장'}
                  </button>
                </form>
              ) : (
                <p className="admin-picker-empty">수정할 업종을 선택해 주세요.</p>
              )}
            </div>
          ) : null}
        </section>

        <section className="admin-industry-panel" aria-labelledby="asset-industry-title">
          <div>
            <h2 id="asset-industry-title">연결 종목</h2>
            <p>업종을 선택한 뒤 연결된 종목을 확인하고 추가하거나 해제합니다.</p>
          </div>
          {editingId === null ? (
            <p className="admin-picker-empty">연결 종목을 확인할 업종을 선택해 주세요.</p>
          ) : (
            <>
              <div className="admin-industry-assets-heading">
                <strong>{industries.find((industry) => industry.id === editingId)?.name}</strong>
                <span>{assetsLoaded ? `${assetsTotalElements}개 종목` : '종목 확인 중'}</span>
              </div>
              {assetError ? (
                <p className="admin-alert" role="alert">
                  {assetError}
                </p>
              ) : null}
              {assetsLoading ? <p role="status">연결 종목을 불러오는 중입니다.</p> : null}
              {!assetsLoading && !assetsLoaded ? (
                <button
                  className="admin-secondary-button"
                  type="button"
                  onClick={() => setAssetLoadAttempt((value) => value + 1)}
                >
                  연결 종목 다시 불러오기
                </button>
              ) : null}
              {assetsLoaded && linkedAssets.length ? (
                <ul className="admin-industry-linked-assets" aria-label="연결된 종목">
                  {linkedAssets.map((asset) => (
                    <li key={asset.assetId}>
                      <div>
                        <strong>{asset.name}</strong>
                        <span>
                          {asset.assetCode} · {asset.market}
                        </span>
                      </div>
                      <button
                        className="admin-secondary-button"
                        type="button"
                        disabled={changingAssetId !== null}
                        onClick={() => void removeAsset(asset)}
                      >
                        {changingAssetId === asset.assetId ? '해제 중…' : '연결 해제'}
                      </button>
                    </li>
                  ))}
                </ul>
              ) : null}
              {assetsLoaded && !linkedAssets.length ? (
                <p className="admin-picker-empty">이 업종에 연결된 종목이 없습니다.</p>
              ) : null}
              {assetsLoaded && assetsTotalPages > 1 ? (
                <nav className="admin-industry-pagination" aria-label="연결 종목 페이지네이션">
                  <button
                    className="admin-secondary-button"
                    type="button"
                    disabled={assetsPage === 1 || changingAssetId !== null}
                    onClick={() => setAssetsPage((page) => page - 1)}
                  >
                    이전
                  </button>
                  <span>
                    {assetsPage} / {assetsTotalPages}
                  </span>
                  <button
                    className="admin-secondary-button"
                    type="button"
                    disabled={assetsPage === assetsTotalPages || changingAssetId !== null}
                    onClick={() => setAssetsPage((page) => page + 1)}
                  >
                    다음
                  </button>
                </nav>
              ) : null}

              <form
                className="admin-industry-stock-search"
                onSubmit={(event) => void search(event)}
              >
                <label className="admin-field">
                  <span>추가할 종목명 또는 종목코드</span>
                  <input
                    value={stockQuery}
                    onChange={(event) => setStockQuery(event.target.value)}
                    required
                  />
                </label>
                <button className="admin-secondary-button" type="submit" disabled={stockSearching}>
                  {stockSearching ? '검색 중…' : '종목 검색'}
                </button>
              </form>
              {stocks.length ? (
                <ul className="admin-stock-search-results admin-industry-stock-results">
                  {stocks.map((stock) => {
                    const linked = linkedAssets.some((asset) => asset.assetId === stock.assetId);
                    return (
                      <li key={stock.assetId}>
                        <div>
                          <strong>{stock.name}</strong>
                          <span>
                            {stock.assetCode} · {stock.market}
                          </span>
                        </div>
                        <button
                          className="admin-secondary-button"
                          type="button"
                          disabled={!assetsLoaded || linked || changingAssetId !== null}
                          onClick={() => void addAsset(stock)}
                        >
                          {linked
                            ? '연결됨'
                            : changingAssetId === stock.assetId
                              ? '추가 중…'
                              : '추가'}
                        </button>
                      </li>
                    );
                  })}
                </ul>
              ) : null}
              {stockSearched && !stockSearching && !stocks.length && !assetError ? (
                <p className="admin-picker-empty">검색 결과가 없습니다.</p>
              ) : null}
            </>
          )}
        </section>
      </div>
    </main>
  );
}
