package com.helpdesk.security;

import com.helpdesk.role.entity.RoleName;
import com.helpdesk.user.entity.UserStatus;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import org.junit.jupiter.api.Test;

import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Duration;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pure unit test — real {@link JwtServiceImpl}, real cryptography, no mocks
 * and no Spring context, matching {@code RoleMapperImplTest}'s "exercise the
 * real implementation directly" convention. Deterministic throughout: the
 * expiry test uses a negative TTL rather than {@code Thread.sleep}.
 */
class JwtServiceImplTest {

    private static final String TEST_SECRET = "0123456789".repeat(7);
    private static final String OTHER_SECRET = "9876543210".repeat(7);
    private static final String ISSUER = "helpdesk-test";

    private final JwtServiceImpl jwtService = new JwtServiceImpl(hs512Properties(ISSUER, TEST_SECRET, Duration.ofMinutes(15)));

    @Test
    void generateAndParse_shouldRoundTripClaims_whenHs512() {
        UserPrincipal principal = aPrincipal(42L, RoleName.ADMIN, 3);

        String token = jwtService.generateAccessToken(principal);
        Claims claims = jwtService.parseClaims(token);

        assertEquals("42", claims.getSubject());
        assertEquals("ADMIN", claims.get(SecurityConstants.ROLE_CLAIM, String.class));
        assertEquals(3, claims.get(SecurityConstants.TOKEN_VERSION_CLAIM, Integer.class));
        assertEquals(ISSUER, claims.getIssuer());
        assertNotNull(claims.getExpiration());
    }

    @Test
    void parseClaims_shouldThrow_whenSignatureDoesNotMatch() {
        JwtServiceImpl otherService = new JwtServiceImpl(hs512Properties(ISSUER, OTHER_SECRET, Duration.ofMinutes(15)));
        String token = otherService.generateAccessToken(aPrincipal(1L, RoleName.USER, 0));

        assertThrows(JwtException.class, () -> jwtService.parseClaims(token));
    }

    @Test
    void parseClaims_shouldThrowExpired_whenTokenAlreadyExpiredAtIssuance() {
        JwtServiceImpl shortLivedService = new JwtServiceImpl(hs512Properties(ISSUER, TEST_SECRET, Duration.ofSeconds(-10)));
        String token = shortLivedService.generateAccessToken(aPrincipal(1L, RoleName.USER, 0));

        assertThrows(ExpiredJwtException.class, () -> shortLivedService.parseClaims(token));
    }

    @Test
    void isTokenVersionCurrent_shouldReturnTrue_whenVersionsMatch() {
        Claims claims = jwtService.parseClaims(jwtService.generateAccessToken(aPrincipal(1L, RoleName.USER, 5)));

        assertTrue(jwtService.isTokenVersionCurrent(claims, 5));
    }

    @Test
    void isTokenVersionCurrent_shouldReturnFalse_whenVersionsDiffer() {
        Claims claims = jwtService.parseClaims(jwtService.generateAccessToken(aPrincipal(1L, RoleName.USER, 5)));

        assertFalse(jwtService.isTokenVersionCurrent(claims, 6));
    }

    @Test
    void constructor_shouldThrow_whenHmacSecretTooShort() {
        JwtProperties properties = hs512Properties(ISSUER, "too-short-secret", Duration.ofMinutes(15));

        assertThrows(IllegalStateException.class, () -> new JwtServiceImpl(properties));
    }

    @Test
    void constructor_shouldThrow_whenHmacSecretBlank() {
        JwtProperties properties = hs512Properties(ISSUER, "", Duration.ofMinutes(15));

        assertThrows(IllegalStateException.class, () -> new JwtServiceImpl(properties));
    }

    @Test
    void constructor_shouldThrow_whenRs256KeyMaterialMissing() {
        JwtProperties properties = new JwtProperties("RS256", null, null, null, Duration.ofMinutes(15), Duration.ofDays(7), ISSUER);

        assertThrows(IllegalStateException.class, () -> new JwtServiceImpl(properties));
    }

    @Test
    void generateAndParse_shouldRoundTrip_whenRs256WithRealKeypair() throws Exception {
        KeyPairGenerator keyPairGenerator = KeyPairGenerator.getInstance("RSA");
        keyPairGenerator.initialize(2048);
        KeyPair keyPair = keyPairGenerator.generateKeyPair();
        String privateKey = Base64.getEncoder().encodeToString(keyPair.getPrivate().getEncoded());
        String publicKey = Base64.getEncoder().encodeToString(keyPair.getPublic().getEncoded());
        JwtServiceImpl rs256Service = new JwtServiceImpl(
                new JwtProperties("RS256", null, privateKey, publicKey, Duration.ofMinutes(15), Duration.ofDays(7), ISSUER));

        String token = rs256Service.generateAccessToken(aPrincipal(7L, RoleName.SUPPORT_ENGINEER, 1));
        Claims claims = rs256Service.parseClaims(token);

        assertEquals("7", claims.getSubject());
        assertEquals("SUPPORT_ENGINEER", claims.get(SecurityConstants.ROLE_CLAIM, String.class));
    }

    @Test
    void parseClaims_shouldThrow_whenIssuerDoesNotMatch() {
        JwtServiceImpl otherIssuerService = new JwtServiceImpl(hs512Properties("a-different-issuer", TEST_SECRET, Duration.ofMinutes(15)));
        String token = otherIssuerService.generateAccessToken(aPrincipal(1L, RoleName.USER, 0));

        assertThrows(JwtException.class, () -> jwtService.parseClaims(token));
    }

    private JwtProperties hs512Properties(String issuer, String secret, Duration accessTokenTtl) {
        return new JwtProperties("HS512", secret, null, null, accessTokenTtl, Duration.ofDays(7), issuer);
    }

    private UserPrincipal aPrincipal(Long userId, RoleName role, int tokenVersion) {
        return new UserPrincipal(userId, "user%d@example.com".formatted(userId), "hashed-password", role, UserStatus.VERIFIED, tokenVersion);
    }
}
