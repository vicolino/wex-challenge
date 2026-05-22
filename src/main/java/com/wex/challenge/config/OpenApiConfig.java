package com.wex.challenge.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI wexOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("WEX Purchase Transactions API")
                        .description("""
                                Service that stores USD purchase transactions and retrieves them
                                converted to a target currency using the U.S. Treasury Reporting Rates
                                of Exchange dataset.
                                """)
                        .version("1.0.0")
                        .contact(new Contact().name("WEX Challenge"))
                        .license(new License().name("MIT")));
    }
}
