package org.grit.daynomy.asset.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.grit.daynomy.asset.dto.AssetCandidateResponse;
import org.grit.daynomy.asset.dto.AssetCandidatesResponse;
import org.grit.daynomy.asset.service.AssetCandidateService;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = AssetCandidateController.class,
    excludeFilters =
        @ComponentScan.Filter(
            type = FilterType.ASSIGNABLE_TYPE,
            classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AssetCandidateControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AssetCandidateService assetCandidateService;

  @Test
  @DisplayName("코스닥 대표 종목 순위 조회는 기본 페이지 조건으로 서비스에 위임한다")
  void getKosdaqTopRankingsUsesDefaultPageCondition() throws Exception {
    when(assetCandidateService.getKosdaqTopRankings(null, 1, 20))
        .thenReturn(
            new AssetCandidatesResponse(
                "2026-08-21",
                List.of(new AssetCandidateResponse(1, "000001", "일위종목")),
                1,
                20,
                8,
                150,
                true));

    mockMvc
        .perform(get("/api/assets/kosdaq/top"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.baseDate").value("2026-08-21"))
        .andExpect(jsonPath("$.rankings[0].rank").value(1))
        .andExpect(jsonPath("$.rankings[0].code").value("000001"))
        .andExpect(jsonPath("$.rankings[0].name").value("일위종목"))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.size").value(20))
        .andExpect(jsonPath("$.totalPages").value(8))
        .andExpect(jsonPath("$.totalElements").value(150))
        .andExpect(jsonPath("$.hasNext").value(true));

    verify(assetCandidateService).getKosdaqTopRankings(null, 1, 20);
  }

  @Test
  @DisplayName("코스닥 대표 종목 순위 조회는 요청한 페이지 조건을 서비스에 전달한다")
  void getKosdaqTopRankingsUsesRequestedPageCondition() throws Exception {
    when(assetCandidateService.getKosdaqTopRankings(null, 2, 10))
        .thenReturn(new AssetCandidatesResponse("2026-08-21", List.of(), 2, 10, 15, 150, true));

    mockMvc
        .perform(get("/api/assets/kosdaq/top").param("page", "2").param("size", "10"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(2))
        .andExpect(jsonPath("$.size").value(10));

    verify(assetCandidateService).getKosdaqTopRankings(null, 2, 10);
  }

  @Test
  @DisplayName("코스닥 대표 종목 검색은 검색어와 페이지 조건을 서비스에 전달한다")
  void getKosdaqTopRankingsUsesKeyword() throws Exception {
    when(assetCandidateService.getKosdaqTopRankings(" 에코프로 ", 1, 20))
        .thenReturn(new AssetCandidatesResponse("2026-08-21", List.of(), 1, 20, 0, 0, false));

    mockMvc.perform(get("/api/assets/kosdaq/top").param("q", " 에코프로 ")).andExpect(status().isOk());

    verify(assetCandidateService).getKosdaqTopRankings(" 에코프로 ", 1, 20);
  }

  @Test
  @DisplayName("문자나 숫자가 없는 검색어는 요청을 거부한다")
  void getKosdaqTopRankingsRejectsInvalidKeyword() throws Exception {
    mockMvc
        .perform(get("/api/assets/kosdaq/top").param("q", "!%_"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("keyword"))
        .andExpect(jsonPath("$.errors[0].reason").value("올바른 검색어를 입력해주세요."));

    verifyNoInteractions(assetCandidateService);
  }

  @Test
  @DisplayName("100자를 초과한 검색어는 요청을 거부한다")
  void getKosdaqTopRankingsRejectsTooLongKeyword() throws Exception {
    mockMvc
        .perform(get("/api/assets/kosdaq/top").param("q", "가".repeat(101)))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("keyword"))
        .andExpect(jsonPath("$.errors[0].reason").value("검색어는 100자 이하여야 합니다."));

    verifyNoInteractions(assetCandidateService);
  }

  @Test
  @DisplayName("페이지 번호가 1보다 작으면 요청을 거부한다")
  void getKosdaqTopRankingsRejectsInvalidPage() throws Exception {
    mockMvc
        .perform(get("/api/assets/kosdaq/top").param("page", "0"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("page"));

    verifyNoInteractions(assetCandidateService);
  }

  @Test
  @DisplayName("페이지 크기가 100보다 크면 요청을 거부한다")
  void getKosdaqTopRankingsRejectsInvalidSize() throws Exception {
    mockMvc
        .perform(get("/api/assets/kosdaq/top").param("size", "101"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("size"));

    verifyNoInteractions(assetCandidateService);
  }
}
