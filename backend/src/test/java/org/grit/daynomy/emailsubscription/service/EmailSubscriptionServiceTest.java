package org.grit.daynomy.emailsubscription.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.util.Optional;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.emailsubscription.domain.EmailSubscriptionStatus;
import org.grit.daynomy.emailsubscription.repository.EmailSubscriptionRepository;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.exception.MemberErrorCode;
import org.grit.daynomy.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class EmailSubscriptionServiceTest {

  @Mock private MemberRepository members;
  @Mock private EmailSubscriptionRepository subscriptions;
  @InjectMocks private EmailSubscriptionService service;

  @Test
  void anUnansweredInvitationUsesTheAccountEmailWithoutCreatingARecord() {
    Member member = member();
    when(members.findById(1L)).thenReturn(Optional.of(member));
    when(subscriptions.findById(1L)).thenReturn(Optional.empty());

    var response = service.get(1L);

    assertThat(response.status()).isEqualTo(EmailSubscriptionStatus.UNDECIDED);
    assertThat(response.email()).isEqualTo("google@example.invalid");
    assertThat(response.promptRequired()).isTrue();
    assertThat(response.subscribedAt()).isNull();
    verify(subscriptions, never()).save(any());
  }

  @Test
  void aMissingMemberCannotReadSubscriptionState() {
    when(members.findById(1L)).thenReturn(Optional.empty());

    assertThatThrownBy(() -> service.get(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(MemberErrorCode.MEMBER_NOT_FOUND);
    verifyNoInteractions(subscriptions);
  }

  @Test
  void aWithdrawnMemberCannotReadSubscriptionState() {
    Member member = member();
    member.withdraw();
    when(members.findById(1L)).thenReturn(Optional.of(member));

    assertThatThrownBy(() -> service.get(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(MemberErrorCode.WITHDRAWN_MEMBER);
    verifyNoInteractions(subscriptions);
  }

  @Test
  void aWithdrawnMemberCannotSubscribe() {
    Member member = member();
    member.withdraw();
    when(members.findByIdForUpdate(1L)).thenReturn(Optional.of(member));

    assertThatThrownBy(() -> service.subscribe(1L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(MemberErrorCode.WITHDRAWN_MEMBER);
    verifyNoInteractions(subscriptions);
  }

  private Member member() {
    return Member.createGoogleMember("google-id", "google@example.invalid", "회원", null);
  }
}
