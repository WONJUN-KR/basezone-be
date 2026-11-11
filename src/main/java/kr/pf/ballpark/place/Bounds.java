package kr.pf.ballpark.place;

// 지도에 보이는 범위 (남서쪽 · 북동쪽 꼭짓점)
public record Bounds(double swLat, double swLng, double neLat, double neLng) {

    // 너무 넓은 범위는 막음 (지도 축소 레벨이 높으면 앱이 '확대해 주세요'를 보여 줌)
    static final double MAX_SPAN_DEG = 0.2;
    // 소수 뺄셈 오차 허용 (37.2 - 37.0 = 0.20000000000000284 라서 딱 0.2 도 넓다고 막히는 것 방지)
    private static final double SPAN_EPSILON = 1e-9;

    public Bounds {
        // NaN · 무한대는 아래 비교를 모두 통과해 버리므로 먼저 막음
        if (!Double.isFinite(swLat) || !Double.isFinite(swLng)
                || !Double.isFinite(neLat) || !Double.isFinite(neLng)) {
            throw new IllegalArgumentException("좌표가 숫자가 아니에요");
        }
        if (swLat > neLat || swLng > neLng) {
            throw new IllegalArgumentException("남서쪽 좌표가 북동쪽보다 커요");
        }
        if (neLat - swLat > MAX_SPAN_DEG + SPAN_EPSILON || neLng - swLng > MAX_SPAN_DEG + SPAN_EPSILON) {
            throw new IllegalArgumentException("지도 범위가 너무 넓어요");
        }
        if (swLat < -90 || neLat > 90 || swLng < -180 || neLng > 180) {
            throw new IllegalArgumentException("좌표 범위를 벗어났어요");
        }
    }
}
