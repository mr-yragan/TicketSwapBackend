package ru.ticketswap.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RateLimitFilter extends OncePerRequestFilter {

    private static final Duration WINDOW = Duration.ofMinutes(1);
    private static final int AUTH_LIMIT = 10;
    private static final int RESEND_LIMIT = 5;
    private static final int PURCHASE_LIMIT = 20;
    private static final int CLEANUP_EVERY_REQUESTS = 500;

    private static final Pattern HOLD_PATH = Pattern.compile("^/api/tickets/\\d+/hold$");
    private static final Pattern BUY_PATH = Pattern.compile("^/api/tickets/\\d+/buy$");

    private final Map<String, Counter> counters = new ConcurrentHashMap<>();
    private int requestsSinceCleanup = 0;

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        Rule rule = resolveRule(request);
        if (rule == null) {
            filterChain.doFilter(request, response);
            return;
        }

        if (!allow(request, rule)) {
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"message\":\"Too many requests\"}");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private Rule resolveRule(HttpServletRequest request) {
        String method = request.getMethod();
        String uri = request.getRequestURI();

        if (HttpMethod.POST.matches(method) && "/api/auth/login".equals(uri)) {
            return new Rule("auth-login", AUTH_LIMIT);
        }
        if (HttpMethod.POST.matches(method) && "/api/auth/password/forgot".equals(uri)) {
            return new Rule("password-forgot", RESEND_LIMIT);
        }
        if (HttpMethod.POST.matches(method) && "/api/auth/email/resend-verification".equals(uri)) {
            return new Rule("email-resend", RESEND_LIMIT);
        }
        if (HttpMethod.POST.matches(method) && "/api/auth/2fa/resend".equals(uri)) {
            return new Rule("2fa-resend", RESEND_LIMIT);
        }
        if (HttpMethod.POST.matches(method) && HOLD_PATH.matcher(uri).matches()) {
            return new Rule("hold", PURCHASE_LIMIT);
        }
        if (HttpMethod.POST.matches(method) && BUY_PATH.matcher(uri).matches()) {
            return new Rule("buy", PURCHASE_LIMIT);
        }
        return null;
    }

    private boolean allow(HttpServletRequest request, Rule rule) {
        cleanupOccasionally();
        String key = rule.name() + ":" + clientKey(request);
        Instant now = Instant.now();
        Counter counter = counters.compute(key, (ignored, existing) -> {
            if (existing == null || existing.windowStart().plus(WINDOW).isBefore(now)) {
                return new Counter(now, 1);
            }
            return new Counter(existing.windowStart(), existing.count() + 1);
        });
        return counter.count() <= rule.limit();
    }

    private String clientKey(HttpServletRequest request) {
        String forwardedFor = request.getHeader("X-Forwarded-For");
        if (forwardedFor != null && !forwardedFor.isBlank()) {
            return forwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private synchronized void cleanupOccasionally() {
        requestsSinceCleanup++;
        if (requestsSinceCleanup < CLEANUP_EVERY_REQUESTS) {
            return;
        }
        requestsSinceCleanup = 0;
        Instant cutoff = Instant.now().minus(WINDOW.multipliedBy(2));
        Iterator<Map.Entry<String, Counter>> iterator = counters.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, Counter> entry = iterator.next();
            if (entry.getValue().windowStart().isBefore(cutoff)) {
                iterator.remove();
            }
        }
    }

    private record Rule(String name, int limit) {
    }

    private record Counter(Instant windowStart, int count) {
    }
}
