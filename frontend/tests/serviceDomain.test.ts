import assert from 'node:assert/strict';
import { describe, it } from 'node:test';
import { isStaffHostname, isUserHostname } from '../src/serviceDomain';

describe('서비스 도메인 분리', () => {
  it('팀원 운영 도메인만 팀원 호스트로 판단한다', () => {
    assert.equal(isStaffHostname('staff.daynomy.com'), true);
    assert.equal(isStaffHostname('STAFF.DAYNOMY.COM'), true);
    assert.equal(isStaffHostname('daynomy.com'), false);
    assert.equal(isStaffHostname('staff.daynomy.com.evil.example'), false);
  });

  it('사용자 운영 도메인을 다른 호스트와 구분한다', () => {
    assert.equal(isUserHostname('daynomy.com'), true);
    assert.equal(isUserHostname('DAYNOMY.COM'), true);
    assert.equal(isUserHostname('staff.daynomy.com'), false);
    assert.equal(isUserHostname('daynomy.com.evil.example'), false);
  });
});
