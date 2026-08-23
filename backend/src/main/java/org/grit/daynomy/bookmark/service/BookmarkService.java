package org.grit.daynomy.bookmark.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.bookmark.domain.Bookmark;
import org.grit.daynomy.bookmark.dto.BookmarkResponse;
import org.grit.daynomy.bookmark.exception.BookmarkErrorCode;
import org.grit.daynomy.bookmark.repository.BookmarkRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.member.domain.Member;
import org.grit.daynomy.member.service.MemberService;
import org.hibernate.exception.ConstraintViolationException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Transactional(readOnly = true)
@Service
public class BookmarkService {

  private final BookmarkRepository bookmarkRepository;
  private final AssetRepository assetRepository;
  private final MemberService memberService;

  @Transactional
  public BookmarkResponse addBookmark(Long memberId, Long targetId) {
    Member member = memberService.getMember(memberId);
    Asset asset = assetRepository.getReferenceById(targetId);

    if (bookmarkRepository.existsByMemberIdAndAssetId(memberId, targetId)) {
      throw new BusinessException(BookmarkErrorCode.BOOKMARK_ALREADY_EXISTS);
    }

    try {
      return BookmarkResponse.from(bookmarkRepository.save(Bookmark.create(member, asset)));
    } catch (DataIntegrityViolationException exception) {
      if (isBookmarkUniqueConstraintViolation(exception)) {
        throw new BusinessException(BookmarkErrorCode.BOOKMARK_ALREADY_EXISTS);
      }
      throw exception;
    }
  }

  @Transactional
  public void deleteBookmark(Long memberId, Long targetId) {
    memberService.getMember(memberId);

    Bookmark bookmark =
        bookmarkRepository
            .findByMemberIdAndAssetId(memberId, targetId)
            .orElseThrow(() -> new BusinessException(BookmarkErrorCode.BOOKMARK_NOT_FOUND));
    bookmarkRepository.delete(bookmark);
  }

  public List<BookmarkResponse> getBookmarks(Long memberId) {
    memberService.getMember(memberId);
    return bookmarkRepository.findAllByMemberIdOrderByIdAsc(memberId).stream()
        .map(BookmarkResponse::from)
        .toList();
  }

  private boolean isBookmarkUniqueConstraintViolation(DataIntegrityViolationException exception) {
    Throwable cause = exception;
    while (cause != null) {
      if (cause instanceof ConstraintViolationException constraintViolationException) {
        return "uk_bookmarks_member_asset".equals(constraintViolationException.getConstraintName());
      }
      cause = cause.getCause();
    }
    return false;
  }
}
