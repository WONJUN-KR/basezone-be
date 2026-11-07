package kr.pf.ballpark.user;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByAuthProviderAndAuthSubject(String authProvider, String authSubject);

    boolean existsByNickname(String nickname);
}
