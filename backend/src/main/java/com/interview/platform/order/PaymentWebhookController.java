package com.interview.platform.order;

import com.interview.platform.common.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/payments/webhooks")
class PaymentWebhookController {
    private final PaymentWebhookService webhooks;

    PaymentWebhookController(PaymentWebhookService webhooks) { this.webhooks = webhooks; }

    @PostMapping("/sandbox")
    ApiResponse<OrderService.OrderView> sandbox(
            @RequestHeader("X-Payment-Timestamp") String timestamp,
            @RequestHeader("X-Payment-Signature") String signature,
            @RequestBody String rawBody) {
        return ApiResponse.ok(webhooks.handle(timestamp, signature, rawBody));
    }
}
