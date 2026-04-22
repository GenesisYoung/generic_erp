package com.gsgd.generic_erp.entity.user;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Audit log for login attempts (both successful and failed).
 * Maps to table: user_login_log_tb
 */
@Entity
@Table(name = "user_login_log_tb")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class UserLoginLog {

    /** UUID stored as string (32 or 36 chars depending on whether dashes are kept). */
    @Id
    @Column(name = "id", length = 32)
    private String id;

    @Column(name = "user_id")
    private Long userId;

    /**
     * ⚠️ Your schema has varchar(32). Widen to varchar(45) to support IPv6.
     */
    @Column(name = "login_ip", length = 32)
    private String loginIp;

    /**
     * ⚠️ Your schema declares this as DATE. A login has a time-of-day,
     * so you should change the column to DATETIME / TIMESTAMP.
     * Keeping as LocalDateTime here assuming you'll fix the column.
     */
    @Column(name = "login_time")
    private LocalDateTime loginTime;

    /** true = login succeeded, false = login failed */
    @Column(name = "status")
    private Boolean status;

    @Column(name = "create_date")
    private LocalDate createDate;
}