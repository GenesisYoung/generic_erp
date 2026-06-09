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
 * Join table: maps permissions to their allowed actions.
 * e.g. permission "product" × action "DELETE" → you can delete products.
 * Maps to table: action_permission_tb
 */
@Entity
@Table(name = "action_permission_tb")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ActionPermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "permission_id", nullable = false)
    private Long permissionId;

    @Column(name = "action_id", nullable = false)
    private Long actionId;

    @Column(name = "create_date")
    private LocalDate createDate;
}
