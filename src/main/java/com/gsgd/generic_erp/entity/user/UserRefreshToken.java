package com.gsgd.generic_erp.entity.user;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Stores hashed refresh tokens issued to users.
 * Never store the raw token — only its hash (SHA-256 recommended).
 * Maps to table: user_refresh_token_tb
 */
@Entity
@Table(name = "user_refresh_token_tb")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "tokenHash") // never log token hashes either
public class UserRefreshToken {

    /** UUID string (32 chars without dashes, 36 with). */
    @Id
    @Column(name = "id", length = 32)
    private String id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    /**
     * ⚠️ Your schema has varchar(50). SHA-256 hex is 64 chars.
     * Widen to varchar(64) or varchar(255).
     */
    @Column(name = "token_hash", length = 50, nullable = false)
    private String tokenHash;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /** true = this token has been revoked (logout, rotation, etc.) */
    @Column(name = "revoked")
    private Boolean revoked;

    @Column(name = "create_date")
    private LocalDate createDate;
}
