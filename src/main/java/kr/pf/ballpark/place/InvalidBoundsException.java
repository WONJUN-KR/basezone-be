package kr.pf.ballpark.place;

// 지도 범위 요청 값이 잘못됨 -> 400. 다른 IllegalArgumentException(서버 버그)이 400 으로 섞이지 않게 따로 둠
public class InvalidBoundsException extends RuntimeException {

    public InvalidBoundsException(String message) {
        super(message);
    }
}
