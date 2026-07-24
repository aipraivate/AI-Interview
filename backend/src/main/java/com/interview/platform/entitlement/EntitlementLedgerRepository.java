package com.interview.platform.entitlement;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface EntitlementLedgerRepository extends JpaRepository<EntitlementLedger, String> {
    List<EntitlementLedger> findTop100ByUserIdOrderByCreatedAtDesc(String userId);
    boolean existsByUserIdAndOperationAndReferenceId(String userId, String operation, String referenceId);
}
