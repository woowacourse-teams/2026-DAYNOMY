/** @vitest-environment jsdom */

import { cleanup, fireEvent, render, waitFor, within } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import { AdminIndustriesPage } from '../../src/features/admin/AdminIndustriesPage';
import {
  addAdminIndustryAsset,
  createAdminIndustry,
  getAdminIndustryAssets,
  getAdminIndustries,
  removeAdminIndustryAsset,
  updateAdminIndustry,
} from '../../src/features/admin/api';
import { searchStocks } from '../../src/features/stocks/api';

vi.mock('../../src/features/admin/api', () => ({
  addAdminIndustryAsset: vi.fn(),
  createAdminIndustry: vi.fn(),
  getAdminIndustryAssets: vi.fn(),
  getAdminIndustries: vi.fn(),
  removeAdminIndustryAsset: vi.fn(),
  updateAdminIndustry: vi.fn(),
}));
vi.mock('../../src/features/stocks/api', () => ({ searchStocks: vi.fn() }));

const semiconductor = {
  id: 1,
  code: 'SEMICONDUCTOR',
  name: '반도체',
  displayOrder: 1,
  active: true,
};
const automobile = {
  id: 2,
  code: 'AUTOMOBILE',
  name: '자동차',
  displayOrder: 2,
  active: true,
};
const stock = {
  assetId: 7,
  assetCode: '005930',
  name: '삼성전자',
  category: 'STOCK' as const,
  market: 'KOSPI' as const,
};
const candidate = {
  assetId: 8,
  assetCode: '000660',
  name: 'SK하이닉스',
  category: 'STOCK' as const,
  market: 'KOSPI' as const,
};
const pageTwoStock = {
  assetId: 9,
  assetCode: '066570',
  name: 'LG전자',
  category: 'STOCK' as const,
  market: 'KOSPI' as const,
};

beforeEach(() => {
  vi.mocked(getAdminIndustries).mockReset();
  vi.mocked(getAdminIndustries).mockResolvedValue([semiconductor, automobile]);
  vi.mocked(createAdminIndustry).mockReset();
  vi.mocked(updateAdminIndustry).mockResolvedValue({
    ...semiconductor,
    name: '반도체·장비',
    active: false,
  });
  vi.mocked(searchStocks).mockImplementation(async (_keyword, _signal, country) =>
    country === 'US' ? [] : [candidate],
  );
  vi.mocked(getAdminIndustryAssets).mockImplementation(async (_industryId, page) => {
    const currentPage = page ?? 1;
    return {
      industryId: semiconductor.id,
      items: currentPage === 2 ? [pageTwoStock] : [stock],
      page: currentPage,
      size: 20,
      totalPages: 2,
      totalElements: 21,
      hasNext: currentPage === 1,
    };
  });
  vi.mocked(addAdminIndustryAsset).mockResolvedValue(undefined);
  vi.mocked(removeAdminIndustryAsset).mockResolvedValue(undefined);
});

it('새 업종을 추가한다', async () => {
  vi.mocked(createAdminIndustry).mockResolvedValue({
    id: 3,
    code: 'AI',
    name: '인공지능',
    displayOrder: 3,
    active: true,
  });
  const view = render(<AdminIndustriesPage />);
  await view.findByRole('button', { name: /반도체.*SEMICONDUCTOR/ });
  fireEvent.change(view.getByRole('textbox', { name: '업종 코드' }), {
    target: { value: 'AI' },
  });
  fireEvent.change(view.getByRole('textbox', { name: '업종 이름' }), {
    target: { value: '인공지능' },
  });
  fireEvent.change(view.getByRole('spinbutton', { name: '노출 순서' }), {
    target: { value: '3' },
  });
  fireEvent.click(view.getByRole('button', { name: '업종 추가' }));

  await waitFor(() =>
    expect(createAdminIndustry).toHaveBeenCalledWith({
      code: 'AI',
      name: '인공지능',
      displayOrder: 3,
    }),
  );
  expect(await view.findByText('업종을 추가했습니다.')).toBeTruthy();
});

afterEach(() => {
  cleanup();
  vi.clearAllMocks();
});

it('업종 정보를 수정하고 연결 종목을 조회·추가·해제한다', async () => {
  const view = render(<AdminIndustriesPage />);
  fireEvent.click(await view.findByRole('button', { name: /반도체.*SEMICONDUCTOR/ }));
  expect(await view.findByText('삼성전자')).toBeTruthy();
  expect(view.getByText('21개 종목')).toBeTruthy();
  fireEvent.click(view.getByRole('button', { name: '다음' }));
  expect(await view.findByText('LG전자')).toBeTruthy();
  expect(getAdminIndustryAssets).toHaveBeenCalledWith(1, 2, expect.anything());
  const lastPageRow = view.getByText('LG전자').closest('li');
  fireEvent.click(within(lastPageRow!).getByRole('button', { name: '연결 해제' }));
  await waitFor(() => expect(removeAdminIndustryAsset).toHaveBeenCalledWith(1, 9));
  expect(await view.findByText('삼성전자')).toBeTruthy();
  const editForm = view.container.querySelector<HTMLFormElement>('.admin-industry-edit')!;
  fireEvent.change(within(editForm).getByRole('textbox', { name: '업종 이름' }), {
    target: { value: '반도체·장비' },
  });
  fireEvent.click(within(editForm).getByRole('checkbox', { name: '공개 소식과 AI 분류에서 사용' }));
  fireEvent.click(within(editForm).getByRole('button', { name: '변경 저장' }));

  await waitFor(() =>
    expect(updateAdminIndustry).toHaveBeenCalledWith(1, {
      name: '반도체·장비',
      displayOrder: 1,
      active: false,
    }),
  );

  fireEvent.change(view.getByRole('textbox', { name: '추가할 종목명 또는 종목코드' }), {
    target: { value: 'SK하이닉스' },
  });
  fireEvent.click(view.getByRole('button', { name: '종목 검색' }));
  expect(searchStocks).toHaveBeenCalledWith('SK하이닉스', undefined, 'KR');
  expect(searchStocks).toHaveBeenCalledWith('SK하이닉스', undefined, 'US');
  fireEvent.click(await view.findByRole('button', { name: '추가' }));

  await waitFor(() => expect(addAdminIndustryAsset).toHaveBeenCalledWith(1, candidate.assetId));
  expect(await view.findByText('SK하이닉스 종목을 연결했습니다.')).toBeTruthy();

  const samsungRow = view.getByText('삼성전자').closest('li');
  fireEvent.click(within(samsungRow!).getByRole('button', { name: '연결 해제' }));
  await waitFor(() => expect(removeAdminIndustryAsset).toHaveBeenCalledWith(1, stock.assetId));
  expect(await view.findByText('삼성전자 종목 연결을 해제했습니다.')).toBeTruthy();
});

it('연결 종목 조회가 실패하면 종목을 추가하지 않는다', async () => {
  vi.mocked(getAdminIndustryAssets).mockRejectedValueOnce(new Error('조회 실패'));
  const view = render(<AdminIndustriesPage />);
  fireEvent.click(await view.findByRole('button', { name: /반도체.*SEMICONDUCTOR/ }));
  expect((await view.findByRole('alert')).textContent).toContain('조회 실패');
  fireEvent.change(view.getByRole('textbox', { name: '추가할 종목명 또는 종목코드' }), {
    target: { value: 'SK하이닉스' },
  });
  fireEvent.click(view.getByRole('button', { name: '종목 검색' }));

  expect(view.getByRole('button', { name: '연결 종목 다시 불러오기' })).toBeTruthy();
  expect((await view.findByRole('button', { name: '추가' })).hasAttribute('disabled')).toBe(true);
  expect(addAdminIndustryAsset).not.toHaveBeenCalled();
});
