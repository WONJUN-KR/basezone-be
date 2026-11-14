package kr.pf.ballpark.place;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import kr.pf.ballpark.place.PlaceQueryRepository.BattingRow;
import kr.pf.ballpark.place.PlaceQueryRepository.CatchballRow;
import kr.pf.ballpark.place.PlaceQueryRepository.CheckinPeopleRow;
import kr.pf.ballpark.place.PlaceQueryRepository.ReportRow;
import kr.pf.ballpark.place.PlaceResponses.BattingSummary;
import kr.pf.ballpark.place.PlaceResponses.CatchballPage;
import kr.pf.ballpark.place.PlaceResponses.CatchballSummary;
import kr.pf.ballpark.place.PlaceResponses.Crowd;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PlaceQueryService {

    static final int MAX_PLACES = 200;
    static final Duration CROWD_REPORT_WINDOW = Duration.ofMinutes(90);

    private final PlaceQueryRepository repository;
    private final Clock clock;

    // 한 번에 최대 200개 (place_id 순). 하나 더 읽어 보고 넘치면 truncated = true 와
    // 다음 조회 커서 nextAfterId(이번 마지막 place_id)를 줌. 앱은 같은 범위 + afterId 로 나머지를 이어서 조회
    public CatchballPage catchballInBounds(Bounds bounds, long afterId) {
        Instant now = clock.instant();
        List<CatchballRow> rows = repository.findCatchballInBounds(
                bounds.swLat(), bounds.swLng(), bounds.neLat(), bounds.neLng(), afterId, MAX_PLACES + 1);
        boolean truncated = rows.size() > MAX_PLACES;
        List<CatchballRow> places = truncated ? rows.subList(0, MAX_PLACES) : rows;
        List<Long> ids = places.stream().map(CatchballRow::placeId).toList();

        Map<Long, List<ReportRow>> reports = repository.findVisibleReportsSince(ids, now.minus(VerdictCalculator.WINDOW))
                .stream().collect(Collectors.groupingBy(ReportRow::placeId));
        Map<Long, Integer> people = repository.sumActiveCheckinPeople(ids, now).stream()
                .collect(Collectors.toMap(CheckinPeopleRow::placeId, CheckinPeopleRow::people));

        Instant crowdFrom = now.minus(CROWD_REPORT_WINDOW);
        return new CatchballPage(places.stream().map(p -> {
            List<ReportRow> rs = reports.getOrDefault(p.placeId(), List.of());
            VerdictCalculator.Result verdict = VerdictCalculator.calculate(rs.stream()
                    .map(r -> new VerdictCalculator.Vote(r.reportId(), r.userId(),
                            r.verdict() == null ? null : Verdict.fromCode(r.verdict()),
                            r.weight().doubleValue(), r.createdAt()))
                    .toList(), now);
            CrowdCalculator.Result crowd = CrowdCalculator.calculate(
                    people.getOrDefault(p.placeId(), 0),
                    rs.stream().filter(r -> r.createdAt().isAfter(crowdFrom)).map(ReportRow::crowd).toList());
            return new CatchballSummary(
                    p.placeId(), p.name(), p.lat().doubleValue(), p.lng().doubleValue(), p.verified(),
                    verdict.verdict(), verdict.judgeCount(), verdict.lastReportedAt(), verdict.needsRefresh(),
                    crowd == null ? null : new Crowd(crowd.level(), crowd.source()));
        }).toList(), truncated, truncated ? places.getLast().placeId() : null);
    }

    // 우리 DB 에 없는 배팅장 ID 는 결과에서 빠짐 (앱은 카카오 정보만으로 표시)
    public List<BattingSummary> battingByKakaoIds(List<String> kakaoPlaceIds) {
        Map<String, BattingRow> rows = repository.findBattingByKakaoIds(kakaoPlaceIds, clock.instant()).stream()
                .collect(Collectors.toMap(BattingRow::kakaoPlaceId, Function.identity()));
        return kakaoPlaceIds.stream().distinct()
                .filter(rows::containsKey)
                .map(id -> {
                    BattingRow r = rows.get(id);
                    return new BattingSummary(r.kakaoPlaceId(), r.placeId(),
                            r.knownBoxes() == 0 ? null : r.leftyBoxes(), r.knownBoxes());
                })
                .toList();
    }
}
