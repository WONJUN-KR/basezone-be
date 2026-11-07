package kr.pf.ballpark.user;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// 로그인 · 가입에 필요한 칸만 매핑. 나머지 칸(관심 정보 · 공개 동의 등)은 DB 기본값
@Entity
@Table(name = "users")
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "auth_provider", nullable = false, length = 10)
    private String authProvider;

    @Column(name = "auth_subject", nullable = false)
    private String authSubject;

    @Column(nullable = false, length = 12, unique = true)
    private String nickname;

    @Column(name = "terms_agreed_at")
    private Instant termsAgreedAt;

    @Column(name = "location_terms_agreed_at")
    private Instant locationTermsAgreedAt;

    @Column(name = "marketing_opt_in_at")
    private Instant marketingOptInAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected User() {
    }

    public User(String authProvider, String authSubject, String nickname, Instant now, boolean locationTerms, boolean marketing) {
        this.authProvider = authProvider;
        this.authSubject = authSubject;
        this.nickname = nickname;
        this.termsAgreedAt = now;
        this.locationTermsAgreedAt = locationTerms ? now : null;
        this.marketingOptInAt = marketing ? now : null;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public String getNickname() {
        return nickname;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
