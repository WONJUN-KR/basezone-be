package kr.pf.ballpark.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.JwtException;

class TokenServiceTest {

    static final String SECRET = "test-secret-test-secret-test-secret-123";

    static AuthProperties props() {
        return new AuthProperties(SECRET, Duration.ofHours(1), Duration.ofDays(30), Duration.ofMinutes(10), null, List.of(), List.of(), false);
    }

    private final TokenService tokens = new TokenService(props(), Clock.systemUTC());

    // 접근 토큰: sub = 사용자 id
    @Test
    void accessToken() {
        assertThat(tokens.accessDecoder().decode(tokens.access(42)).getSubject()).isEqualTo("42");
    }

    // 가입 토큰은 접근 토큰으로 못 씀
    @Test
    void signupTokenIsNotAccess() {
        String signup = tokens.signup(SocialProvider.KAKAO, "123");
        assertThat(tokens.readSignup(signup)).containsExactly("kakao", "123");
        assertThatThrownBy(() -> tokens.accessDecoder().decode(signup)).isInstanceOf(JwtException.class);
    }

    // 접근 토큰을 가입 토큰으로 못 씀
    @Test
    void accessIsNotSignup() {
        assertThatThrownBy(() -> tokens.readSignup(tokens.access(1))).isInstanceOf(AuthException.class);
    }

    // 만료된 토큰 거절 (1시간 전에 발급한 1시간짜리)
    @Test
    void expired() {
        TokenService past = new TokenService(props(), Clock.fixed(Instant.now().minus(Duration.ofHours(2)), ZoneOffset.UTC));
        assertThatThrownBy(() -> tokens.accessDecoder().decode(past.access(1))).isInstanceOf(JwtException.class);
    }

    // 다른 비밀키로 만든 토큰 거절
    @Test
    void otherSecret() {
        AuthProperties other = new AuthProperties("other-secret-other-secret-other-secret!", Duration.ofHours(1), Duration.ofDays(30),
                Duration.ofMinutes(10), null, List.of(), List.of(), false);
        String forged = new TokenService(other, Clock.systemUTC()).access(1);
        assertThatThrownBy(() -> tokens.accessDecoder().decode(forged)).isInstanceOf(JwtException.class);
    }

    // 비밀키가 짧으면 시작 안 함
    @Test
    void shortSecret() {
        assertThatThrownBy(() -> TokenService.secretKey("short")).isInstanceOf(IllegalStateException.class);
    }

    // 갱신 토큰은 매번 다르고, 해시는 64자
    @Test
    void refreshToken() {
        String a = tokens.newRefreshToken();
        assertThat(a).isNotEqualTo(tokens.newRefreshToken());
        assertThat(TokenService.hash(a)).hasSize(64).isEqualTo(TokenService.hash(a));
    }
}
