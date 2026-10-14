// @vitest-environment jsdom
import { render } from '@testing-library/react';
import { expect, it } from 'vitest';
import { ReturnLine } from '../../src/features/league/ReturnLine';

it('0%도 점으로 표시하고 누락 구간을 건너뛰어 연결하지 않는다', () => {
  const view = render(<ReturnLine rates={[0, 2, null, -1, 3]} />);
  const points = view.container.querySelectorAll('circle');
  const lines = view.container.querySelectorAll('g line');
  expect(points).toHaveLength(4);
  expect(lines).toHaveLength(2);
  expect(points[0].getAttribute('cy')).toBe('72');
  expect(lines[0].getAttribute('x2')).toBe('30');
  expect(lines[1].getAttribute('x1')).toBe('70');
});
