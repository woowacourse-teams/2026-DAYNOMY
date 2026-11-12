package org.grit.daynomy.asset.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.Asset;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.Industry;
import org.grit.daynomy.asset.dto.AdminAssetIndustriesResponse;
import org.grit.daynomy.asset.dto.AdminAssetIndustriesUpdateRequest;
import org.grit.daynomy.asset.dto.AdminIndustriesResponse;
import org.grit.daynomy.asset.dto.AdminIndustryAssetsResponse;
import org.grit.daynomy.asset.dto.AdminIndustryCreateRequest;
import org.grit.daynomy.asset.dto.AdminIndustryResponse;
import org.grit.daynomy.asset.dto.AdminIndustryUpdateRequest;
import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.asset.repository.AssetRepository;
import org.grit.daynomy.asset.repository.IndustryRepository;
import org.grit.daynomy.common.exception.BusinessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class AdminIndustryService {

  private static final Set<AssetCategory> SECURITY_CATEGORIES =
      Set.of(AssetCategory.STOCK, AssetCategory.ETF);
  private static final Comparator<Industry> INDUSTRY_ORDER =
      Comparator.comparingInt(Industry::getDisplayOrder).thenComparing(Industry::getId);

  private final IndustryRepository industryRepository;
  private final AssetRepository assetRepository;

  @Transactional(readOnly = true)
  public AdminIndustriesResponse getIndustries() {
    return AdminIndustriesResponse.from(industryRepository.findAllByOrderByDisplayOrderAscIdAsc());
  }

  @Transactional
  public AdminIndustryResponse createIndustry(AdminIndustryCreateRequest request) {
    if (industryRepository.existsByCode(request.code())) {
      throw new BusinessException(AssetErrorCode.INDUSTRY_ALREADY_EXISTS);
    }
    Industry industry =
        Industry.create(request.code(), request.name().strip(), request.displayOrder());
    try {
      return AdminIndustryResponse.from(industryRepository.saveAndFlush(industry));
    } catch (DataIntegrityViolationException exception) {
      throw new BusinessException(AssetErrorCode.INDUSTRY_ALREADY_EXISTS);
    }
  }

  @Transactional
  public AdminIndustryResponse updateIndustry(Long industryId, AdminIndustryUpdateRequest request) {
    Industry industry = getIndustry(industryId);
    industry.update(request.name().strip(), request.displayOrder(), request.active());
    return AdminIndustryResponse.from(industry);
  }

  @Transactional(readOnly = true)
  public AdminIndustryAssetsResponse getIndustryAssets(Long industryId, int page, int size) {
    getIndustry(industryId);
    return AdminIndustryAssetsResponse.from(
        industryId,
        assetRepository.findAllByIndustries_IdAndCategoryInAndListedTrueOrderByNameAscIdAsc(
            industryId, SECURITY_CATEGORIES, PageRequest.of(page - 1, size)));
  }

  @Transactional
  public void addIndustryAsset(Long industryId, Long assetId) {
    Industry industry = getIndustry(industryId);
    getIndustryAsset(assetId).addIndustry(industry);
  }

  @Transactional
  public void removeIndustryAsset(Long industryId, Long assetId) {
    Industry industry = getIndustry(industryId);
    getAsset(assetId).removeIndustry(industry);
  }

  @Transactional(readOnly = true)
  public AdminAssetIndustriesResponse getAssetIndustries(Long assetId) {
    Asset asset = getAsset(assetId);
    return AdminAssetIndustriesResponse.from(assetId, sorted(asset.getIndustries()));
  }

  @Transactional
  public AdminAssetIndustriesResponse updateAssetIndustries(
      Long assetId, AdminAssetIndustriesUpdateRequest request) {
    Asset asset = getIndustryAsset(assetId);
    List<Long> industryIds = new LinkedHashSet<>(request.industryIds()).stream().toList();
    List<Industry> industries = industryRepository.findAllById(industryIds);
    if (industries.size() != industryIds.size()) {
      throw new BusinessException(AssetErrorCode.INDUSTRY_ID_NOT_FOUND);
    }
    List<Industry> sorted = sorted(industries);
    asset.replaceIndustries(sorted);
    return AdminAssetIndustriesResponse.from(assetId, sorted);
  }

  private Industry getIndustry(Long industryId) {
    return industryRepository
        .findById(industryId)
        .orElseThrow(() -> new BusinessException(AssetErrorCode.INDUSTRY_ID_NOT_FOUND));
  }

  private Asset getAsset(Long assetId) {
    return assetRepository
        .findById(assetId)
        .orElseThrow(() -> new BusinessException(AssetErrorCode.ASSET_NOT_FOUND));
  }

  private Asset getIndustryAsset(Long assetId) {
    Asset asset = getAsset(assetId);
    if (!SECURITY_CATEGORIES.contains(asset.getCategory())) {
      throw new BusinessException(AssetErrorCode.ASSET_INDUSTRY_UNSUPPORTED);
    }
    return asset;
  }

  private List<Industry> sorted(Iterable<Industry> industries) {
    ArrayList<Industry> result = new ArrayList<>();
    industries.forEach(result::add);
    result.sort(INDUSTRY_ORDER);
    return result;
  }
}
