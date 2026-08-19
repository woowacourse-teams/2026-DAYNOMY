package org.grit.daynomy.auth.token;

import org.grit.daynomy.member.domain.MemberRole;

public record AuthenticatedMember(Long memberId, MemberRole role) {}
