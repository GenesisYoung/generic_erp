package com.gsgd.generic_erp.entity.auth;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "page_permission_approvals_record")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PagePermissionApprovalsRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "user_id", nullable = false, columnDefinition = "MEDIUMTEXT")
    private Long userId;

    @Column(name = "permission_id", nullable = false, columnDefinition = "MEDIUMTEXT")
    private Long permissionId;

    @Column(name = "menu_id", nullable = false, columnDefinition = "MEDIUMTEXT")
    private Long menuId;

    @Column(name = "create_date", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createDate;
}