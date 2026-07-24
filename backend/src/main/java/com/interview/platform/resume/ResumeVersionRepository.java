package com.interview.platform.resume;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

interface ResumeVersionRepository extends JpaRepository<ResumeVersion, String> {
    List<ResumeVersion> findByResumeIdOrderByVersionNoDesc(String resumeId);
}
