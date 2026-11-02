package org.grit.daynomy.person.controller;

import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.grit.daynomy.auth.token.JwtAuthenticationFilter;
import org.grit.daynomy.common.exception.GlobalExceptionHandler;
import org.grit.daynomy.person.dto.FeaturedPeoplePageResponse;
import org.grit.daynomy.person.service.FeaturedPersonService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.ComponentScan.Filter;
import org.springframework.context.annotation.FilterType;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@WebMvcTest(
    controllers = FeaturedPersonController.class,
    excludeFilters =
        @Filter(type = FilterType.ASSIGNABLE_TYPE, classes = JwtAuthenticationFilter.class))
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class FeaturedPersonControllerTest {

  @Autowired private MockMvc mockMvc;
  @MockitoBean private FeaturedPersonService featuredPersonService;

  @Test
  void getPeopleUsesDefaultPageConditions() throws Exception {
    given(featuredPersonService.getPeople(1, 6, null))
        .willReturn(new FeaturedPeoplePageResponse(List.of(), 1, 6, 0, 0, false));

    mockMvc
        .perform(get("/api/people"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.people").isArray())
        .andExpect(jsonPath("$.page").value(1))
        .andExpect(jsonPath("$.size").value(6))
        .andExpect(jsonPath("$.totalElements").value(0))
        .andExpect(jsonPath("$.hasNext").value(false));

    then(featuredPersonService).should().getPeople(1, 6, null);
  }

  @Test
  void getPeopleAcceptsPersonAndPageConditions() throws Exception {
    given(featuredPersonService.getPeople(2, 12, 7L))
        .willReturn(new FeaturedPeoplePageResponse(List.of(), 2, 12, 3, 30, true));

    mockMvc
        .perform(get("/api/people").param("page", "2").param("size", "12").param("personId", "7"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.page").value(2))
        .andExpect(jsonPath("$.size").value(12))
        .andExpect(jsonPath("$.totalElements").value(30));

    then(featuredPersonService).should().getPeople(2, 12, 7L);
  }

  @Test
  void getPeopleRejectsInvalidPageConditions() throws Exception {
    mockMvc
        .perform(get("/api/people").param("page", "0").param("size", "101"))
        .andExpect(status().isBadRequest());

    then(featuredPersonService).should(never()).getPeople(0, 101, null);
  }
}
