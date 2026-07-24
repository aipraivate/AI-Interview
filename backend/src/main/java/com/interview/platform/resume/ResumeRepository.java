package com.interview.platform.resume;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

interface ResumeRepository extends JpaRepository<Resume, String> {
    List<Resume> findByUserIdOrderByUpdatedAtDesc(String userId);
    Optional<Resume> findByIdAndUserId(String id, String userId);
}

