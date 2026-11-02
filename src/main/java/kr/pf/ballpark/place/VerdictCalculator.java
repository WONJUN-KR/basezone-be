package kr.pf.ballpark.place;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

// 판정 규칙 (디자인 '규칙 숫자' 판과 같은 숫자)
// - 최근 30일 제보만, 무게는 7일마다 절반 (reports.weight 는 사용자 보정 무게)
// - 불가 비율 70% 이상 또는 24시간 안에 서로 다른 2명이 불가 -> 불가
// - 가능 비율 70% 이상 -> 가능, 나머지 -> 확인 필요 (두 비율은 따로 계산)
// - 마지막 제보가 7일 넘으면 판정은 두고 '새 제보 필요', 30일 넘으면 셀 제보가 없어서 확인 필요
public final class VerdictCalculator {

    static final Duration WINDOW = Duration.ofDays(30);
    static final Duration HALF_LIFE = Duration.ofDays(7);
    static final Duration STALE_AFTER = Duration.ofDays(7);
    static final Duration QUICK_OUT_WINDOW = Duration.ofHours(24);
    static final double RATIO = 0.7;
    static final int QUICK_OUT_USERS = 2;

    private VerdictCalculator() {
    }

    // userId 는 탈퇴로 끊긴 제보면 null (그때는 제보 한 건을 한 사람으로 셈)
    public record Vote(Long reportId, Long userId, Verdict verdict, double weight, Instant createdAt) {
    }

    public record Result(Verdict verdict, int judgeCount, Instant lastReportedAt, boolean needsRefresh) {
    }

    public static Result calculate(List<Vote> votes, Instant now) {
        Instant from = now.minus(WINDOW);
        List<Vote> counted = votes.stream()
                .filter(v -> v.verdict() != null && v.createdAt().isAfter(from) && !v.createdAt().isAfter(now))
                .toList();
        if (counted.isEmpty()) {
            return new Result(Verdict.REVIEW, 0, null, false);
        }

        double total = 0, safe = 0, out = 0;
        Set<Object> judges = new HashSet<>();
        Set<Object> quickOutUsers = new HashSet<>();
        Instant last = Instant.MIN;
        for (Vote v : counted) {
            double ageDays = Duration.between(v.createdAt(), now).toMillis() / 86_400_000.0;
            double w = v.weight() * Math.pow(0.5, ageDays / HALF_LIFE.toDays());
            total += w;
            if (v.verdict() == Verdict.SAFE) safe += w;
            if (v.verdict() == Verdict.OUT) out += w;

            Object who = Objects.requireNonNullElse(v.userId(), "report:" + v.reportId());
            judges.add(who);
            if (v.verdict() == Verdict.OUT && v.createdAt().isAfter(now.minus(QUICK_OUT_WINDOW))) {
                quickOutUsers.add(who);
            }
            if (v.createdAt().isAfter(last)) last = v.createdAt();
        }

        Verdict verdict;
        if (out / total >= RATIO || quickOutUsers.size() >= QUICK_OUT_USERS) {
            verdict = Verdict.OUT;
        } else if (safe / total >= RATIO) {
            verdict = Verdict.SAFE;
        } else {
            verdict = Verdict.REVIEW;
        }
        boolean needsRefresh = last.isBefore(now.minus(STALE_AFTER));
        return new Result(verdict, judges.size(), last, needsRefresh);
    }
}
