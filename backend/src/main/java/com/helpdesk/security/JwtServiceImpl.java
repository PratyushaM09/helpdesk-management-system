package com.helpdesk.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.JwtParser;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.PublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.time.Instant;
import java.util.Base64;
import java.util.Date;

/**
 * Resolves the signing/verification key material once, at construction —
 * a misconfigured {@code app.jwt.*} block fails application startup
 * immediately (matching this project's existing "fail loudly, never
 * silently fall back" convention for missing secrets, e.g. {@code DB_PASSWORD})
 * rather than surfacing as a confusing failure on the first login attempt.
 * <p>
 * HS512 uses one {@link SecretKey} for both signing and verification
 * (symmetric); RS256 uses a {@link PrivateKey} to sign and the paired
 * {@link PublicKey} to verify (asymmetric, ADR-0003's reasoning for
 * choosing it in {@code prod}). The {@link JwtParser} built here is
 * immutable/thread-safe per the JJWT library's own contract, so it is built
 * once and reused for every {@link #parseClaims} call, not rebuilt per request.
 */
@Service
public class JwtServiceImpl implements JwtService {

    private final JwtProperties jwtProperties;
    private final boolean rs256;
    private final SecretKey hmacKey;
    private final PrivateKey rsaPrivateKey;
    private final JwtParser jwtParser;

    public JwtServiceImpl(JwtProperties jwtProperties) {
        this.jwtProperties = jwtProperties;
        this.rs256 = "RS256".equals(jwtProperties.algorithm());

        if (rs256) {
            requireConfigured(jwtProperties.privateKey(), "app.jwt.private-key");
            requireConfigured(jwtProperties.publicKey(), "app.jwt.public-key");
            this.rsaPrivateKey = parsePrivateKey(jwtProperties.privateKey());
            PublicKey rsaPublicKey = parsePublicKey(jwtProperties.publicKey());
            this.hmacKey = null;
            this.jwtParser = Jwts.parser()
                    .verifyWith(rsaPublicKey)
                    .requireIssuer(jwtProperties.issuer())
                    .build();
        } else {
            requireConfigured(jwtProperties.secret(), "app.jwt.secret");
            this.hmacKey = parseHmacKey(jwtProperties.secret());
            this.rsaPrivateKey = null;
            this.jwtParser = Jwts.parser()
                    .verifyWith(hmacKey)
                    .requireIssuer(jwtProperties.issuer())
                    .build();
        }
    }

    @Override
    public String generateAccessToken(UserPrincipal principal) {
        Instant now = Instant.now();
        Instant expiry = now.plus(jwtProperties.accessTokenTtl());
        JwtBuilder builder = Jwts.builder()
                .subject(String.valueOf(principal.userId()))
                .claim(SecurityConstants.ROLE_CLAIM, principal.role().name())
                .claim(SecurityConstants.TOKEN_VERSION_CLAIM, principal.tokenVersion())
                .issuer(jwtProperties.issuer())
                .issuedAt(Date.from(now))
                .expiration(Date.from(expiry));

        if (rs256) {
            builder.signWith(rsaPrivateKey, Jwts.SIG.RS256);
        } else {
            builder.signWith(hmacKey, Jwts.SIG.HS512);
        }
        return builder.compact();
    }

    @Override
    public Claims parseClaims(String token) {
        return jwtParser.parseSignedClaims(token).getPayload();
    }

    @Override
    public boolean isTokenVersionCurrent(Claims claims, int currentTokenVersion) {
        Integer claimedVersion = claims.get(SecurityConstants.TOKEN_VERSION_CLAIM, Integer.class);
        return claimedVersion != null && claimedVersion == currentTokenVersion;
    }

    private static void requireConfigured(String value, String propertyName) {
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("%s is required for the configured app.jwt.algorithm".formatted(propertyName));
        }
    }

    /** HS512 requires a key of at least 64 bytes (512 bits) — enforced here, not left to fail obscurely inside the signing call. */
    private static SecretKey parseHmacKey(String secret) {
        byte[] bytes = secret.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 64) {
            throw new IllegalStateException(
                    "app.jwt.secret must be at least 64 bytes for HS512 (got %d)".formatted(bytes.length));
        }
        return Keys.hmacShaKeyFor(bytes);
    }

    private static PrivateKey parsePrivateKey(String raw) {
        try {
            return KeyFactory.getInstance("RSA")
                    .generatePrivate(new PKCS8EncodedKeySpec(decodeKeyMaterial(raw)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("app.jwt.private-key is not a valid RSA PKCS8 key", e);
        }
    }

    private static PublicKey parsePublicKey(String raw) {
        try {
            return KeyFactory.getInstance("RSA")
                    .generatePublic(new X509EncodedKeySpec(decodeKeyMaterial(raw)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("app.jwt.public-key is not a valid RSA X509 key", e);
        }
    }

    /** Accepts either a full PEM block or a bare Base64 DER blob — strips any PEM markers/whitespace before decoding either way. */
    private static byte[] decodeKeyMaterial(String raw) {
        String cleaned = raw
                .replaceAll("-----BEGIN [A-Z ]+-----", "")
                .replaceAll("-----END [A-Z ]+-----", "")
                .replaceAll("\\s", "");
        return Base64.getDecoder().decode(cleaned);
    }
}
