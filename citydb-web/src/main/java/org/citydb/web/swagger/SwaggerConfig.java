package org.citydb.web.swagger;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {
    @Bean
    public OpenAPI openAPI() {
        Info info = new Info()
                .title("3DCityDB OGC API - Features")
                .version("1.0")
                .description("3DCityDB OGC API - Features.")
                .termsOfService("https://www.ogc.org/standards/")
                .license(new License()
                        .name("Apache License")
                        .url("http://www.apache.org/licenses/"));

        return new OpenAPI().info(info);
    }
}