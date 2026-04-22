package com.gsgd.generic_erp.entity.user;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

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
