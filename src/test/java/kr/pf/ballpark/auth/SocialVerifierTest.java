package kr.pf.ballpark.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

// 구글 · 애플 id_token 의 발급자 · 대상 확인 (서명 확인은 Nimbus 가 공개키로)
class SocialVerifierTest {

    private final OAuth2TokenValidator<Jwt> google = SocialVerifier.idTokenValidator(
            Set.of("https://accounts.google.com", "accounts.google.com"), List.of("ios-client", "android-client"));

    private static Jwt jwt(String iss, String aud, String sub) {
        Jwt.Builder b = Jwt.withTokenValue("t").header("alg", "RS256")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(300)).claim("iss", iss).audience(List.of(aud));
        if (sub != null) b.subject(sub);
        return b.build();
    }

    @Test
    void accepts() {
        assertThat(google.validate(jwt("https://accounts.google.com", "android-client", "1")).hasErrors()).isFalse();
        assertThat(google.validate(jwt("accounts.google.com", "ios-client", "1")).hasErrors()).isFalse();
    }

    // 다른 앱의 클라이언트 ID 로 받은 토큰 거절
    @Test
    void otherAudience() {
        assertThat(google.validate(jwt("https://accounts.google.com", "someone-else", "1")).hasErrors()).isTrue();
    }

    // 발급자가 다르면 거절, sub 가 없으면 거절
    @Test
    void wrongIssuerOrNoSubject() {
        assertThat(google.validate(jwt("https://appleid.apple.com", "ios-client", "1")).hasErrors()).isTrue();
        assertThat(google.validate(jwt("https://accounts.google.com", "ios-client", null)).hasErrors()).isTrue();
    }

    // 로컬 개발용 가짜 로그인: 토큰 문자열이 그대로 식별값
    @Test
    void fakeSocial() {
        AuthProperties p = new AuthProperties(TokenServiceTest.SECRET, null, null, null, null, List.of(), List.of(), true);
        assertThat(new SocialVerifier(p).verify(SocialProvider.GOOGLE, "abc")).isEqualTo("fake-abc");
    }
}
