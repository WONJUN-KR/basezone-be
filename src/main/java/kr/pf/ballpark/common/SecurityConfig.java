package kr.pf.ballpark.common;

import java.time.Clock;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;

import kr.pf.ballpark.auth.AuthProperties;
import kr.pf.ballpark.auth.TokenService;

// 인증: Authorization: Bearer <접근 토큰>. 로그인 · 핑 · 헬스체크만 토큰 없이
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    SecurityFilterChain api(HttpSecurity http) throws Exception {
        http.csrf(c -> c.disable())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(a -> a
                        .requestMatchers(HttpMethod.POST, "/api/auth/**").permitAll()
                        // /error: 요청 처리 중 오류가 나면 여기로 넘어오는데, 막으면 원래 오류 대신 401 이 나감
                        .requestMatchers("/api/ping", "/actuator/health", "/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(o -> o.jwt(j -> {}));
        return http.build();
    }

    // 접근 토큰만 통과 (가입 토큰으로 API 를 부르면 401)
    @Bean
    JwtDecoder jwtDecoder(TokenService tokens) {
        return tokens.accessDecoder();
    }
}
