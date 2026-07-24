package com.interview.platform.auth;

import com.interview.platform.common.BusinessException;
import com.interview.platform.entitlement.EntitlementService;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Locale;

@Service
public class AuthService {
    private static final String TERMS_VERSION = "2026-07-21";
    private static final String PRIVACY_VERSION = "2026-07-21";

    private final UserAccountRepository users;
    private final AuthTokenRepository tokens;
    private final PolicyConsentRepository consents;
    private final TokenHasher tokenHasher;
    private final PasswordEncoder passwordEncoder;
    private final EntitlementService entitlements;
    private final SecureRandom secureRandom = new SecureRandom();

    public AuthService(UserAccountRepository users, AuthTokenRepository tokens,
                       PolicyConsentRepository consents, TokenHasher tokenHasher,
                       PasswordEncoder passwordEncoder, EntitlementService entitlements) {
        this.users = users;
        this.tokens = tokens;
        this.consents = consents;
        this.tokenHasher = tokenHasher;
        this.passwordEncoder = passwordEncoder;
        this.entitlements = entitlements;
    }

    @Transactional
    public GuestLogin loginAsGuest(String nickname) {
        UserAccount user = users.save(new UserAccount(normalizeNickname(nickname)));
        entitlements.createTrialAccount(user.getId(), 3);
        return issueSession(user);
    }

    @Transactional
    public GuestLogin register(RegisterCommand command) {
        return register(command, null);
    }

    @Transactional
    public GuestLogin register(RegisterCommand command, String currentUserId) {
        if (!command.acceptTerms() || !command.acceptPrivacy()) {
            throw new BusinessException("POLICY_CONSENT_REQUIRED", "请先同意服务协议和隐私政策",
                    HttpStatus.UNPROCESSABLE_ENTITY);
        }
        String email = normalizeEmail(command.email());
        if (users.existsByEmailIgnoreCase(email)) {
            throw BusinessException.conflict("EMAIL_ALREADY_REGISTERED", "该邮箱已注册");
        }
        UserAccount user;
        if (currentUserId != null) {
            user = users.findById(currentUserId).orElseThrow(() -> BusinessException.notFound("账号不存在"));
            try {
                user.upgrade(email, passwordEncoder.encode(command.password()),
                        normalizeNickname(command.nickname()));
            } catch (IllegalStateException exception) {
                throw BusinessException.conflict("ACCOUNT_ALREADY_REGISTERED", "当前账号已完成注册");
            }
            tokens.findByUserIdAndRevokedAtIsNull(currentUserId).forEach(AuthToken::revoke);
        } else {
            user = users.save(UserAccount.formal(email,
                    passwordEncoder.encode(command.password()), normalizeNickname(command.nickname())));
            entitlements.createTrialAccount(user.getId(), 3);
        }
        consents.save(new PolicyConsent(user.getId(), "TERMS", TERMS_VERSION));
        consents.save(new PolicyConsent(user.getId(), "PRIVACY", PRIVACY_VERSION));
        return issueSession(user);
    }

    @Transactional
    public GuestLogin login(LoginCommand command) {
        UserAccount user = users.findByEmailIgnoreCase(normalizeEmail(command.email()))
                .filter(value -> "ACTIVE".equals(value.getStatus()))
                .orElseThrow(this::invalidCredentials);
        if (user.getPasswordHash() == null || !passwordEncoder.matches(command.password(), user.getPasswordHash())) {
            throw invalidCredentials();
        }
        return issueSession(user);
    }

    @Transactional
    public GuestLogin refresh(String rawRefreshToken) {
        AuthToken refresh = findToken(rawRefreshToken, "REFRESH")
                .orElseThrow(() -> new BusinessException("INVALID_REFRESH_TOKEN", "登录状态已失效",
                        HttpStatus.UNAUTHORIZED));
        refresh.revoke();
        UserAccount user = users.findById(refresh.getUserId())
                .filter(value -> "ACTIVE".equals(value.getStatus()))
                .orElseThrow(() -> new BusinessException("ACCOUNT_UNAVAILABLE", "账号不可用",
                        HttpStatus.UNAUTHORIZED));
        return issueSession(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        findToken(rawRefreshToken, "REFRESH").ifPresent(AuthToken::revoke);
    }

    public String authenticate(String rawToken) {
        return findToken(rawToken, "ACCESS").map(AuthToken::getUserId).orElse(null);
    }

    @Transactional(readOnly = true)
    public void verifyHighRiskAction(String userId, String password) {
        UserAccount user = users.findById(userId).orElseThrow(() -> BusinessException.notFound("账号不存在"));
        if (user.getEmail() != null && (password == null || user.getPasswordHash() == null
                || !passwordEncoder.matches(password, user.getPasswordHash()))) {
            throw new BusinessException("SECONDARY_VERIFICATION_FAILED", "账号密码验证失败",
                    HttpStatus.UNAUTHORIZED);
        }
    }

    @Transactional
    public void anonymize(String userId) {
        UserAccount user = users.findById(userId).orElseThrow(() -> BusinessException.notFound("账号不存在"));
        tokens.findByUserIdAndRevokedAtIsNull(userId).forEach(AuthToken::revoke);
        user.anonymize();
    }

    @Transactional(readOnly = true)
    public UserView me(String userId) {
        UserAccount user = users.findById(userId).orElseThrow();
        return view(user);
    }

    private GuestLogin issueSession(UserAccount user) {
        String accessToken = randomToken();
        String refreshToken = randomToken();
        Instant accessExpiresAt = Instant.now().plus(30, ChronoUnit.MINUTES);
        Instant refreshExpiresAt = Instant.now().plus(30, ChronoUnit.DAYS);
        tokens.save(new AuthToken(user.getId(), tokenHasher.hash(accessToken), "ACCESS", accessExpiresAt));
        tokens.save(new AuthToken(user.getId(), tokenHasher.hash(refreshToken), "REFRESH", refreshExpiresAt));
        return new GuestLogin(accessToken, accessExpiresAt, refreshToken, refreshExpiresAt, view(user));
    }

    private java.util.Optional<AuthToken> findToken(String rawToken, String tokenType) {
        if (rawToken == null || rawToken.isBlank()) return java.util.Optional.empty();
        return tokens.findByTokenHashAndTokenTypeAndRevokedAtIsNullAndExpiresAtAfter(
                tokenHasher.hash(rawToken), tokenType, Instant.now());
    }

    private UserView view(UserAccount user) {
        return new UserView(user.getId(), user.getNickname(), user.getEmail(),
                user.getMemberLevel(), entitlements.availableCredits(user.getId()));
    }

    private String randomToken() {
        byte[] bytes = new byte[32];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeNickname(String nickname) {
        if (nickname == null || nickname.isBlank()) return "体验用户";
        String trimmed = nickname.trim();
        return trimmed.length() > 80 ? trimmed.substring(0, 80) : trimmed;
    }

    private BusinessException invalidCredentials() {
        return new BusinessException("INVALID_CREDENTIALS", "邮箱或密码错误", HttpStatus.UNAUTHORIZED);
    }

    public record RegisterCommand(String email, String password, String nickname,
                                  boolean acceptTerms, boolean acceptPrivacy) {}
    public record LoginCommand(String email, String password) {}
    public record GuestLogin(String accessToken, Instant expiresAt, String refreshToken,
                             Instant refreshExpiresAt, UserView user) {}
    public record UserView(String id, String nickname, String email, String memberLevel,
                           int availableCredits) {}
}
