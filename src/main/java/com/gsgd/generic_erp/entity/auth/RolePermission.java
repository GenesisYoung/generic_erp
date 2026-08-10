package com.gsgd.generic_erp.entity.auth;

import java.time.LocalDate;
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
 * Join table: maps roles to their permissions (many-to-many).
 * Maps to table: role_permission_tb
 */
@Entity
@Table(name = "role_permission_tb")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class RolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "role_id", nullable = false)
    private Long roleId;

    @Column(name = "permission_id", nullable = false)
    private Long permissionId;

    /** Optional menu scope for the grant; null for role-wide grants. */
    @Column(name = "menu_id")
    private Long menuId;

    /** Id of the user who created this grant (audit column, NOT NULL in DB). */
    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    @Column(name = "create_date")
    private LocalDate createDate;
}
