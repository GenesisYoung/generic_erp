package com.gsgd.generic_erp.entity.user;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * An action is a concrete operation (e.g. "CREATE", "READ", "UPDATE", "DELETE",
 * or finer-grained ones like "APPROVE_PO"). Actions are attached to permissions
 * via action_permission_tb.
 * Maps to table: actions_tb
 *
 * Note: your SQL table is "actions_tb" (plural) — matching that exactly here.
 */
@Entity
@Table(name = "actions_tb")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Action {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "action_name", length = 50, nullable = false)
    private String actionName;

    @Column(name = "create_date")
    private LocalDate createDate;
}