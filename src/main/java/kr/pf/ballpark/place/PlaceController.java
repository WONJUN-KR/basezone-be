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

import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import kr.pf.ballpark.place.PlaceResponses.BattingSummary;
import kr.pf.ballpark.place.PlaceResponses.CatchballPage;
import lombok.RequiredArgsConstructor;

// 홈 지도 조회 API. 로그인 없이 볼 수 있음
@RestController
@RequestMapping("/api/places")
@RequiredArgsConstructor
@Validated
public class PlaceController {

    private final PlaceQueryService service;

    @GetMapping("/catchball")
    public CatchballPage catchball(
            @RequestParam double swLat, @RequestParam double swLng,
            @RequestParam double neLat, @RequestParam double neLng,
            @RequestParam(defaultValue = "0") @PositiveOrZero long afterId) {
        return service.catchballInBounds(new Bounds(swLat, swLng, neLat, neLng), afterId);
    }

    // 기기가 카카오 지도에서 찾은 배팅장 ID 목록 -> 우리 데이터(왼손 타석 수)
    // GET 쿼리로 보내면 ID 목록이 길어져서 POST 본문으로 받음
    @PostMapping("/batting/lookup")
    public List<BattingSummary> battingLookup(@Valid @RequestBody BattingLookupRequest request) {
        return service.battingByKakaoIds(request.kakaoPlaceIds());
    }

    public record BattingLookupRequest(
            // @Pattern 은 null 을 통과시키므로 @NotNull 도 (["12345", null] 막기)
            @NotEmpty @Size(max = 50) List<@NotNull @Pattern(regexp = "\\d{1,20}") String> kakaoPlaceIds) {
    }

    @ExceptionHandler(InvalidBoundsException.class)
    public ResponseEntity<Map<String, String>> badRequest(InvalidBoundsException e) {
        return ResponseEntity.badRequest().body(Map.of("message", e.getMessage()));
    }

    // @Validated 의 쿼리 파라미터 검증 실패 (afterId 음수 등)는 500 이 아니라 400
    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, String>> invalidParam(ConstraintViolationException e) {
        return ResponseEntity.badRequest().body(Map.of("message", "요청 값이 올바르지 않아요"));
    }
}
