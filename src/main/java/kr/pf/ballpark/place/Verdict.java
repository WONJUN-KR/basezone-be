package kr.pf.ballpark.place;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonValue;

// 캐치볼 장소 판정. DB(reports.verdict) · API 값은 소문자
public enum Verdict {
    SAFE, REVIEW, OUT;

    @JsonValue
    public String code() {
        return name().toLowerCase(Locale.ROOT);
    }

    public static Verdict fromCode(String code) {
        return valueOf(code.toUpperCase(Locale.ROOT));
    }
}
