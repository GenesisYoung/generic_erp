package com.gsgd.generic_erp.entity.auth;

import java.time.LocalDate;
import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

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

    @Column(name = "password", length = 255, nullable = false)
    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY) // Allow setting but not getting the password in JSON
    private String password;

    /** true = active, false = disabled */
    @Column(name = "status")
    private Boolean status;

    /**
     * How many consecutive failed login attempts. Reset to 0 after successful
     * login.
     */
    @Column(name = "failed_attempted")
    private Byte failedAttempted;

    /** If non-null and in the future, account is locked until this moment. */
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    @Column(name = "create_date")
    private LocalDate createDate;
}
