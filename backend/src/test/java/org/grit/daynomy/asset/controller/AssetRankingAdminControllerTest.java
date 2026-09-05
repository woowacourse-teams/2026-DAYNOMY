package org.grit.daynomy.asset.controller;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.grit.daynomy.asset.exception.AssetErrorCode;
import org.grit.daynomy.asset.service.AssetRankingSyncService;
import org.grit.daynomy.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class AssetRankingAdminControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private AssetRankingSyncService assetRankingSyncService;

  @Test
  @DisplayName("관리자 코스닥 대표 종목 순위 수동 동기화를 실행한다")
  void syncKosdaqTopRankings() throws Exception {
    when(assetRankingSyncService.syncKosdaqTopRankings()).thenReturn(150);

    mockMvc
        .perform(
            post("/api/admin/assets/kosdaq/top/sync")
                .with(csrf())
                .with(user("admin").roles("ADMIN")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.savedCount").value(150));

    verify(assetRankingSyncService).syncKosdaqTopRankings();
  }

  @Test
  @DisplayName("비로그인 사용자는 관리자 코스닥 대표 종목 순위 수동 동기화를 요청할 수 없다")
  void syncKosdaqTopRankingsRejectsAnonymousUser() throws Exception {
    mockMvc
        .perform(post("/api/admin/assets/kosdaq/top/sync").with(csrf()))
        .andExpect(status().isUnauthorized())
        .andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
  }

  @Test
  @DisplayName("일반 사용자는 관리자 코스닥 대표 종목 순위 수동 동기화를 요청할 수 없다")
  void syncKosdaqTopRankingsRejectsUser() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/assets/kosdaq/top/sync").with(csrf()).with(user("user").roles("USER")))
        .andExpect(status().isForbidden())
        .andExpect(jsonPath("$.code").value("FORBIDDEN"));
  }

  @Test
  @DisplayName("관리자 코스닥 대표 종목 순위 수동 동기화가 진행 중이면 409를 반환한다")
  void syncKosdaqTopRankingsAlreadyRunning() throws Exception {
    when(assetRankingSyncService.syncKosdaqTopRankings())
        .thenThrow(new BusinessException(AssetErrorCode.ASSET_RANKING_SYNC_ALREADY_RUNNING));

    mockMvc
        .perform(
            post("/api/admin/assets/kosdaq/top/sync")
                .with(csrf())
                .with(user("admin").roles("ADMIN")))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("ASSET_RANKING_SYNC_ALREADY_RUNNING"))
        .andExpect(jsonPath("$.message").value("자산 순위 동기화가 이미 진행 중입니다."));
  }
}
