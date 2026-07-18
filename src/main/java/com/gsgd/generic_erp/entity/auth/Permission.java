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
 * A permission represents a protectable resource or capability
 * (e.g. "product.view", "order.delete").
 * Maps to table: permission_tb
 */
@Entity
@Table(name = "permission_tb")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Permission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "permission_name", length = 50, nullable = false)
    private String permissionName;

    /** Numeric value — often used as a bitmask for fast permission checks. */
    @Column(name = "val")
    private Long val;

    @Column(name = "create_date")
    private LocalDate createDate;
}