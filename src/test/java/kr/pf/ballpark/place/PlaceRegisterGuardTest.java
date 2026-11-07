package kr.pf.ballpark.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import kr.pf.ballpark.place.PlaceRegisterGuard.Decision;
import kr.pf.ballpark.place.PlaceRegisterGuard.Result;
import kr.pf.ballpark.place.PlaceRegisterGuard.Spot;

class PlaceRegisterGuardTest {

    private static final Instant NOW = Instant.parse("2026-11-07T06:00:00Z");
    private static final Instant OLD_USER = NOW.minus(Duration.ofDays(30));
    // 위도 1도 = 약 111,195m
    private static final double M = 1 / 111_195.0;
    private static final Spot NEW = new Spot(null, 37.5, 127.0, null);

    private static Spot north(long id, double meters) {
        return new Spot(id, 37.5 + meters * M, 127.0, null);
    }

    private static List<Instant> ago(Duration... ds) {
        return Arrays.stream(ds).map(NOW::minus).toList();
    }

    private static Decision decide(List<Spot> nearby, Instant userCreatedAt, List<Instant> mine) {
        return PlaceRegisterGuard.check(NEW, nearby, userCreatedAt, mine, NOW).decision();
    }

    // 49m 면 중복, 51m 면 등록 가능
    @Test
    void nearbyBoundary() {
        assertThat(decide(List.of(north(1, 49)), OLD_USER, List.of())).isEqualTo(Decision.DUPLICATE);
        assertThat(decide(List.of(north(1, 51)), OLD_USER, List.of())).isEqualTo(Decision.ALLOWED);
    }

    // 중복 후보는 가까운 순
    @Test
    void duplicatesSorted() {
        Result r = PlaceRegisterGuard.check(NEW, List.of(north(1, 40), north(2, 10), north(3, 300)), OLD_USER, List.of(), NOW);
        assertThat(r.duplicates()).containsExactly(2L, 1L);
    }

    // 멀어도 지번이 같으면 중복, 본번이 없으면 지번 비교 안 함
    @Test
    void sameLot() {
        String lot = PlaceRegisterGuard.lotKey("서울", "송파구", "잠실동", "1", "");
        Spot me = new Spot(null, 37.5, 127.0, lot);
        Result r = PlaceRegisterGuard.check(me, List.of(new Spot(1L, 37.5 + 200 * M, 127.0, lot)), OLD_USER, List.of(), NOW);
        assertThat(r.decision()).isEqualTo(Decision.DUPLICATE);
        assertThat(PlaceRegisterGuard.lotKey("서울", "송파구", "잠실동", null, null)).isNull();
    }

    // 24시간에 3곳: 3곳째까지 되고 4곳째부터 막힘, 24시간 지난 건 안 셈
    @Test
    void dailyLimit() {
        assertThat(decide(List.of(), OLD_USER, ago(Duration.ofHours(1), Duration.ofHours(2)))).isEqualTo(Decision.ALLOWED);
        assertThat(decide(List.of(), OLD_USER, ago(Duration.ofHours(1), Duration.ofHours(2), Duration.ofHours(3))))
                .isEqualTo(Decision.DAILY_LIMIT);
        assertThat(decide(List.of(), OLD_USER, ago(Duration.ofHours(1), Duration.ofHours(2), Duration.ofHours(25))))
                .isEqualTo(Decision.ALLOWED);
    }

    // 가입 23시간 된 계정은 1곳만, 25시간 지나면 일반 한도
    @Test
    void newAccountLimit() {
        assertThat(decide(List.of(), NOW.minus(Duration.ofHours(23)), List.of())).isEqualTo(Decision.ALLOWED);
        assertThat(decide(List.of(), NOW.minus(Duration.ofHours(23)), ago(Duration.ofHours(1)))).isEqualTo(Decision.NEW_ACCOUNT_LIMIT);
        assertThat(decide(List.of(), NOW.minus(Duration.ofHours(25)), ago(Duration.ofHours(1)))).isEqualTo(Decision.ALLOWED);
    }

    // 한도에 걸려도 중복이면 중복을 먼저 보여줌
    @Test
    void duplicateBeforeLimit() {
        assertThat(decide(List.of(north(1, 10)), OLD_USER, ago(Duration.ofHours(1), Duration.ofHours(2), Duration.ofHours(3))))
                .isEqualTo(Decision.DUPLICATE);
    }

    // 확인 안 된 장소는 신고 2건, 확인된 장소는 3건이면 숨김
    @Test
    void hideByReports() {
        assertThat(PlaceRegisterGuard.shouldHide(false, 1)).isFalse();
        assertThat(PlaceRegisterGuard.shouldHide(false, 2)).isTrue();
        assertThat(PlaceRegisterGuard.shouldHide(true, 2)).isFalse();
        assertThat(PlaceRegisterGuard.shouldHide(true, 3)).isTrue();
    }
}
