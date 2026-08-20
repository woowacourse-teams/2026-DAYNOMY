package org.grit.daynomy.common.exception;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTest {

  private MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc =
        MockMvcBuilders.standaloneSetup(new TestController())
            .setControllerAdvice(new GlobalExceptionHandler())
            .build();
  }

  @Test
  void returnsFieldErrorsWhenRequestBodyValidationFails() throws Exception {
    mockMvc
        .perform(
            post("/test")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"name\":\"\",\"count\":0}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_REQUEST"))
        .andExpect(jsonPath("$.message").value("요청값이 올바르지 않습니다."))
        .andExpect(jsonPath("$.errors").isArray())
        .andExpect(jsonPath("$.errors.length()").value(2))
        .andExpect(jsonPath("$.errors[0].rejectedValue").doesNotExist())
        .andExpect(jsonPath("$.errors[1].rejectedValue").doesNotExist());
  }

  @RestController
  private static class TestController {

    @PostMapping("/test")
    void test(@Valid @RequestBody TestRequest request) {}
  }

  private record TestRequest(
      @NotBlank(message = "이름을 입력해주세요.") String name,
      @Min(value = 1, message = "개수는 1 이상이어야 합니다.") int count) {}
}
