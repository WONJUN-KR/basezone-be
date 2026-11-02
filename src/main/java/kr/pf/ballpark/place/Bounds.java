package kr.pf.ballpark.place;

// 지도에 보이는 범위 (남서쪽 · 북동쪽 꼭짓점)
public record Bounds(double swLat, double swLng, double neLat, double neLng) {

    // 너무 넓은 범위는 막음 (지도 축소 레벨이 높으면 앱이 '확대해 주세요'를 보여 줌)
    static final double MAX_SPAN_DEG = 0.2;

    public Bounds {
        if (swLat > neLat || swLng > neLng) {
            throw new IllegalArgumentException("남서쪽 좌표가 북동쪽보다 커요");
        }
        if (neLat - swLat > MAX_SPAN_DEG || neLng - swLng > MAX_SPAN_DEG) {
            throw new IllegalArgumentException("지도 범위가 너무 넓어요");
        }
        if (swLat < -90 || neLat > 90 || swLng < -180 || neLng > 180) {
            throw new IllegalArgumentException("좌표 범위를 벗어났어요");
        }
    }
}
