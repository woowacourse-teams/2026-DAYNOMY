package org.grit.daynomy.finance.service;

import lombok.RequiredArgsConstructor;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.finance.domain.FinancialPlan;
import org.grit.daynomy.finance.dto.FinancialPlanDto.*;
import org.grit.daynomy.finance.exception.FinancialLearningErrorCode;
import org.grit.daynomy.finance.repository.FinancialPlanRepository;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.exception.MemberErrorCode;
import org.grit.daynomy.member.repository.MemberRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class FinancialPlanService {
  private final FinancialPlanRepository repository;
  private final MemberRepository memberRepository;

  @Transactional(readOnly = true)
  public PlanListResponse list(Long memberId) {
    return new PlanListResponse(
        repository.findAllByMemberIdOrderByCreatedAtDescIdDesc(memberId).stream()
            .map(PlanResponse::from)
            .toList());
  }

  @Transactional
  public PlanResponse save(Long memberId, String key, PlanRequest request) {
    if (!request.monthlyPlan().toDomain().balanced())
      throw new BusinessException(FinancialLearningErrorCode.INVALID_MONTHLY_ALLOCATION);
    Member member =
        memberRepository
            .findByIdForUpdate(memberId)
            .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
    FinancialPlan plan =
        repository
            .findByMemberIdAndPlanKey(memberId, key)
            .orElseGet(() -> new FinancialPlan(member, key, request.toDomain()));
    plan.change(request.toDomain());
    return PlanResponse.from(repository.saveAndFlush(plan));
  }

  @Transactional
  public void remove(Long memberId, String key) {
    memberRepository
        .findByIdForUpdate(memberId)
        .orElseThrow(() -> new BusinessException(MemberErrorCode.MEMBER_NOT_FOUND));
    repository.findByMemberIdAndPlanKey(memberId, key).ifPresent(repository::delete);
  }
}
