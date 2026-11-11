package org.grit.daynomy.league.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.math.BigDecimal;
import java.util.List;
import org.grit.daynomy.auth.token.AuthenticatedMember;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.BusinessException;
import org.grit.daynomy.league.dto.SharedPortfolioDto.*;
import org.grit.daynomy.league.exception.LeagueErrorCode;
import org.grit.daynomy.league.service.SharedPortfolioService;
import org.grit.daynomy.member.domain.MemberRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = SharedPortfolioController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@EnableWebSecurity
class SharedPortfolioControllerTest {
  private static final String PATH = "/api/users/me/shared-portfolio";
  @Autowired private MockMvc mvc;
  @MockitoBean private SharedPortfolioService service;

  @BeforeEach
  void authenticate() {
    SecurityContextHolder.getContext()
        .setAuthentication(
            new UsernamePasswordAuthenticationToken(
                new AuthenticatedMember(7L, MemberRole.USER), null, List.of()));
  }

  @AfterEach
  void clearAuthentication() {
    SecurityContextHolder.clearContext();
  }

  @Test
  void readsOwnPortfolioAndNeverCachesIt() throws Exception {
    given(service.getMine(7L))
        .willReturn(
            new PortfolioResponse(
                List.of(), BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, true));
    mvc.perform(get(PATH))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"))
        .andExpect(jsonPath("$.holdings").isEmpty())
        .andExpect(jsonPath("$.totalPurchaseAmount").value(0))
        .andExpect(jsonPath("$.pricesComplete").value(true));
    then(service).should().getMine(7L);
  }

  @Test
  void convertsImportBodyWithoutTrustingAnOwnerFromTheRequest() throws Exception {
    mvc.perform(
            post(PATH + "/import")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    """
        {"memberId":99,"holdings":[{"assetId":12,"quantity":2,"averagePurchasePrice":123.45}],
         "overwriteExisting":true}
        """))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"));
    then(service)
        .should()
        .importHoldings(
            7L,
            new ImportRequest(
                List.of(new HoldingInput(12L, 2, new BigDecimal("123.45"), null)), true));
  }

  @ParameterizedTest
  @CsvSource({
    "assetId,0,2,100",
    "quantity,12,0,100",
    "quantity,12,1000000001,100",
    "averagePurchasePrice,12,2,0",
    "averagePurchasePrice,12,2,1000000001",
    "averagePurchasePrice,12,2,1.001"
  })
  void validatesNestedHoldingInputs(String field, String assetId, String quantity, String price)
      throws Exception {
    String body =
        "{\"overwriteExisting\":false,\"holdings\":[{\"assetId\":%s,\"quantity\":%s,\"averagePurchasePrice\":%s}]}"
            .formatted(assetId, quantity, price);
    mvc.perform(post(PATH + "/import").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[*].field", hasItem("holdings[0]." + field)))
        .andExpect(jsonPath("$.errors[0].reason").isNotEmpty());
    verifyNoInteractions(service);
  }

  @ParameterizedTest
  @ValueSource(
      strings = {
        "{\"overwriteExisting\":false}",
        "{\"overwriteExisting\":false,\"holdings\":[]}",
        "{\"overwriteExisting\":false,\"holdings\":[null]}"
      })
  void requiresNonemptyHoldingsWithoutNullElements(String body) throws Exception {
    mvc.perform(post(PATH + "/import").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors").isNotEmpty());
    verifyNoInteractions(service);
  }

  @Test
  void visibilityAndDeletionUsePathAssetAndAuthenticatedMember() throws Exception {
    mvc.perform(
            patch(PATH + "/holdings/12/visibility")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"hidden\":false}"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"));
    mvc.perform(delete(PATH + "/holdings/12"))
        .andExpect(status().isOk())
        .andExpect(header().string("Cache-Control", "no-store"));
    then(service).should().changeVisibility(7L, 12L, new VisibilityRequest(false));
    then(service).should().removeHolding(7L, 12L);
  }

  @Test
  void rejectsMissingVisibilityAndMalformedAssetId() throws Exception {
    mvc.perform(
            patch(PATH + "/holdings/12/visibility")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("hidden"));
    mvc.perform(delete(PATH + "/holdings/invalid"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.errors[0].field").value("assetId"));
    verifyNoInteractions(service);
  }

  @Test
  void reportsSourceManagedDeletionConflict() throws Exception {
    given(service.removeHolding(7L, 12L))
        .willThrow(new BusinessException(LeagueErrorCode.SHARED_HOLDING_SOURCE_MANAGED));
    mvc.perform(delete(PATH + "/holdings/12"))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.code").value("SHARED_HOLDING_SOURCE_MANAGED"))
        .andExpect(
            jsonPath("$.message").value(LeagueErrorCode.SHARED_HOLDING_SOURCE_MANAGED.message()));
  }
}
