package org.grit.daynomy.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.PathItem.HttpMethod;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import java.util.EnumSet;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

  private static final String ADMIN_API_PATH_PREFIX = "/api/admin/";
  private static final String ACCESS_TOKEN_SCHEME = "access_token";
  private static final String CSRF_TOKEN_SCHEME = "csrfToken";
  private static final Set<HttpMethod> CSRF_REQUIRED_METHODS =
      EnumSet.of(HttpMethod.POST, HttpMethod.PUT, HttpMethod.PATCH, HttpMethod.DELETE);

  @Bean
  public OpenAPI daynomyOpenAPI() {
    return new OpenAPI()
        .info(
            new Info()
                .title("Daynomy API")
                .version("v1")
                .description("Daynomy backend API documentation"));
  }

  @Bean
  public OpenApiCustomizer adminSecurityOpenApiCustomizer() {
    return openApi -> {
      registerAdminSecuritySchemes(openApi);
      if (openApi.getPaths() == null) {
        return;
      }

      openApi
          .getPaths()
          .forEach(
              (path, pathItem) -> {
                if (!path.startsWith(ADMIN_API_PATH_PREFIX)) {
                  return;
                }
                pathItem
                    .readOperationsMap()
                    .forEach(
                        (method, operation) -> {
                          SecurityRequirement requirement =
                              new SecurityRequirement().addList(ACCESS_TOKEN_SCHEME);
                          if (CSRF_REQUIRED_METHODS.contains(method)) {
                            requirement.addList(CSRF_TOKEN_SCHEME);
                          }
                          operation.addSecurityItem(requirement);
                        });
              });
    };
  }

  private void registerAdminSecuritySchemes(OpenAPI openApi) {
    if (openApi.getComponents() == null) {
      openApi.setComponents(new Components());
    }
    openApi
        .getComponents()
        .addSecuritySchemes(
            ACCESS_TOKEN_SCHEME,
            new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.COOKIE)
                .name("access_token")
                .description("관리자 API 인증 쿠키입니다. ADMIN 권한이 필요합니다."))
        .addSecuritySchemes(
            CSRF_TOKEN_SCHEME,
            new SecurityScheme()
                .type(SecurityScheme.Type.APIKEY)
                .in(SecurityScheme.In.HEADER)
                .name("X-XSRF-TOKEN")
                .description("XSRF-TOKEN 쿠키에서 가져온 CSRF 토큰 값입니다."));
  }
}
