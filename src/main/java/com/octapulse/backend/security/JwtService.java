package com.octapulse.backend.security;

import com.octapulse.backend.config.JwtProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;

@Service
public class JwtService {

    private final JwtProperties props;
    private final SecretKey key;

    public JwtService(JwtProperties props) {
        this.props = props;
        this.key = Keys.hmacShaKeyFor(props.secret().getBytes());
    }

    public String createAccessToken(String userId) {
        Instant expiry = Instant.now().plus(props.accessTokenMinutes(), ChronoUnit.MINUTES);
        return Jwts.builder()
                .subject(userId)
                .claim("type", "access")
                .expiration(Date.from(expiry))
                .signWith(key)
                .compact();
    }

    public String decodeSubject(String token) {
        var claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(token).getPayload();
        if (!"access".equals(claims.get("type"))) {
            throw new io.jsonwebtoken.JwtException("not an access token");
        }
        return claims.getSubject();
    }
}
