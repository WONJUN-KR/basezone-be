package kr.pf.ballpark.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import kr.pf.ballpark.place.VerdictCalculator.Result;
import kr.pf.ballpark.place.VerdictCalculator.Vote;

class VerdictCalculatorTest {

    private static final Instant NOW = Instant.parse("2026-11-02T06:00:00Z");
    private long seq = 1;

    private Vote vote(Long userId, Verdict verdict, Duration ago) {
        return new Vote(seq++, userId, verdict, 1.0, NOW.minus(ago));
    }

    // 제보가 없으면 확인 필요, 판정 인원 0명
    @Test
    void noReports() {
        Result r = VerdictCalculator.calculate(List.of(), NOW);
        assertThat(r.verdict()).isEqualTo(Verdict.REVIEW);
        assertThat(r.judgeCount()).isZero();
        assertThat(r.lastReportedAt()).isNull();
    }

    // 새 장소는 1명부터 판정
    @Test
    void singleReport() {
        Result r = VerdictCalculator.calculate(List.of(vote(1L, Verdict.SAFE, Duration.ofHours(1))), NOW);
        assertThat(r.verdict()).isEqualTo(Verdict.SAFE);
        assertThat(r.judgeCount()).isEqualTo(1);
    }

    // 가능 70% 이상이면 가능
    @Test
    void safeAt70Percent() {
        List<Vote> votes = new ArrayList<>();
        for (long u = 1; u <= 7; u++) votes.add(vote(u, Verdict.SAFE, Duration.ofHours(2)));
        for (long u = 8; u <= 10; u++) votes.add(vote(u, Verdict.REVIEW, Duration.ofHours(2)));
        assertThat(VerdictCalculator.calculate(votes, NOW).verdict()).isEqualTo(Verdict.SAFE);
    }

    // 가능 · 불가 비율은 따로 계산
    // 가능 30% · 확인 필요 40% · 불가 30% -> 둘 다 70% 아님 -> 확인 필요 (가능 30% 이하라고 불가가 아님)
    @Test
    void ratiosAreSeparate() {
        List<Vote> votes = new ArrayList<>();
        for (long u = 1; u <= 3; u++) votes.add(vote(u, Verdict.SAFE, Duration.ofDays(2)));
        for (long u = 4; u <= 7; u++) votes.add(vote(u, Verdict.REVIEW, Duration.ofDays(2)));
        for (long u = 8; u <= 10; u++) votes.add(vote(u, Verdict.OUT, Duration.ofDays(2)));
        assertThat(VerdictCalculator.calculate(votes, NOW).verdict()).isEqualTo(Verdict.REVIEW);
    }

    // 24시간 안에 서로 다른 2명이 불가면 비율과 상관없이 불가
    @Test
    void twoOutsIn24h() {
        List<Vote> votes = new ArrayList<>();
        for (long u = 1; u <= 8; u++) votes.add(vote(u, Verdict.SAFE, Duration.ofDays(1).plusHours(1)));
        votes.add(vote(20L, Verdict.OUT, Duration.ofHours(3)));
        votes.add(vote(21L, Verdict.OUT, Duration.ofHours(1)));
        assertThat(VerdictCalculator.calculate(votes, NOW).verdict()).isEqualTo(Verdict.OUT);
    }

    // 같은 사람이 불가를 두 번 해도 '2명 불가'로 세지 않음
    @Test
    void sameUserOutTwice() {
        List<Vote> votes = new ArrayList<>();
        for (long u = 1; u <= 8; u++) votes.add(vote(u, Verdict.SAFE, Duration.ofHours(5)));
        votes.add(vote(20L, Verdict.OUT, Duration.ofHours(3)));
        votes.add(vote(20L, Verdict.OUT, Duration.ofHours(1)));
        assertThat(VerdictCalculator.calculate(votes, NOW).verdict()).isNotEqualTo(Verdict.OUT);
    }

    // 오래된 제보는 7일마다 무게 절반
    // 14일 전 불가 3표(무게 1/4씩) vs 오늘 가능 2표 -> 가능 2 / (2 + 0.75) = 72.7%
    @Test
    void oldReportsDecay() {
        List<Vote> votes = new ArrayList<>();
        for (long u = 1; u <= 3; u++) votes.add(vote(u, Verdict.OUT, Duration.ofDays(14)));
        votes.add(vote(10L, Verdict.SAFE, Duration.ofMinutes(10)));
        votes.add(vote(11L, Verdict.SAFE, Duration.ofMinutes(20)));
        assertThat(VerdictCalculator.calculate(votes, NOW).verdict()).isEqualTo(Verdict.SAFE);
    }

    // 마지막 제보가 7일 넘으면 판정은 그대로, '새 제보 필요' 표시
    @Test
    void staleAfter7Days() {
        Result r = VerdictCalculator.calculate(List.of(vote(1L, Verdict.SAFE, Duration.ofDays(8))), NOW);
        assertThat(r.verdict()).isEqualTo(Verdict.SAFE);
        assertThat(r.needsRefresh()).isTrue();
    }

    // 30일 넘은 제보는 세지 않음
    @Test
    void ignoreOver30Days() {
        Result r = VerdictCalculator.calculate(List.of(vote(1L, Verdict.SAFE, Duration.ofDays(31))), NOW);
        assertThat(r.verdict()).isEqualTo(Verdict.REVIEW);
        assertThat(r.judgeCount()).isZero();
    }

    // 판정 없는 제보(혼잡도만)는 빼고, 탈퇴로 연결이 끊긴 제보는 한 건을 한 명으로 셈
    @Test
    void nullVerdictAndUser() {
        List<Vote> votes = List.of(
                vote(null, Verdict.SAFE, Duration.ofHours(1)),
                vote(null, Verdict.SAFE, Duration.ofHours(2)),
                vote(3L, null, Duration.ofHours(1)));
        Result r = VerdictCalculator.calculate(votes, NOW);
        assertThat(r.verdict()).isEqualTo(Verdict.SAFE);
        assertThat(r.judgeCount()).isEqualTo(2);
    }
}
