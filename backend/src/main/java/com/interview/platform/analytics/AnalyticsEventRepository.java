package com.interview.platform.analytics;

import org.springframework.data.jpa.repository.JpaRepository;
interface AnalyticsEventRepository extends JpaRepository<AnalyticsEvent, String> {}
