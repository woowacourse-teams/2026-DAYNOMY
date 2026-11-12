package org.grit.daynomy.asset.service;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.grit.daynomy.asset.domain.Industry;
import org.grit.daynomy.asset.dto.IndustriesResponse;
import org.grit.daynomy.asset.repository.IndustryRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@RequiredArgsConstructor
@Service
public class IndustryService {

  private final IndustryRepository industryRepository;

  @Transactional(readOnly = true)
  public IndustriesResponse getActiveIndustries() {
    return IndustriesResponse.from(findActiveIndustries());
  }

  @Transactional(readOnly = true)
  public List<Industry> findActiveIndustries() {
    return industryRepository.findAllByActiveTrueOrderByDisplayOrderAscIdAsc();
  }

  @Transactional(readOnly = true)
  public boolean isActive(String code) {
    return industryRepository.findByCodeAndActiveTrue(code).isPresent();
  }
}
