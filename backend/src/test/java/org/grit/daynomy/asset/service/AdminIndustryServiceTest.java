package org.grit.daynomy.asset.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.Industry;
import org.grit.daynomy.asset.dto.AdminAssetIndustriesUpdateRequest;
import org.grit.daynomy.asset.dto.AdminIndustryCreateRequest;
import org.grit.daynomy.asset.dto.AdminIndustryUpdateRequest;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.asset.repository.IndustryRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

@ExtendWith(MockitoExtension.class)
class AdminIndustryServiceTest {

  @Mock private IndustryRepository industryRepository;
  @Mock private AssetRepository assetRepository;

  private AdminIndustryService service;

  @BeforeEach
  void setUp() {
    service = new AdminIndustryService(industryRepository, assetRepository);
  }

  @Test
  void rejectsDuplicateIndustryCode() {
    given(industryRepository.existsByCode("SEMICONDUCTOR")).willReturn(true);

    assertThatThrownBy(
            () -> service.createIndustry(new AdminIndustryCreateRequest("SEMICONDUCTOR", "반도체", 1)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(AssetErrorCode.INDUSTRY_ALREADY_EXISTS);
  }

  @Test
  void convertsConcurrentDuplicateIndustryCodeToConflict() {
    given(industryRepository.existsByCode("AI")).willReturn(false);
    given(industryRepository.saveAndFlush(any(Industry.class)))
        .willThrow(new DataIntegrityViolationException("duplicate"));

    assertThatThrownBy(
            () -> service.createIndustry(new AdminIndustryCreateRequest("AI", "인공지능", 5)))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(AssetErrorCode.INDUSTRY_ALREADY_EXISTS);
  }

  @Test
  void updatesIndustryWithoutChangingCode() {
    Industry industry = Industry.create("BIO", "바이오", 4);
    given(industryRepository.findById(1L)).willReturn(Optional.of(industry));

    var response = service.updateIndustry(1L, new AdminIndustryUpdateRequest("헬스케어", 2, false));

    assertThat(response.code()).isEqualTo("BIO");
    assertThat(response.name()).isEqualTo("헬스케어");
    assertThat(response.displayOrder()).isEqualTo(2);
    assertThat(response.active()).isFalse();
  }

  @Test
  void replacesAssetIndustriesAfterDeduplicatingAndSortingIds() {
    Asset asset = org.mockito.Mockito.mock(Asset.class);
    Industry later = industry(2L, "BIO", 20);
    Industry earlier = industry(1L, "SEMICONDUCTOR", 10);
    given(assetRepository.findById(7L)).willReturn(Optional.of(asset));
    given(asset.getCategory()).willReturn(AssetCategory.STOCK);
    given(industryRepository.findAllById(List.of(2L, 1L))).willReturn(List.of(later, earlier));

    var response =
        service.updateAssetIndustries(
            7L, new AdminAssetIndustriesUpdateRequest(List.of(2L, 1L, 2L)));

    then(asset).should().replaceIndustries(List.of(earlier, later));
    assertThat(response.industries()).extracting("code").containsExactly("SEMICONDUCTOR", "BIO");
  }

  @Test
  void paginatesAndAddsAndRemovesAssetFromIndustry() {
    Asset asset = org.mockito.Mockito.mock(Asset.class);
    Industry industry = org.mockito.Mockito.mock(Industry.class);
    given(industryRepository.findById(1L)).willReturn(Optional.of(industry));
    given(assetRepository.findById(7L)).willReturn(Optional.of(asset));
    given(asset.getCategory()).willReturn(AssetCategory.STOCK);
    given(
            assetRepository.findAllByIndustries_IdAndCategoryInAndListedTrueOrderByNameAscIdAsc(
                1L, Set.of(AssetCategory.STOCK, AssetCategory.ETF), PageRequest.of(1, 20)))
        .willReturn(new PageImpl<>(List.of(), PageRequest.of(1, 20), 42));

    var response = service.getIndustryAssets(1L, 2, 20);
    service.addIndustryAsset(1L, 7L);
    service.removeIndustryAsset(1L, 7L);

    assertThat(response.page()).isEqualTo(2);
    assertThat(response.totalPages()).isEqualTo(3);
    assertThat(response.totalElements()).isEqualTo(42);
    assertThat(response.hasNext()).isTrue();

    then(asset).should().addIndustry(industry);
    then(asset).should().removeIndustry(industry);
  }

  @Test
  void rejectsIndustryConnectionsForNonSecurityAssets() {
    Asset asset = org.mockito.Mockito.mock(Asset.class);
    Industry industry = org.mockito.Mockito.mock(Industry.class);
    given(industryRepository.findById(1L)).willReturn(Optional.of(industry));
    given(assetRepository.findById(7L)).willReturn(Optional.of(asset));
    given(asset.getCategory()).willReturn(AssetCategory.GOLD);

    assertThatThrownBy(() -> service.addIndustryAsset(1L, 7L))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(AssetErrorCode.ASSET_INDUSTRY_UNSUPPORTED);
    assertThatThrownBy(
            () ->
                service.updateAssetIndustries(
                    7L, new AdminAssetIndustriesUpdateRequest(List.of(1L))))
        .isInstanceOf(BusinessException.class)
        .extracting(exception -> ((BusinessException) exception).errorCode())
        .isEqualTo(AssetErrorCode.ASSET_INDUSTRY_UNSUPPORTED);
  }

  private Industry industry(long id, String code, int displayOrder) {
    Industry industry = org.mockito.Mockito.mock(Industry.class);
    given(industry.getId()).willReturn(id);
    given(industry.getCode()).willReturn(code);
    given(industry.getName()).willReturn(code);
    given(industry.getDisplayOrder()).willReturn(displayOrder);
    given(industry.isActive()).willReturn(true);
    return industry;
  }
}
