package com.interview.platform.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Optional;
import java.util.List;

interface AuthTokenRepository extends JpaRepository<AuthToken, String> {
    Optional<AuthToken> findByTokenHashAndTokenTypeAndRevokedAtIsNullAndExpiresAtAfter(
            String tokenHash, String tokenType, Instant now);
    List<AuthToken> findByUserIdAndRevokedAtIsNull(String userId);
}
