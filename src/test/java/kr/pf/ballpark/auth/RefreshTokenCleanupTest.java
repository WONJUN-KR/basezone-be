package kr.pf.ballpark.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

class RefreshTokenCleanupTest {

    // 지금 시각 기준으로 만료된 것만 지우라고 넘김
    @Test
    void deletesExpiredAtNow() {
        Instant now = Instant.parse("2026-11-07T19:30:00Z");
        RefreshTokenRepository repo = mock(RefreshTokenRepository.class);
        when(repo.deleteExpired(now)).thenReturn(3);
        assertThat(new RefreshTokenCleanup(repo, Clock.fixed(now, ZoneOffset.UTC)).run()).isEqualTo(3);
        verify(repo).deleteExpired(now);
    }
}
