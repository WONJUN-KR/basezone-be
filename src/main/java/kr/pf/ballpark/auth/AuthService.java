package kr.pf.ballpark.auth;

import java.time.Clock;
import java.time.Instant;
import java.util.regex.Pattern;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.pf.ballpark.user.User;
import kr.pf.ballpark.user.UserRepository;

// 로그인 흐름
// 1. login: 소셜 토큰 확인 -> 가입한 사람이면 우리 토큰, 처음이면 가입 토큰 (사용자 행은 아직 안 만듦)
// 2. signup: 가입 토큰 + 닉네임 + 약관 동의 -> 사용자 생성 -> 우리 토큰
// 3. refresh: 갱신 토큰을 새것으로 바꿔 줌 (쓴 갱신 토큰은 바로 지움)
// 4. logout: 갱신 토큰 삭제
@Service
public class AuthService {

    // 피그마 F08: 2~12자, 한글 · 영문 · 숫자 · 밑줄
    static final Pattern NICKNAME = Pattern.compile("^[가-힣A-Za-z0-9_]{2,12}$");

    private final SocialVerifier verifier;
    private final TokenService tokens;
    private final UserRepository users;
    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;

    public AuthService(SocialVerifier verifier, TokenService tokens, UserRepository users, RefreshTokenRepository refreshTokens, Clock clock) {
        this.verifier = verifier;
        this.tokens = tokens;
        this.users = users;
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    public record Tokens(String accessToken, long expiresIn, String refreshToken) {
    }

    // tokens 가 null 이면 가입 필요 (signupToken 사용)
    public record LoginResult(Tokens tokens, String signupToken) {
    }

    // 트랜잭션 없음: 소셜 확인(외부 호출)이 느려도 DB 커넥션을 붙잡지 않게. 갱신 토큰 저장은 save 하나라 따로 묶을 필요 없음
    public LoginResult login(SocialProvider provider, String socialToken) {
        String subject = verifier.verify(provider, socialToken);
        return users.findByAuthProviderAndAuthSubject(provider.code(), subject)
                .map(u -> new LoginResult(issue(u.getId()), null))
                .orElseGet(() -> new LoginResult(null, tokens.signup(provider, subject)));
    }

    @Transactional
    public Tokens signup(String signupToken, String nickname, boolean termsAgreed, boolean locationTermsAgreed, boolean marketingOptIn) {
        String[] who = tokens.readSignup(signupToken);
        if (!termsAgreed) {
            throw new AuthException(AuthException.Code.TERMS_REQUIRED);
        }
        if (nickname == null || !NICKNAME.matcher(nickname).matches()) {
            throw new AuthException(AuthException.Code.INVALID_NICKNAME);
        }
        if (users.findByAuthProviderAndAuthSubject(who[0], who[1]).isPresent()) {
            throw new AuthException(AuthException.Code.ALREADY_SIGNED_UP);
        }
        if (users.existsByNickname(nickname)) {
            throw new AuthException(AuthException.Code.NICKNAME_TAKEN);
        }
        User u;
        try {
            u = users.saveAndFlush(new User(who[0], who[1], nickname, clock.instant(), locationTermsAgreed, marketingOptIn));
        } catch (DataIntegrityViolationException e) {
            // 동시에 같은 닉네임으로 가입한 경우
            throw new AuthException(AuthException.Code.NICKNAME_TAKEN);
        }
        return issue(u.getId());
    }

    @Transactional
    public Tokens refresh(String refreshToken) {
        String hash = TokenService.hash(refreshToken == null ? "" : refreshToken);
        RefreshToken saved = refreshTokens.findByTokenHash(hash)
                .orElseThrow(() -> new AuthException(AuthException.Code.INVALID_REFRESH_TOKEN));
        // 조회와 삭제 사이에 같은 토큰으로 동시에 들어오면 둘 다 통과할 수 있어서, 만료 조건까지 넣은 삭제 한 번으로 한 요청만 성공시킴
        // 만료된 행은 여기서 안 지워짐 -> 보관 기간 배치가 정리
        if (refreshTokens.deleteActive(hash, clock.instant()) != 1) {
            throw new AuthException(AuthException.Code.INVALID_REFRESH_TOKEN);
        }
        return issue(saved.getUserId());
    }

    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken != null) {
            refreshTokens.deleteByTokenHash(TokenService.hash(refreshToken));
        }
    }

    private Tokens issue(long userId) {
        String refresh = tokens.newRefreshToken();
        Instant now = clock.instant();
        refreshTokens.save(new RefreshToken(userId, TokenService.hash(refresh), tokens.refreshExpiresAt(), now));
        return new Tokens(tokens.access(userId), tokens.accessTtlSeconds(), refresh);
    }
}
