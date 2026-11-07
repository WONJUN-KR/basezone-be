package kr.pf.ballpark.auth;

import org.springframework.http.HttpStatus;

public class AuthException extends RuntimeException {

    public enum Code {
        UNKNOWN_PROVIDER(HttpStatus.BAD_REQUEST),
        INVALID_SOCIAL_TOKEN(HttpStatus.UNAUTHORIZED),
        INVALID_SIGNUP_TOKEN(HttpStatus.UNAUTHORIZED),
        INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED),
        TERMS_REQUIRED(HttpStatus.BAD_REQUEST),
        INVALID_NICKNAME(HttpStatus.BAD_REQUEST),
        NICKNAME_TAKEN(HttpStatus.CONFLICT),
        ALREADY_SIGNED_UP(HttpStatus.CONFLICT),
        PROVIDER_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE),
        // 소셜 회사 쪽 장애 · 네트워크 오류 (토큰이 틀린 것과 구분)
        SOCIAL_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE);

        final HttpStatus status;

        Code(HttpStatus status) {
            this.status = status;
        }
    }

    private final Code code;

    public AuthException(Code code) {
        super(code.name());
        this.code = code;
    }

    public Code code() {
        return code;
    }
}
