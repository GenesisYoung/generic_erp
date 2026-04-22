package com.gsgd.generic_erp.entity.user;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Core user account entity.
 * Maps to table: user_tb
 */
@Entity
@Table(name = "user_tb")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = "password") // never log the password
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "username", length = 50, nullable = false, unique = true)
    private String username;

    @Column(name = "display_name", length = 50)
    private String displayName;

    @Column(name = "email", length = 50)
    private String email;

    /**
     * ⚠️ Your schema defines this as varchar(50). BCrypt hashes are exactly 60 chars.
     * You MUST widen this column to at least varchar(60), ideally varchar(255).
     */
    @Column(name = "password", length = 50, nullable = false)
    private String password;

    /** true = active, false = disabled */
    @Column(name = "status")
    private Boolean status;

    /** How many consecutive failed login attempts. Reset to 0 after successful login. */
    @Column(name = "failed_attempted")
    private Byte failedAttempted;

    /** If non-null and in the future, account is locked until this moment. */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "create_date")
    private LocalDate createDate;
}
