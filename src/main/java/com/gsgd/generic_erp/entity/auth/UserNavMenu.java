package com.gsgd.generic_erp.entity.auth;

import java.time.LocalDateTime;

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
 * Maps which navigation menu items a user is allowed to access.
 * Join table between user_tb and navigation_menu_tb.
 * Maps to table: user_nav_menu
 */
@Entity
@Table(name = "user_nav_menu")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class UserNavMenu {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** FK → navigation_menu_tb.id */
    @Column(name = "nav_id", nullable = false)
    private Long navId;

    /** FK → user_tb.id */
    @Column(name = "user_id", nullable = false)
    private Long userId;

    /** Whether this menu entry is currently active for the user */
    @Column(name = "is_enabled", nullable = false)
    private Boolean isEnabled;

    @Column(name = "create_date", nullable = false, updatable = false)
    private LocalDateTime createDate;
}