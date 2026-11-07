package kr.pf.ballpark.auth;

import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.nimbusds.jose.RemoteKeySourceException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.http.HttpHeaders;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwtValidationException;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

// 앱이 보낸 소셜 토큰을 각 회사에 확인하고 사용자 식별값(subject)을 돌려줌
@Component
public class SocialVerifier {

    private static final String KAKAO_TOKEN_INFO = "https://kapi.kakao.com/v1/user/access_token_info";
    private static final String GOOGLE_JWKS = "https://www.googleapis.com/oauth2/v3/certs";
    private static final Set<String> GOOGLE_ISSUERS = Set.of("https://accounts.google.com", "accounts.google.com");
    private static final String APPLE_JWKS = "https://appleid.apple.com/auth/keys";
    private static final Set<String> APPLE_ISSUERS = Set.of("https://appleid.apple.com");

    private static final Logger log = LoggerFactory.getLogger(SocialVerifier.class);

    private final AuthProperties props;
    private final RestClient kakao;
    private volatile JwtDecoder google;
    private volatile JwtDecoder apple;

    public SocialVerifier(AuthProperties props, Environment env) {
        this.props = props;
        // 가짜 로그인은 로컬 전용. prod 프로필에서 켜져 있으면 아예 안 뜸
        if (props.fakeSocial()) {
            if (env.acceptsProfiles(Profiles.of("prod"))) {
                throw new IllegalStateException("prod 프로필에서는 AUTH_FAKE_SOCIAL 을 켤 수 없어요");
            }
            log.warn("AUTH_FAKE_SOCIAL=true: 소셜 토큰을 확인하지 않아요. 로컬 개발에서만 쓰세요");
        }
        // 카카오가 느려도 로그인 요청이 오래 묶이지 않게
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(5));
        this.kakao = RestClient.builder().requestFactory(factory).build();
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
        } catch (RestClientResponseException e) {
            // 4xx = 토큰이 틀림, 5xx = 카카오 장애
            throw new AuthException(e.getStatusCode().is4xxClientError()
                    ? AuthException.Code.INVALID_SOCIAL_TOKEN : AuthException.Code.SOCIAL_UNAVAILABLE);
        } catch (RestClientException e) {
            // 타임아웃 · 연결 실패
            throw new AuthException(AuthException.Code.SOCIAL_UNAVAILABLE);
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
        } catch (JwtValidationException e) {
            // 서명은 맞는데 발급자 · 대상 · 만료가 틀림
            throw new AuthException(AuthException.Code.INVALID_SOCIAL_TOKEN);
        } catch (JwtException e) {
            // 공개키를 못 받아옴(구글 · 애플 장애 · 네트워크) vs 토큰 모양 · 서명이 틀림
            throw new AuthException(isKeyFetchFailure(e) ? AuthException.Code.SOCIAL_UNAVAILABLE : AuthException.Code.INVALID_SOCIAL_TOKEN);
        }
    }

    // Nimbus 가 공개키 요청에 실패하면 원인에 RemoteKeySourceException(IOException 계열)이 붙음
    static boolean isKeyFetchFailure(Throwable e) {
        for (Throwable t = e; t != null; t = t.getCause()) {
            if (t instanceof RemoteKeySourceException || t instanceof IOException) return true;
        }
        return false;
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
