package com.interview.platform.order;

import com.interview.platform.common.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders")
class OrderController {
    private final OrderService orders;

    OrderController(OrderService orders) {
        this.orders = orders;
    }

    @GetMapping("/products")
    ApiResponse<List<OrderService.ProductView>> products() { return ApiResponse.ok(orders.products()); }

    @PostMapping
    ApiResponse<OrderService.OrderView> create(@AuthenticationPrincipal String userId,
                                               @RequestHeader("Idempotency-Key") String idempotencyKey,
                                               @Valid @RequestBody CreateOrder request) {
        return ApiResponse.ok(orders.create(userId, request.productId(), idempotencyKey));
    }

    @PostMapping("/{orderId}/sandbox-pay")
    ApiResponse<OrderService.OrderView> sandboxPay(@AuthenticationPrincipal String userId,
                                                   @PathVariable String orderId,
                                                   @Valid @RequestBody SandboxPay request) {
        return ApiResponse.ok(orders.sandboxPay(userId, orderId, request.providerTradeNo()));
    }

    @GetMapping
    ApiResponse<List<OrderService.OrderView>> list(@AuthenticationPrincipal String userId) {
        return ApiResponse.ok(orders.list(userId));
    }

    @PostMapping("/{orderId}/refund")
    ApiResponse<OrderService.OrderView> refund(@AuthenticationPrincipal String userId,
                                               @PathVariable String orderId,
                                               @RequestHeader("Idempotency-Key") String idempotencyKey,
                                               @RequestBody(required = false) RefundRequest request) {
        return ApiResponse.ok(orders.refund(userId, orderId,
                request == null ? null : request.reason(), idempotencyKey));
    }

    record CreateOrder(@NotBlank String productId) {}
    record SandboxPay(@NotBlank @Size(max = 100) String providerTradeNo) {}
    record RefundRequest(@Size(max = 200) String reason) {}
}
