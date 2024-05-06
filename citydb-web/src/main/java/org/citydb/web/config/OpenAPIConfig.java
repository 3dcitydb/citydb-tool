package org.citydb.web.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenAPIConfig {

    @Value("${citydb.openapi.dev-url}")
    private String devUrl;

    @Bean
    public OpenAPI myOpenAPI() {
        Server devServer = new Server();
        devServer.setUrl(devUrl);
        devServer.setDescription("Example OGC API Server");

        License mitLicense = new License()
                .name("Apache License")
                .url("http://www.apache.org/licenses/");

        Info info = new Info()
                .title("OGC API - Features Example")
                .version("1.0")
                .description("OGC API - Features - Part 1: Core corrigendum 1.0.1 is an OGC Standard.")
                .termsOfService("https://www.ogc.org/standards/")
                .license(mitLicense);

        return new OpenAPI().info(info).servers(List.of(devServer));
    }
}