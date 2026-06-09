package com.gsgd.generic_erp.entity.auth;

import java.time.LocalDate;

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
 * Extended user profile information (separated from User for performance
 * and security — login queries don't need to load this).
 * Maps to table: user_info_tb
 */
@Entity
@Table(name = "user_info_tb")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class UserInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "real_name", length = 50)
    private String realName;

    @Column(name = "title", length = 100)
    private String title;

    @Column(name = "birthday")
    private LocalDate birthday;

    @Column(name = "hire_date")
    private LocalDate hireDate;

    @Column(name = "create_date")
    private LocalDate createDate;
}