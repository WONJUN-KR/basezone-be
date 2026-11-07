package kr.pf.ballpark.auth;

import java.time.Duration;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;

// 값은 환경 변수로 (.env, 커밋 안 함). 클라이언트 ID 가 비어 있으면 그 로그인은 꺼짐
@ConfigurationProperties("auth")
public record AuthProperties(
        String jwtSecret,
        Duration accessTtl,
        Duration refreshTtl,
        Duration signupTtl,
        Long kakaoAppId,
        List<String> googleClientIds,
        List<String> appleClientIds,
        // 로컬 개발 전용: 소셜 토큰을 확인하지 않고 토큰 문자열을 그대로 사용자 식별값으로 씀. 운영에서 켜면 안 됨
        boolean fakeSocial) {

    public AuthProperties {
        googleClientIds = googleClientIds == null ? List.of() : googleClientIds.stream().filter(s -> !s.isBlank()).toList();
        appleClientIds = appleClientIds == null ? List.of() : appleClientIds.stream().filter(s -> !s.isBlank()).toList();
    }
}
