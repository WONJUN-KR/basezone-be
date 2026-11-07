package kr.pf.ballpark.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import kr.pf.ballpark.place.CrowdCalculator.Result;
import kr.pf.ballpark.place.CrowdCalculator.Source;

class CrowdCalculatorTest {

    // 체크인이 있으면 제보보다 먼저 씀
    @Test
    void checkinFirst() {
        Result r = CrowdCalculator.calculate(5, List.of(3, 3, 3));
        assertThat(r.level()).isEqualTo(2);
        assertThat(r.source()).isEqualTo(Source.CHECKIN);
    }

    // 체크인 인원 -> 단계 (4 · 8명 경계, 혼잡이 끝 단계)
    @Test
    void checkinSteps() {
        assertThat(CrowdCalculator.calculate(1, List.of()).level()).isEqualTo(1);
        assertThat(CrowdCalculator.calculate(3, List.of()).level()).isEqualTo(1);
        assertThat(CrowdCalculator.calculate(4, List.of()).level()).isEqualTo(2);
        assertThat(CrowdCalculator.calculate(7, List.of()).level()).isEqualTo(2);
        assertThat(CrowdCalculator.calculate(8, List.of()).level()).isEqualTo(3);
        assertThat(CrowdCalculator.calculate(30, List.of()).level()).isEqualTo(3);
    }

    // 체크인이 없으면 최근 90분 제보의 평균
    @Test
    void reportAverage() {
        Result r = CrowdCalculator.calculate(0, List.of(2, 2, 3));
        assertThat(r.source()).isEqualTo(Source.REPORT);
        assertThat(r.level()).isEqualTo(2); // (2 + 2 + 3) / 3 = 2.33
        assertThat(CrowdCalculator.calculate(0, List.of(1, 3)).level()).isEqualTo(2); // 2.0
        assertThat(CrowdCalculator.calculate(0, List.of(1, 2)).level()).isEqualTo(2); // 1.5 -> 반올림
        assertThat(CrowdCalculator.calculate(0, List.of(2, 3)).level()).isEqualTo(3); // 2.5 -> 반올림
        assertThat(CrowdCalculator.calculate(0, List.of(3)).level()).isEqualTo(3);
    }

    // 범위 밖 값(0 · 4) · null 은 빼고 계산
    @Test
    void ignoresOutOfRange() {
        assertThat(CrowdCalculator.calculate(0, List.of(0, 4, 1)).level()).isEqualTo(1);
        assertThat(CrowdCalculator.calculate(0, List.of(4))).isNull();
        assertThat(CrowdCalculator.calculate(0, Arrays.asList(null, 2)).level()).isEqualTo(2);
    }

    // 체크인도 제보도 없으면 null (화면에서 혼잡도 숨김)
    @Test
    void noData() {
        assertThat(CrowdCalculator.calculate(0, List.of())).isNull();
        assertThat(CrowdCalculator.calculate(0, Arrays.asList((Integer) null))).isNull();
    }
}
