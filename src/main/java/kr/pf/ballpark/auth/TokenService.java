package kr.pf.ballpark.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

// 우리 토큰
// - 접근 토큰: HS256 JWT, sub = 사용자 id, typ = access
// - 가입 토큰: 소셜 확인은 끝났는데 아직 가입 전일 때. sub = provider:subject, typ = signup
// - 갱신 토큰: 무작위 32바이트. DB 에는 SHA-256 해시만
@Component
public class TokenService {

    static final String TYP = "typ";
    static final String ACCESS = "access";
    static final String SIGNUP = "signup";

    private final AuthProperties props;
    private final Clock clock;
    private final JwtEncoder encoder;
    private final JwtDecoder decoder;
    private final JwtDecoder accessDecoder;
    private final SecureRandom random = new SecureRandom();

    public TokenService(AuthProperties props, Clock clock) {
        this.props = props;
        this.clock = clock;
        SecretKey key = secretKey(props.jwtSecret());
        this.encoder = NimbusJwtEncoder.withSecretKey(key).build();
        this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        NimbusJwtDecoder access = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
        access.setJwtValidator(new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(),
                jwt -> ACCESS.equals(jwt.getClaimAsString(TYP)) ? OAuth2TokenValidatorResult.success()
                        : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "not an access token", null))));
        this.accessDecoder = access;
    }

    static SecretKey secretKey(String secret) {
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("auth.jwt-secret(JWT_SECRET) 는 32바이트 이상이어야 해요");
        }
        return new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    // 접근 토큰 전용 (만료 + typ = access 확인). SecurityConfig 가 씀
    public JwtDecoder accessDecoder() {
        return accessDecoder;
    }

    public String access(long userId) {
        return sign(String.valueOf(userId), ACCESS, props.accessTtl().toSeconds());
    }

    public String signup(SocialProvider provider, String subject) {
        return sign(provider.code() + ":" + subject, SIGNUP, props.signupTtl().toSeconds());
    }

    public long accessTtlSeconds() {
        return props.accessTtl().toSeconds();
    }

    // 가입 토큰 확인 -> [provider, subject]
    public String[] readSignup(String token) {
        try {
            Jwt jwt = decoder.decode(token);
            String sub = jwt.getSubject();
            int i = sub == null ? -1 : sub.indexOf(':');
            if (!SIGNUP.equals(jwt.getClaimAsString(TYP)) || i < 1) {
                throw new AuthException(AuthException.Code.INVALID_SIGNUP_TOKEN);
            }
            return new String[] {sub.substring(0, i), sub.substring(i + 1)};
        } catch (JwtException e) {
            throw new AuthException(AuthException.Code.INVALID_SIGNUP_TOKEN);
        }
    }

    public String newRefreshToken() {
        byte[] b = new byte[32];
        random.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    public Instant refreshExpiresAt() {
        return clock.instant().plus(props.refreshTtl());
    }

    public static String hash(String refreshToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(refreshToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private String sign(String subject, String typ, long ttlSeconds) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .subject(subject)
                .issuedAt(now)
                .expiresAt(now.plusSeconds(ttlSeconds))
                .claim(TYP, typ)
                .build();
        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
        return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
    }
}
