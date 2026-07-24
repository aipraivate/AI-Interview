package com.interview.platform.order;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

interface PaymentCallbackRepository extends JpaRepository<PaymentCallback, String> {
    Optional<PaymentCallback> findByProviderAndEventId(String provider, String eventId);
}
