package com.interview.platform.auth;

import org.springframework.data.jpa.repository.JpaRepository;

interface PolicyConsentRepository extends JpaRepository<PolicyConsent, String> {}
