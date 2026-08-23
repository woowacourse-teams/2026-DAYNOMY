package org.grit.daynomy.bookmark.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.List;
import java.util.Optional;
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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

@ExtendWith(MockitoExtension.class)
class BookmarkServiceTest {

  @Mock private BookmarkRepository bookmarkRepository;

  @Mock private AssetRepository assetRepository;

  @Mock private MemberService memberService;

  @InjectMocks private BookmarkService bookmarkService;

  @Test
  @DisplayName("활성 회원의 자산을 북마크하고 응답 DTO를 반환한다")
  void addBookmarkReturnsResponse() {
    Member member = mock(Member.class);
    Asset asset = createAsset(10L, "에코프로비엠");
    Bookmark savedBookmark = mock(Bookmark.class);
    given(memberService.getMember(3L)).willReturn(member);
    given(assetRepository.getReferenceById(10L)).willReturn(asset);
    given(bookmarkRepository.existsByMemberIdAndAssetId(3L, 10L)).willReturn(false);
    given(bookmarkRepository.save(any(Bookmark.class))).willReturn(savedBookmark);
    given(savedBookmark.getId()).willReturn(1L);
    given(savedBookmark.getAsset()).willReturn(asset);

    BookmarkResponse response = bookmarkService.addBookmark(3L, 10L);

    assertThat(response).isEqualTo(new BookmarkResponse(1L, 10L, "에코프로비엠"));
    verify(bookmarkRepository).save(any(Bookmark.class));
  }

  @Test
  @DisplayName("이미 북마크한 자산이면 추가하지 않고 예외를 던진다")
  void addBookmarkThrowsWhenAlreadyExists() {
    Asset asset = mock(Asset.class);
    given(memberService.getMember(3L)).willReturn(mock(Member.class));
    given(assetRepository.getReferenceById(10L)).willReturn(asset);
    given(bookmarkRepository.existsByMemberIdAndAssetId(3L, 10L)).willReturn(true);

    assertThatThrownBy(() -> bookmarkService.addBookmark(3L, 10L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(BookmarkErrorCode.BOOKMARK_ALREADY_EXISTS);
    verify(bookmarkRepository, never()).save(any(Bookmark.class));
  }

  @Test
  @DisplayName("북마크 유니크 제약조건 위반을 중복 북마크 예외로 변환한다")
  void addBookmarkTranslatesBookmarkUniqueConstraintViolation() {
    Asset asset = mock(Asset.class);
    given(memberService.getMember(3L)).willReturn(mock(Member.class));
    given(assetRepository.getReferenceById(10L)).willReturn(asset);
    given(bookmarkRepository.existsByMemberIdAndAssetId(3L, 10L)).willReturn(false);
    given(bookmarkRepository.save(any(Bookmark.class)))
        .willThrow(
            new DataIntegrityViolationException(
                "duplicate bookmark",
                new ConstraintViolationException(
                    "duplicate bookmark", null, "uk_bookmarks_member_asset")));

    assertThatThrownBy(() -> bookmarkService.addBookmark(3L, 10L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(BookmarkErrorCode.BOOKMARK_ALREADY_EXISTS);
  }

  @Test
  @DisplayName("다른 무결성 예외는 그대로 전달한다")
  void addBookmarkRethrowsOtherIntegrityViolations() {
    Asset asset = mock(Asset.class);
    given(memberService.getMember(3L)).willReturn(mock(Member.class));
    given(assetRepository.getReferenceById(10L)).willReturn(asset);
    given(bookmarkRepository.existsByMemberIdAndAssetId(3L, 10L)).willReturn(false);
    DataIntegrityViolationException exception =
        new DataIntegrityViolationException("other integrity violation");
    given(bookmarkRepository.save(any(Bookmark.class))).willThrow(exception);

    assertThatThrownBy(() -> bookmarkService.addBookmark(3L, 10L)).isSameAs(exception);
  }

  @Test
  @DisplayName("존재하는 회원의 북마크를 삭제한다")
  void deleteBookmarkDeletesBookmark() {
    given(memberService.getMember(3L)).willReturn(mock(Member.class));
    Bookmark bookmark = mock(Bookmark.class);
    given(bookmarkRepository.findByMemberIdAndAssetId(3L, 10L)).willReturn(Optional.of(bookmark));

    bookmarkService.deleteBookmark(3L, 10L);

    verify(bookmarkRepository).delete(bookmark);
  }

  @Test
  @DisplayName("삭제할 북마크가 없으면 예외를 던진다")
  void deleteBookmarkThrowsWhenMissing() {
    given(memberService.getMember(3L)).willReturn(mock(Member.class));
    given(bookmarkRepository.findByMemberIdAndAssetId(3L, 10L)).willReturn(Optional.empty());

    assertThatThrownBy(() -> bookmarkService.deleteBookmark(3L, 10L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(BookmarkErrorCode.BOOKMARK_NOT_FOUND);
    verify(bookmarkRepository, never()).delete(any(Bookmark.class));
  }

  @Test
  @DisplayName("회원의 북마크 목록을 응답 DTO 목록으로 변환한다")
  void getBookmarksReturnsResponses() {
    given(memberService.getMember(3L)).willReturn(mock(Member.class));
    Bookmark first = createBookmark(1L, 10L, "에코프로비엠");
    Bookmark second = createBookmark(2L, 20L, "삼성전자");
    given(bookmarkRepository.findAllByMemberIdOrderByIdAsc(3L)).willReturn(List.of(first, second));

    List<BookmarkResponse> responses = bookmarkService.getBookmarks(3L);

    assertThat(responses)
        .containsExactly(
            new BookmarkResponse(1L, 10L, "에코프로비엠"), new BookmarkResponse(2L, 20L, "삼성전자"));
  }

  private Bookmark createBookmark(Long id, Long assetId, String assetName) {
    Bookmark bookmark = mock(Bookmark.class);
    Asset asset = createAsset(assetId, assetName);
    given(bookmark.getId()).willReturn(id);
    given(bookmark.getAsset()).willReturn(asset);
    return bookmark;
  }

  private Asset createAsset(Long id, String name) {
    Asset asset = mock(Asset.class);
    given(asset.getId()).willReturn(id);
    given(asset.getName()).willReturn(name);
    return asset;
  }
}
