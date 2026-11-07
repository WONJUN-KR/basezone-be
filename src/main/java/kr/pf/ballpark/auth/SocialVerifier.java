package kr.pf.ballpark.auth;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

// 앱이 보낸 소셜 토큰을 각 회사에 확인하고 사용자 식별값(subject)을 돌려줌
@Component
public class SocialVerifier {

    private static final String KAKAO_TOKEN_INFO = "https://kapi.kakao.com/v1/user/access_token_info";
    private static final String GOOGLE_JWKS = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> GOOGLE_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");
    private static final String APPLE_JWKS = "https://appleid.apple.com/auth/keys";
    private static final Set<String> APPLE_ISSUERS = Set.of("https://appleid.apple.com");

    private final AuthProperties props;
    private final RestClient kakao;
    private volatile JwtDecoder google;
    private volatile JwtDecoder apple;

    public SocialVerifier(AuthProperties props) {
        this.props = props;
        this.kakao = RestClient.create();
    }

    public String verify(SocialProvider provider, String token) {
        if (token == null || token.isBlank()) {
            throw new AuthException(AuthException.Code.INVALID_SOCIAL_TOKEN);
        }
        if (props.fakeSocial()) {
            return "fake-" + token;
        }
        return switch (provider) {
            case KAKAO -> kakao(token);
            case GOOGLE -> idToken(googleDecoder(), token);
            case APPLE -> idToken(appleDecoder(), token);
        };
    }

    // 카카오: 액세스 토큰 정보 API 로 확인. 다른 앱에서 받은 토큰이면 거절 (app_id 비교)
    private String kakao(String accessToken) {
        if (props.kakaoAppId() == null) {
            throw new AuthException(AuthException.Code.PROVIDER_NOT_CONFIGURED);
        }
        Map<?, ?> info;
        try {
            info = kakao.get().uri(KAKAO_TOKEN_INFO)
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
                    .retrieve().body(Map.class);
        } catch (RestClientException e) {
            throw new AuthException(AuthException.Code.INVALID_SOCIAL_TOKEN);
        }
        if (info == null || info.get("id") == null || !String.valueOf(props.kakaoAppId()).equals(String.valueOf(info.get("app_id")))) {
            throw new AuthException(AuthException.Code.INVALID_SOCIAL_TOKEN);
        }
        return String.valueOf(info.get("id"));
    }

    // 구글 · 애플: id_token 서명(공개키) · 발급자 · 대상(우리 클라이언트 ID) · 만료 확인
    private String idToken(JwtDecoder decoder, String idToken) {
        try {
            return decoder.decode(idToken).getSubject();
        } catch (JwtException e) {
            throw new AuthException(AuthException.Code.INVALID_SOCIAL_TOKEN);
        }
    }

    private JwtDecoder googleDecoder() {
        if (google == null) google = decoder(GOOGLE_JWKS, GOOGLE_ISSUERS, props.googleClientIds());
        return google;
    }

    private JwtDecoder appleDecoder() {
        if (apple == null) apple = decoder(APPLE_JWKS, APPLE_ISSUERS, props.appleClientIds());
        return apple;
    }

    private static JwtDecoder decoder(String jwksUri, Set<String> issuers, List<String> audiences) {
        if (audiences.isEmpty()) {
            throw new AuthException(AuthException.Code.PROVIDER_NOT_CONFIGURED);
        }
        NimbusJwtDecoder d = NimbusJwtDecoder.withJwkSetUri(jwksUri).build();
        d.setJwtValidator(idTokenValidator(issuers, audiences));
        return d;
    }

    static OAuth2TokenValidator<Jwt> idTokenValidator(Set<String> issuers, List<String> audiences) {
        OAuth2TokenValidator<Jwt> claims = jwt -> {
            // 구글은 'accounts.google.com' 처럼 URL 이 아닌 발급자도 써서 getIssuer()(URL 변환) 대신 문자열로
            String iss = jwt.getClaimAsString("iss");
            boolean ok = iss != null && issuers.contains(iss)
                    && jwt.getAudience() != null && jwt.getAudience().stream().anyMatch(audiences::contains)
                    && jwt.getSubject() != null;
            return ok ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "issuer or audience mismatch", null));
        };
        return new DelegatingOAuth2TokenValidator<>(new JwtTimestampValidator(Duration.ofSeconds(60)), claims);
    }
}
