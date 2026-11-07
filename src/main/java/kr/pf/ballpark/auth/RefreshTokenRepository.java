package kr.pf.ballpark.auth;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    long deleteByTokenHash(String tokenHash);

    // 아직 안 만료된 토큰만 지우고 지운 수를 돌려줌 (갱신 때 1 이어야 성공)
    @Modifying
    @Query("delete from RefreshToken t where t.tokenHash = :hash and t.expiresAt > :now")
    int deleteActive(@Param("hash") String hash, @Param("now") Instant now);
}
