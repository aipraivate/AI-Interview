package com.interview.platform.order;

import com.interview.platform.common.BusinessException;
import com.interview.platform.entitlement.EntitlementService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class OrderService {
    private static final Map<String, PurchaseOrder.Product> PRODUCTS = Map.of(
            "credits-5", new PurchaseOrder.Product("credits-5", "5次文字面试包", 5, 990),
            "credits-20", new PurchaseOrder.Product("credits-20", "20次文字面试包", 20, 2990)
    );

    private final PurchaseOrderRepository orders;
    private final EntitlementService entitlements;

    public OrderService(PurchaseOrderRepository orders, EntitlementService entitlements) {
        this.orders = orders;
        this.entitlements = entitlements;
    }

    public List<ProductView> products() {
        return PRODUCTS.values().stream().map(value -> new ProductView(
                value.id(), value.name(), value.credits(), value.amountCents(), "CNY")).toList();
    }

    @Transactional
    public OrderView create(String userId, String productId, String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new BusinessException("IDEMPOTENCY_KEY_REQUIRED", "创建订单需要 Idempotency-Key",
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }
        PurchaseOrder.Product product = PRODUCTS.get(productId);
        if (product == null) throw BusinessException.notFound("商品不存在");
        return orders.findByUserIdAndIdempotencyKey(userId, idempotencyKey)
                .map(this::view)
                .orElseGet(() -> view(orders.save(new PurchaseOrder(userId, product, idempotencyKey))));
    }

    @Transactional
    public OrderView sandboxPay(String userId, String orderId, String tradeNo) {
        PurchaseOrder order = orders.findOwnedForUpdate(orderId, userId)
                .orElseThrow(() -> BusinessException.notFound("订单不存在"));
        try {
            if (order.markPaid(tradeNo)) {
                entitlements.grantCredits(userId, order.getCredits(), order.getId());
            }
        } catch (IllegalStateException exception) {
            throw BusinessException.conflict("ORDER_NOT_PAYABLE", "订单当前不能支付");
        }
        return view(order);
    }

    @Transactional(readOnly = true)
    public List<OrderView> list(String userId) {
        return orders.findByUserIdOrderByCreatedAtDesc(userId).stream().map(this::view).toList();
    }

    @Transactional
    public OrderView refund(String userId, String orderId, String reason, String idempotencyKey) {
        requireIdempotencyKey(idempotencyKey, "退款");
        PurchaseOrder order = orders.findOwnedForUpdate(orderId, userId)
                .orElseThrow(() -> BusinessException.notFound("订单不存在"));
        try {
            if (order.refund(reason == null ? "用户申请" : reason.trim(), idempotencyKey)) {
                entitlements.revokeCredits(userId, order.getCredits(), order.getId());
            }
        } catch (IllegalStateException exception) {
            throw BusinessException.conflict("ORDER_NOT_REFUNDABLE", "订单当前不能退款");
        }
        return view(order);
    }

    @Transactional
    public OrderView providerPay(String orderId, String tradeNo) {
        PurchaseOrder order = orders.findForUpdate(orderId)
                .orElseThrow(() -> BusinessException.notFound("订单不存在"));
        try {
            if (order.markPaid(tradeNo)) {
                entitlements.grantCredits(order.getUserId(), order.getCredits(), order.getId());
            }
        } catch (IllegalStateException exception) {
            throw BusinessException.conflict("ORDER_NOT_PAYABLE", "订单当前不能支付");
        }
        return view(order);
    }

    @Transactional(readOnly = true)
    public OrderView getById(String orderId) {
        return orders.findById(orderId).map(this::view)
                .orElseThrow(() -> BusinessException.notFound("订单不存在"));
    }

    private void requireIdempotencyKey(String value, String operation) {
        if (value == null || value.isBlank() || value.length() > 80) {
            throw new BusinessException("IDEMPOTENCY_KEY_REQUIRED", operation + "需要有效的 Idempotency-Key",
                    org.springframework.http.HttpStatus.BAD_REQUEST);
        }
    }

    private OrderView view(PurchaseOrder value) {
        return new OrderView(value.getId(), value.getProductId(), value.getProductName(),
                value.getCredits(), value.getAmountCents(), value.getCurrency(), value.getStatus(),
                value.getCreatedAt(), value.getPaidAt(), value.getRefundedAt(), value.getRefundReason());
    }

    public record ProductView(String id, String name, int credits, int amountCents, String currency) {}
    public record OrderView(String id, String productId, String productName, int credits,
                            int amountCents, String currency, String status,
                            Instant createdAt, Instant paidAt, Instant refundedAt, String refundReason) {}
}
