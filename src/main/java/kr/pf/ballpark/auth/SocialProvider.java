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
        throw new AuthException(AuthException.Code.UNKNOWN_PROVIDER);
    }
}
