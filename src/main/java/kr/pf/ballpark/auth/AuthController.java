package kr.pf.ballpark.auth;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import kr.pf.ballpark.auth.AuthService.LoginResult;
import kr.pf.ballpark.auth.AuthService.Tokens;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService auth;

    public AuthController(AuthService auth) {
        this.auth = auth;
    }

    // token: 카카오 = 액세스 토큰, 구글 · 애플 = id_token
    public record LoginRequest(SocialProvider provider, String token) {
    }

    // 동의 항목은 안 보내면 동의 안 한 것으로 봄 (Jackson 3 은 빠진 boolean 을 거부해서 Boolean 으로 받음)
    public record SignupRequest(String signupToken, String nickname, Boolean termsAgreed, Boolean locationTermsAgreed, Boolean marketingOptIn) {
    }

    public record RefreshRequest(String refreshToken) {
    }

    // status: ok (토큰 발급) / signup_required (가입 화면으로)
    public record LoginResponse(String status, Tokens tokens, String signupToken) {
    }

    @PostMapping("/login")
    public LoginResponse login(@RequestBody LoginRequest req) {
        if (req.provider() == null) {
            throw new AuthException(AuthException.Code.UNKNOWN_PROVIDER);
        }
        LoginResult r = auth.login(req.provider(), req.token());
        return r.tokens() != null ? new LoginResponse("ok", r.tokens(), null) : new LoginResponse("signup_required", null, r.signupToken());
    }

    @PostMapping("/signup")
    public Tokens signup(@RequestBody SignupRequest req) {
        return auth.signup(req.signupToken(), req.nickname(), Boolean.TRUE.equals(req.termsAgreed()),
                Boolean.TRUE.equals(req.locationTermsAgreed()), Boolean.TRUE.equals(req.marketingOptIn()));
    }

    @PostMapping("/refresh")
    public Tokens refresh(@RequestBody RefreshRequest req) {
        return auth.refresh(req.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody RefreshRequest req) {
        auth.logout(req.refreshToken());
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<Map<String, String>> handle(AuthException e) {
        return ResponseEntity.status(e.code().status).body(Map.of("error", e.code().name().toLowerCase()));
    }
}
