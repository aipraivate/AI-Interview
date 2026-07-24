package com.interview.platform.order;

import com.interview.platform.common.BusinessException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class PaymentWebhookService {
    private final PaymentCallbackRepository callbacks;
    private final OrderService orders;
    private final ObjectMapper objectMapper;
    private final String secret;

    public PaymentWebhookService(PaymentCallbackRepository callbacks, OrderService orders,
                                 ObjectMapper objectMapper,
                                 @Value("${app.payment.webhook-secret:}") String secret) {
        this.callbacks = callbacks;
        this.orders = orders;
        this.objectMapper = objectMapper;
        this.secret = secret;
    }

    @Transactional
    public OrderService.OrderView handle(String timestampValue, String signature, String rawBody) {
        verify(timestampValue, signature, rawBody);
        CallbackBody body;
        try {
            body = objectMapper.readValue(rawBody, CallbackBody.class);
        } catch (Exception exception) {
            throw new BusinessException("INVALID_PAYMENT_CALLBACK", "支付回调内容无效", HttpStatus.BAD_REQUEST);
        }
        if (blank(body.eventId()) || blank(body.orderId()) || blank(body.providerTradeNo())) {
            throw new BusinessException("INVALID_PAYMENT_CALLBACK", "支付回调字段不完整", HttpStatus.BAD_REQUEST);
        }
        return callbacks.findByProviderAndEventId("sandbox", body.eventId())
                .map(existing -> orders.getById(existing.getOrderId()))
                .orElseGet(() -> {
                    PaymentCallback callback = callbacks.save(new PaymentCallback(
                            "sandbox", body.eventId(), body.orderId(), sha256(rawBody)));
                    OrderService.OrderView order = orders.providerPay(body.orderId(), body.providerTradeNo());
                    callback.complete();
                    return order;
                });
    }

    private void verify(String timestampValue, String signature, String rawBody) {
        if (secret == null || secret.length() < 16) {
            throw new BusinessException("PAYMENT_NOT_CONFIGURED", "支付回调密钥未配置", HttpStatus.SERVICE_UNAVAILABLE);
        }
        long timestamp;
        try { timestamp = Long.parseLong(timestampValue); }
        catch (Exception exception) {
            throw new BusinessException("INVALID_PAYMENT_SIGNATURE", "支付签名无效", HttpStatus.UNAUTHORIZED);
        }
        if (Math.abs(Instant.now().getEpochSecond() - timestamp) > 300) {
            throw new BusinessException("PAYMENT_CALLBACK_EXPIRED", "支付回调已过期", HttpStatus.UNAUTHORIZED);
        }
        String expected = hmac(timestampValue + "." + rawBody);
        if (signature == null || !MessageDigest.isEqual(expected.getBytes(StandardCharsets.US_ASCII),
                signature.toLowerCase(java.util.Locale.ROOT).getBytes(StandardCharsets.US_ASCII))) {
            throw new BusinessException("INVALID_PAYMENT_SIGNATURE", "支付签名无效", HttpStatus.UNAUTHORIZED);
        }
    }

    private String hmac(String value) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to verify payment callback", exception);
        }
    }

    private String sha256(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException("Unable to hash callback", exception);
        }
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
    public record CallbackBody(String eventId, String orderId, String providerTradeNo) {}
}
