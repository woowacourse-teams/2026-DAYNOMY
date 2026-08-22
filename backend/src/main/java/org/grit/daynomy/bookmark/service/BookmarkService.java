package org.grit.daynomy.bookmark.service;

import lombok.RequiredArgsConstructor;
import org.grit.daynomy.bookmark.domain.Bookmark;
import org.grit.daynomy.bookmark.exception.BookmarkErrorCode;
import org.grit.daynomy.bookmark.repository.BookmarkRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.service.MemberService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class BookmarkService {

  private final BookmarkRepository bookmarkRepository;
  private final MemberService memberService;

  @Transactional
  public Bookmark addBookmark(Long memberId, Long targetId) {
    Member member = memberService.getMember(memberId);

    if (bookmarkRepository.existsByMemberIdAndTargetId(memberId, targetId)) {
      throw new BusinessException(BookmarkErrorCode.BOOKMARK_ALREADY_EXISTS);
    }

    return bookmarkRepository.save(Bookmark.create(member, targetId));
  }
}
