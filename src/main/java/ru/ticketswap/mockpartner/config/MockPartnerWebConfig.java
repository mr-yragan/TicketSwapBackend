package ru.ticketswap.mockpartner.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class MockPartnerWebConfig implements WebMvcConfigurer {

    private final MockPartnerOrganizerInterceptor mockPartnerOrganizerInterceptor;

    public MockPartnerWebConfig(MockPartnerOrganizerInterceptor mockPartnerOrganizerInterceptor) {
        this.mockPartnerOrganizerInterceptor = mockPartnerOrganizerInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(mockPartnerOrganizerInterceptor)
                .addPathPatterns("/api/mock/partners/{organizerCode}/**");
    }
}
