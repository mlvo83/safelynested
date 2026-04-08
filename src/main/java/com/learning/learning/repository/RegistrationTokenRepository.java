package com.learning.learning.repository;

import com.learning.learning.entity.RegistrationToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Optional;

@Repository
public interface RegistrationTokenRepository extends JpaRepository<RegistrationToken, Long> {

    Optional<RegistrationToken> findByToken(String token);

    Optional<RegistrationToken> findByEmailAndIsUsedFalse(String email);

    boolean existsByEmailAndIsUsedFalseAndExpiresAtAfter(String email, LocalDateTime now);
}
