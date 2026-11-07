package kr.pf.ballpark.auth;

import java.time.Clock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

// 만료된 갱신 토큰 정리. 갱신 때는 살아 있는 토큰만 지우므로(AuthService.refresh) 만료된 행은 여기서 지움
@Component
public class RefreshTokenCleanup {

    private static final Logger log = LoggerFactory.getLogger(RefreshTokenCleanup.class);

    private final RefreshTokenRepository refreshTokens;
    private final Clock clock;

    public RefreshTokenCleanup(RefreshTokenRepository refreshTokens, Clock clock) {
        this.refreshTokens = refreshTokens;
        this.clock = clock;
    }

    // 매일 새벽 4시 30분 (한국 시간)
    @Scheduled(cron = "0 30 4 * * *", zone = "Asia/Seoul")
    @Transactional
    public int run() {
        int n = refreshTokens.deleteExpired(clock.instant());
        log.info("만료된 갱신 토큰 {}개 삭제", n);
        return n;
    }
}
