package ru.ticketswap.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import ru.ticketswap.auth.JwtService;
import ru.ticketswap.user.User;
import ru.ticketswap.user.UserIdentityService;

import java.io.IOException;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtService jwtService;
    private final UserIdentityService userIdentityService;

    public JwtAuthenticationFilter(JwtService jwtService, UserIdentityService userIdentityService) {
        this.jwtService = jwtService;
        this.userIdentityService = userIdentityService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        final String authHeader = request.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        String jwt = authHeader.substring(7);
        String userEmail;
        int tokenVersion;
        try {
            userEmail = jwtService.extractEmail(jwt);
            tokenVersion = jwtService.extractTokenVersion(jwt);
        } catch (Exception e) {
            filterChain.doFilter(request, response);
            return;
        }

        if (userEmail != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                User user = userIdentityService.findUserByEmail(userEmail)
                        .orElseThrow(() -> new UsernameNotFoundException("User not found"));
                if (user.getTokenVersion() != tokenVersion) {
                    filterChain.doFilter(request, response);
                    return;
                }

                UserDetails userDetails = userIdentityService.loadUserDetailsByEmail(userEmail);
                UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                        userDetails,
                        null,
                        userDetails.getAuthorities()
                );
                authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

                SecurityContextHolder.getContext().setAuthentication(authToken);
            } catch (AuthenticationException ex) {
                SecurityContextHolder.clearContext();
            }
        }
        filterChain.doFilter(request, response);
    }
}
