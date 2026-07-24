package com.interview.platform.order;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

interface PurchaseOrderRepository extends JpaRepository<PurchaseOrder, String> {
    Optional<PurchaseOrder> findByUserIdAndIdempotencyKey(String userId, String idempotencyKey);
    List<PurchaseOrder> findByUserIdOrderByCreatedAtDesc(String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.id = :id and o.userId = :userId")
    Optional<PurchaseOrder> findOwnedForUpdate(@Param("id") String id, @Param("userId") String userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from PurchaseOrder o where o.id = :id")
    Optional<PurchaseOrder> findForUpdate(@Param("id") String id);
}
