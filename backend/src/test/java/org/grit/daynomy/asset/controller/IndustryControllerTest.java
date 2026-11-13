package org.grit.daynomy.asset.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.grit.daynomy.asset.dto.IndustriesResponse;
import org.grit.daynomy.asset.dto.IndustryResponse;
import org.grit.daynomy.asset.service.IndustryService;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@AutoConfigureMockMvc(addFilters = false)
@WebMvcTest(
    controllers = IndustryController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
class IndustryControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private IndustryService industryService;

  @Test
  void returnsActiveIndustriesInDisplayOrder() throws Exception {
    IndustriesResponse response =
        new IndustriesResponse(
            List.of(
                new IndustryResponse("SEMICONDUCTOR", "반도체"),
                new IndustryResponse("AUTOMOBILE", "자동차")));
    given(industryService.getActiveIndustries()).willReturn(response);

    mockMvc
        .perform(get("/api/industries"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.industries[0].code").value("SEMICONDUCTOR"))
        .andExpect(jsonPath("$.industries[0].name").value("반도체"))
        .andExpect(jsonPath("$.industries[1].code").value("AUTOMOBILE"));

    then(industryService).should().getActiveIndustries();
  }
}
