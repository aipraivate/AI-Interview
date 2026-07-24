package com.interview.platform.privacy;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

interface DataRequestRepository extends JpaRepository<DataRequest, String> {
    List<DataRequest> findByUserIdOrderByCreatedAtDesc(String userId);
    List<DataRequest> findTop10ByStatusOrderByCreatedAtAsc(String status);
    java.util.Optional<DataRequest> findByIdAndUserId(String id, String userId);
}
