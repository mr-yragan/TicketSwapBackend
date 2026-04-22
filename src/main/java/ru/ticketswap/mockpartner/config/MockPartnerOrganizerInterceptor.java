package ru.ticketswap.mockpartner.config;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.HandlerMapping;
import ru.ticketswap.mockpartner.service.MockPartnerOrganizerResolver;

import java.util.Map;

@Component
public class MockPartnerOrganizerInterceptor implements HandlerInterceptor {

    private final MockPartnerOrganizerResolver mockPartnerOrganizerResolver;

    public MockPartnerOrganizerInterceptor(MockPartnerOrganizerResolver mockPartnerOrganizerResolver) {
        this.mockPartnerOrganizerResolver = mockPartnerOrganizerResolver;
    }

    @Override
    @SuppressWarnings("unchecked")
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        Object attributes = request.getAttribute(HandlerMapping.URI_TEMPLATE_VARIABLES_ATTRIBUTE);
        if (!(attributes instanceof Map<?, ?> variables)) {
            return true;
        }

        Object organizerCode = variables.get("organizerCode");
        if (organizerCode instanceof String organizerCodeValue) {
            mockPartnerOrganizerResolver.requireSupportedOrganizer(organizerCodeValue);
        }

        return true;
    }
}
