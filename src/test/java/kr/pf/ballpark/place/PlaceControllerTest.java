package kr.pf.ballpark.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.assertj.MockMvcTester;

import kr.pf.ballpark.place.PlaceResponses.BattingSummary;
import kr.pf.ballpark.place.PlaceResponses.CatchballSummary;
import kr.pf.ballpark.place.PlaceResponses.Crowd;

@WebMvcTest(PlaceController.class)
class PlaceControllerTest {

    @Autowired
    private MockMvcTester mvc;

    @MockitoBean
    private PlaceQueryService service;

    // 지도 범위로 캐치볼 장소 조회 -> 판정 · 혼잡도가 소문자 코드로 나감
    @Test
    void catchball() {
        given(service.catchballInBounds(any())).willReturn(List.of(new CatchballSummary(
                1L, "잠실한강공원 잔디밭", 37.5185, 127.0868, Verdict.SAFE, 12,
                Instant.parse("2026-10-30T03:00:00Z"), false, new Crowd(2, CrowdCalculator.Source.REPORT))));

        assertThat(mvc.get().uri("/api/places/catchball?swLat=37.50&swLng=127.07&neLat=37.53&neLng=127.10"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("""
                        [{"id":1,"name":"잠실한강공원 잔디밭","verdict":"safe","judgeCount":12,
                          "lastReportedAt":"2026-10-30T03:00:00Z","needsRefresh":false,
                          "crowd":{"level":2,"source":"report"}}]
                        """);
    }

    // 지도 범위가 0.2도보다 넓으면 400
    @Test
    void boundsTooWide() {
        assertThat(mvc.get().uri("/api/places/catchball?swLat=37.0&swLng=127.0&neLat=38.0&neLng=128.0"))
                .hasStatus(400);
    }

    // 카카오 배팅장 ID 로 우리 데이터(왼손 타석 수) 조회
    @Test
    void battingLookup() {
        given(service.battingByKakaoIds(List.of("12345", "67890")))
                .willReturn(List.of(new BattingSummary("12345", 7L, 2, 8)));

        assertThat(mvc.post().uri("/api/places/batting/lookup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kakaoPlaceIds\":[\"12345\",\"67890\"]}"))
                .hasStatusOk()
                .bodyJson()
                .isLenientlyEqualTo("[{\"kakaoPlaceId\":\"12345\",\"placeId\":7,\"leftyBoxes\":2,\"knownBoxes\":8}]");
    }

    // 카카오 장소 ID 가 숫자가 아니면 400
    @Test
    void invalidKakaoId() {
        assertThat(mvc.post().uri("/api/places/batting/lookup")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"kakaoPlaceIds\":[\"abc\"]}"))
                .hasStatus(400);
    }
}
