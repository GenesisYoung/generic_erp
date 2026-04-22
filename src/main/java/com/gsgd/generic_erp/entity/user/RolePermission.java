package com.gsgd.generic_erp.entity.user;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

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

    @Column(name = "create_date")
    private LocalDate createDate;
}
