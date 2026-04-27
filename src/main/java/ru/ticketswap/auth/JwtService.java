package ru.ticketswap.auth;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;
import ru.ticketswap.config.TicketSwapProperties;
import ru.ticketswap.user.User;

import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;

@Service
public class JwtService {

    private static final String TOKEN_VERSION_CLAIM = "tokenVersion";

    private final Key key;
    private final long expirationMs;

    public JwtService(TicketSwapProperties properties) {
        String secret = properties.getSecurity().getJwt().getSecret();
        this.expirationMs = properties.getSecurity().getJwt().getExpirationMs();
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generate(User user) {
        return Jwts.builder()
                .setSubject(user.getEmail())
                .claim(TOKEN_VERSION_CLAIM, user.getTokenVersion())
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + expirationMs))
                .signWith(key)
                .compact();
    }

    public String extractEmail(String token) {
        return parseClaims(token).getSubject();
    }

    public int extractTokenVersion(String token) {
        Object value = parseClaims(token).get(TOKEN_VERSION_CLAIM);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return 0;
    }

    private Claims parseClaims(String token) {
        return Jwts.parserBuilder()
                .setSigningKey(key)
                .build()
                .parseClaimsJws(token)
                .getBody();
    }
}
