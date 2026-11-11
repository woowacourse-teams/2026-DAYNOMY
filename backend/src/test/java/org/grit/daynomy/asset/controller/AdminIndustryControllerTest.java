package org.grit.daynomy.asset.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.grit.daynomy.asset.domain.AssetCategory;
import org.grit.daynomy.asset.domain.StockMarket;
import org.grit.daynomy.asset.dto.AdminAssetIndustriesResponse;
import org.grit.daynomy.asset.dto.AdminAssetIndustriesUpdateRequest;
import org.grit.daynomy.asset.dto.AdminIndustriesResponse;
import org.grit.daynomy.asset.dto.AdminIndustryAssetsResponse;
import org.grit.daynomy.asset.dto.AdminIndustryCreateRequest;
import org.grit.daynomy.asset.dto.AdminIndustryResponse;
import org.grit.daynomy.asset.dto.AdminIndustryUpdateRequest;
import org.grit.daynomy.asset.dto.StockSearchItemResponse;
import org.grit.daynomy.asset.service.AdminIndustryService;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(
    controllers = {AdminIndustryController.class, AdminAssetIndustryController.class},
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
class AdminIndustryControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private AdminIndustryService adminIndustryService;

  @Test
  void managesIndustryMasterData() throws Exception {
    AdminIndustryResponse industry = new AdminIndustryResponse(1L, "SEMICONDUCTOR", "반도체", 1, true);
    given(adminIndustryService.getIndustries())
        .willReturn(new AdminIndustriesResponse(List.of(industry)));
    given(adminIndustryService.createIndustry(new AdminIndustryCreateRequest("AI", "인공지능", 5)))
        .willReturn(new AdminIndustryResponse(5L, "AI", "인공지능", 5, true));
    given(
            adminIndustryService.updateIndustry(
                1L, new AdminIndustryUpdateRequest("반도체·장비", 2, false)))
        .willReturn(new AdminIndustryResponse(1L, "SEMICONDUCTOR", "반도체·장비", 2, false));

    mockMvc
        .perform(get("/api/admin/industries"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.industries[0].code").value("SEMICONDUCTOR"));
    mockMvc
        .perform(
            post("/api/admin/industries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"AI\",\"name\":\"인공지능\",\"displayOrder\":5}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.id").value(5));
    mockMvc
        .perform(
            patch("/api/admin/industries/1")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"반도체·장비\",\"displayOrder\":2,\"active\":false}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.active").value(false));
  }

  @Test
  void rejectsInvalidIndustryCode() throws Exception {
    mockMvc
        .perform(
            post("/api/admin/industries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"code\":\"semiconductor\",\"name\":\"반도체\",\"displayOrder\":1}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void readsAndReplacesAssetIndustries() throws Exception {
    AdminIndustryResponse industry = new AdminIndustryResponse(1L, "SEMICONDUCTOR", "반도체", 1, true);
    given(adminIndustryService.getAssetIndustries(7L))
        .willReturn(new AdminAssetIndustriesResponse(7L, List.of(industry)));
    given(
            adminIndustryService.updateAssetIndustries(
                7L, new AdminAssetIndustriesUpdateRequest(List.of(1L, 2L))))
        .willReturn(new AdminAssetIndustriesResponse(7L, List.of(industry)));

    mockMvc
        .perform(get("/api/admin/assets/7/industries"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.industries[0].id").value(1));
    mockMvc
        .perform(
            put("/api/admin/assets/7/industries")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"industryIds\":[1,2]}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.assetId").value(7));

    then(adminIndustryService)
        .should()
        .updateAssetIndustries(7L, new AdminAssetIndustriesUpdateRequest(List.of(1L, 2L)));
  }

  @Test
  void readsAddsAndRemovesIndustryAssets() throws Exception {
    StockSearchItemResponse stock =
        new StockSearchItemResponse(
            7L, "005930", "삼성전자", AssetCategory.STOCK, StockMarket.KOSPI, "KRW");
    AdminIndustryAssetsResponse response =
        new AdminIndustryAssetsResponse(1L, List.of(stock), 1, 20, 2, 25L, true);
    given(adminIndustryService.getIndustryAssets(1L, 1, 20)).willReturn(response);

    mockMvc
        .perform(get("/api/admin/industries/1/assets"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items[0].name").value("삼성전자"))
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.totalElements").value(25))
        .andExpect(jsonPath("$.hasNext").value(true));
    mockMvc.perform(put("/api/admin/industries/1/assets/7")).andExpect(status().isNoContent());
    mockMvc.perform(delete("/api/admin/industries/1/assets/7")).andExpect(status().isNoContent());
    mockMvc
        .perform(get("/api/admin/industries/1/assets?page=0&size=101"))
        .andExpect(status().isBadRequest());

    then(adminIndustryService).should().addIndustryAsset(1L, 7L);
    then(adminIndustryService).should().removeIndustryAsset(1L, 7L);
  }
}
