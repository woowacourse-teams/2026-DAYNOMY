package org.grit.daynomy.config;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.Operation;
import io.swagger.v3.oas.models.PathItem;
import io.swagger.v3.oas.models.Paths;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class SwaggerConfigTest {

  private final SwaggerConfig swaggerConfig = new SwaggerConfig();

  @Test
  @DisplayName("관리자 API 인증과 CSRF 보안 스키마를 등록한다")
  void registersAdminSecuritySchemes() {
    OpenAPI openApi = openApiWithAdminOperations();

    swaggerConfig.adminSecurityOpenApiCustomizer().customise(openApi);

    SecurityScheme accessToken = openApi.getComponents().getSecuritySchemes().get("access_token");
    assertThat(accessToken.getType()).isEqualTo(SecurityScheme.Type.APIKEY);
    assertThat(accessToken.getIn()).isEqualTo(SecurityScheme.In.COOKIE);
    assertThat(accessToken.getName()).isEqualTo("access_token");
    assertThat(accessToken.getDescription()).contains("ADMIN 권한");

    SecurityScheme csrfToken = openApi.getComponents().getSecuritySchemes().get("csrfToken");
    assertThat(csrfToken.getType()).isEqualTo(SecurityScheme.Type.APIKEY);
    assertThat(csrfToken.getIn()).isEqualTo(SecurityScheme.In.HEADER);
    assertThat(csrfToken.getName()).isEqualTo("X-XSRF-TOKEN");
    assertThat(csrfToken.getDescription()).contains("XSRF-TOKEN 쿠키");
  }

  @Test
  @DisplayName("관리자 API의 안전한 HTTP 메서드에는 인증만 요구한다")
  void appliesOnlyAuthenticationToSafeAdminMethods() {
    OpenAPI openApi = openApiWithAdminOperations();

    swaggerConfig.adminSecurityOpenApiCustomizer().customise(openApi);

    PathItem adminPath = openApi.getPaths().get("/api/admin/news");
    assertAuthenticationOnly(adminPath.getGet());
    assertAuthenticationOnly(adminPath.getHead());
    assertAuthenticationOnly(adminPath.getOptions());
    assertAuthenticationOnly(adminPath.getTrace());
  }

  @Test
  @DisplayName("관리자 API의 상태 변경 HTTP 메서드에는 인증과 CSRF를 함께 요구한다")
  void appliesAuthenticationAndCsrfToUnsafeAdminMethods() {
    OpenAPI openApi = openApiWithAdminOperations();

    swaggerConfig.adminSecurityOpenApiCustomizer().customise(openApi);

    PathItem adminPath = openApi.getPaths().get("/api/admin/news");
    assertAuthenticationAndCsrf(adminPath.getPost());
    assertAuthenticationAndCsrf(adminPath.getPut());
    assertAuthenticationAndCsrf(adminPath.getPatch());
    assertAuthenticationAndCsrf(adminPath.getDelete());
  }

  @Test
  @DisplayName("공개 API에는 관리자 보안 요구사항을 적용하지 않는다")
  void doesNotApplyAdminSecurityToPublicApi() {
    OpenAPI openApi = openApiWithAdminOperations();

    swaggerConfig.adminSecurityOpenApiCustomizer().customise(openApi);

    assertThat(openApi.getPaths().get("/api/news").getGet().getSecurity()).isNull();
  }

  private OpenAPI openApiWithAdminOperations() {
    PathItem adminPath = new PathItem();
    for (PathItem.HttpMethod method : PathItem.HttpMethod.values()) {
      adminPath.operation(method, new Operation());
    }
    return new OpenAPI()
        .paths(
            new Paths()
                .addPathItem("/api/admin/news", adminPath)
                .addPathItem("/api/news", new PathItem().get(new Operation())));
  }

  private void assertAuthenticationOnly(Operation operation) {
    List<SecurityRequirement> security = operation.getSecurity();
    assertThat(security).hasSize(1);
    assertThat(security.getFirst()).containsOnlyKeys("access_token");
  }

  private void assertAuthenticationAndCsrf(Operation operation) {
    List<SecurityRequirement> security = operation.getSecurity();
    assertThat(security).hasSize(1);
    assertThat(security.getFirst()).containsOnlyKeys("access_token", "csrfToken");
  }
}
