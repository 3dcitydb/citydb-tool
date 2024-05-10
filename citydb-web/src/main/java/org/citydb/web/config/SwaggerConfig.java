package org.citydb.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class SwaggerConfig {

    @Value("${citydb.openapi.dev-url}")
    private String devUrl;

    @Bean
    public OpenAPI openAPI() {
        Server devServer = new Server();
        devServer.setUrl(devUrl);
        devServer.setDescription("Example OGC API Server");

        Info info = new Info()
                .title("OGC API - Features Example")
                .version("1.0")
                .description("OGC API - Features - Part 1: Core corrigendum 1.0.1 is an OGC Standard.")
                .termsOfService("https://www.ogc.org/standards/")
                .license(new License()
                        .name("Apache License")
                        .url("http://www.apache.org/licenses/"));


        return new OpenAPI().info(info).servers(List.of(devServer));
    }
}