package com.interview.platform.interview;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface RequestIdempotencyRepository extends JpaRepository<RequestIdempotency, String> {
    Optional<RequestIdempotency> findByUserIdAndScopeAndIdempotencyKey(
            String userId, String scope, String idempotencyKey);
}
