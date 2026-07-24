package com.interview.platform.entitlement;

import com.interview.platform.common.BusinessException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
public class EntitlementService {
    private final EntitlementAccountRepository accounts;
    private final EntitlementLedgerRepository ledger;

    public EntitlementService(EntitlementAccountRepository accounts, EntitlementLedgerRepository ledger) {
        this.accounts = accounts;
        this.ledger = ledger;
    }

    public void createTrialAccount(String userId, int credits) {
        accounts.save(new EntitlementAccount(userId, credits));
        ledger.save(new EntitlementLedger(userId, "GRANT", credits, null));
    }

    @Transactional
    public void reserve(String userId, String referenceId) {
        EntitlementAccount account = accountForUpdate(userId);
        try {
            account.reserve();
        } catch (IllegalStateException exception) {
            throw new BusinessException("INSUFFICIENT_CREDITS", "面试次数不足", HttpStatus.PAYMENT_REQUIRED);
        }
        ledger.save(new EntitlementLedger(userId, "RESERVE", 1, referenceId));
    }

    @Transactional
    public void confirmReservation(String userId, String referenceId) {
        EntitlementAccount account = accountForUpdate(userId);
        account.consumeReservation();
        ledger.save(new EntitlementLedger(userId, "CONSUME", 1, referenceId));
    }

    @Transactional
    public void releaseReservation(String userId, String referenceId) {
        EntitlementAccount account = accountForUpdate(userId);
        account.releaseReservation();
        ledger.save(new EntitlementLedger(userId, "RELEASE", 1, referenceId));
    }

    @Transactional(readOnly = true)
    public int availableCredits(String userId) {
        return accounts.findById(userId).map(EntitlementAccount::getAvailableCredits).orElse(0);
    }

    @Transactional
    public void grantCredits(String userId, int credits, String referenceId) {
        EntitlementAccount account = accountForUpdate(userId);
        account.grant(credits);
        ledger.save(new EntitlementLedger(userId, "GRANT", credits, referenceId));
    }

    @Transactional
    public boolean compensate(String userId, String referenceId, String reason) {
        if (ledger.existsByUserIdAndOperationAndReferenceId(userId, "COMPENSATE", referenceId)) return false;
        EntitlementAccount account = accountForUpdate(userId);
        account.grant(1);
        ledger.save(new EntitlementLedger(userId, "COMPENSATE", 1, referenceId));
        return true;
    }

    @Transactional
    public void revokeCredits(String userId, int credits, String referenceId) {
        if (ledger.existsByUserIdAndOperationAndReferenceId(userId, "REFUND", referenceId)) return;
        EntitlementAccount account = accountForUpdate(userId);
        try { account.deduct(credits); }
        catch (IllegalStateException exception) {
            throw BusinessException.conflict("CREDITS_ALREADY_USED", "对应权益已使用，退款需人工审核");
        }
        ledger.save(new EntitlementLedger(userId, "REFUND", credits, referenceId));
    }

    @Transactional(readOnly = true)
    public AccountView account(String userId) {
        EntitlementAccount account = accounts.findById(userId)
                .orElseThrow(() -> BusinessException.notFound("权益账户不存在"));
        return new AccountView(account.getAvailableCredits(), account.getReservedCredits());
    }

    @Transactional(readOnly = true)
    public List<LedgerView> ledger(String userId) {
        return ledger.findTop100ByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(value -> new LedgerView(value.getId(), value.getOperation(), value.getAmount(),
                        value.getReferenceId(), value.getCreatedAt()))
                .toList();
    }

    private EntitlementAccount accountForUpdate(String userId) {
        return accounts.findForUpdate(userId)
                .orElseThrow(() -> BusinessException.notFound("权益账户不存在"));
    }

    public record AccountView(int availableCredits, int reservedCredits) {}
    public record LedgerView(String id, String operation, int amount, String referenceId, Instant createdAt) {}
}
