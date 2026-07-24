package com.interview.platform.auth;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_accounts")
public class UserAccount {
    @Id
    @Column(length = 36)
    private String id;
    @Column(nullable = false, length = 80)
    private String nickname;
    @Column(nullable = false, length = 20)
    private String status;
    @Column(length = 190)
    private String email;
    @Column(length = 100)
    private String passwordHash;
    @Column(nullable = false, length = 20)
    private String userType;
    @Column(nullable = false, length = 20)
    private String memberLevel;
    @Column(nullable = false, length = 40)
    private String timezone;
    @Column(nullable = false)
    private Instant createdAt;
    @Column(nullable = false)
    private Instant updatedAt;
    private Instant deletedAt;

    protected UserAccount() {}

    public UserAccount(String nickname) {
        this.id = UUID.randomUUID().toString();
        this.nickname = nickname;
        this.status = "ACTIVE";
        this.userType = "INDIVIDUAL";
        this.memberLevel = "FREE";
        this.timezone = "Asia/Shanghai";
        this.createdAt = Instant.now();
        this.updatedAt = createdAt;
    }

    public static UserAccount formal(String email, String passwordHash, String nickname) {
        UserAccount user = new UserAccount(nickname);
        user.email = email;
        user.passwordHash = passwordHash;
        return user;
    }

    void upgrade(String email, String passwordHash, String nickname) {
        if (this.email != null || !"ACTIVE".equals(status)) throw new IllegalStateException("NOT_GUEST");
        this.email = email;
        this.passwordHash = passwordHash;
        this.nickname = nickname;
        this.updatedAt = Instant.now();
    }

    public String getId() { return id; }
    public String getNickname() { return nickname; }
    public String getStatus() { return status; }
    public String getEmail() { return email; }
    String getPasswordHash() { return passwordHash; }
    public String getMemberLevel() { return memberLevel; }
    public Instant getCreatedAt() { return createdAt; }

    void anonymize() {
        nickname = "已注销用户";
        email = null;
        passwordHash = null;
        status = "DELETED";
        deletedAt = Instant.now();
        updatedAt = deletedAt;
    }
}
