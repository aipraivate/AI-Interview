package com.interview.platform.entitlement;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

interface EntitlementAccountRepository extends JpaRepository<EntitlementAccount, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from EntitlementAccount e where e.userId = :userId")
    Optional<EntitlementAccount> findForUpdate(@Param("userId") String userId);
}

