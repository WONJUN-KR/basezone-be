package kr.pf.ballpark.place;

import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import kr.pf.ballpark.place.PlaceResponses.BattingSummary;
import kr.pf.ballpark.place.PlaceResponses.CatchballSummary;
import lombok.RequiredArgsConstructor;

// 홈 지도 조회 API. 로그인 없이 볼 수 있음
@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
@Validated
public class PlaceController {

    private final PlaceQueryService service;

    @GetMapping("/catchball")
    public List<CatchballSummary> catchball(
            @RequestParam double swLat, @RequestParam double swLng,
            @RequestParam double neLat, @RequestParam double neLng) {
        return service.catchballInBounds(new Bounds(swLat, swLng, neLat, neLng));
    }

    // 기기가 카카오 지도에서 찾은 배팅장 ID 목록 -> 우리 데이터(왼손 타석 수)
    // GET 쿼리로 보내면 ID 목록이 길어져서 POST 본문으로 받음
    @PostMapping("/batting/lookup")
    public List<BattingSummary> battingLookup(@Valid @RequestBody BattingLookupRequest request) {
        return service.battingByKakaoIds(request.kakaoPlaceIds());
    }

    public record BattingLookupRequest(
            @NotEmpty @Size(max = 50) List<@Pattern(regexp = "\\d{1,20}") String> kakaoPlaceIds) {
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }
}
