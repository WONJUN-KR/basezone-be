package kr.pf.ballpark.place;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonValue;

// 혼잡도 1~4 (앱 화면: 여유 · 보통 · 조금 혼잡 · 혼잡)
// '지금 이용 중' 체크인이 있으면 그 인원으로, 없을 때만 최근 90분 제보(1 여유 · 2 보통 · 3 혼잡)로
// 인원 -> 단계 경계(4 · 8 · 12명)와 제보 3단계 -> 화면 4단계 대응은 가정. 기획 확정되면 바꿈
public final class CrowdCalculator {

    static final int[] PEOPLE_STEPS = {4, 8, 12};
    private static final int[] REPORT_TO_LEVEL = {0, 1, 2, 4};

    private CrowdCalculator() {
    }

    public enum Source {
        CHECKIN, REPORT;

        @JsonValue
        public String code() {
            return name().toLowerCase();
        }
    }

    public record Result(int level, Source source) {
    }

    // checkinPeople: 살아 있는 체크인 인원 합 (party_size 없으면 1명). recentReportCrowds: 최근 90분 제보의 crowd 값
    public static Result calculate(int checkinPeople, List<Integer> recentReportCrowds) {
        if (checkinPeople > 0) {
            int level = 1;
            for (int step : PEOPLE_STEPS) {
                if (checkinPeople >= step) level++;
            }
            return new Result(level, Source.CHECKIN);
        }
        List<Integer> valid = recentReportCrowds.stream().filter(c -> c != null && c >= 1 && c <= 3).toList();
        if (valid.isEmpty()) {
            return null;
        }
        double avg = valid.stream().mapToInt(c -> REPORT_TO_LEVEL[c]).average().orElseThrow();
        return new Result((int) Math.round(avg), Source.REPORT);
    }
}
