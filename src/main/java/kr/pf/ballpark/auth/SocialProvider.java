package kr.pf.ballpark.auth;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

// users.auth_provider 값과 같음
public enum SocialProvider {
    KAKAO, APPLE, GOOGLE;

    @JsonValue
    public String code() {
        return name().toLowerCase();
    }

    @JsonCreator
    public static SocialProvider from(String code) {
        for (SocialProvider p : values()) {
            if (p.code().equals(code)) return p;
        }
        // 여기서 예외를 던지면 Jackson 이 감싸서 AuthException 처리기에 안 닿을 수 있음 -> null 로 넘기고 AuthController.login 이 UNKNOWN_PROVIDER 처리
        return null;
    }
}
