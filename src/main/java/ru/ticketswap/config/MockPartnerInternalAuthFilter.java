package ru.ticketswap.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MockPartnerInternalAuthFilter extends OncePerRequestFilter {

    public static final String INTERNAL_TOKEN_HEADER = "X-Mock-Partner-Token";

    private final TicketSwapProperties properties;

    public MockPartnerInternalAuthFilter(TicketSwapProperties properties) {
        this.properties = properties;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        return !properties.getMockPartner().isEnabled() || !request.getRequestURI().startsWith("/api/mock/partners/");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String expectedToken = properties.getMockPartner().getInternalToken();
        String providedToken = request.getHeader(INTERNAL_TOKEN_HEADER);

        if (expectedToken == null || expectedToken.isBlank() || !constantTimeEquals(expectedToken, providedToken)) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"Mock partner API is internal only\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean constantTimeEquals(String expected, String provided) {
        if (provided == null) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                provided.getBytes(StandardCharsets.UTF_8)
        );
    }
}
