package com.gsgd.generic_erp.entity.user;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * A role groups a set of permissions. Users are assigned roles.
 * Maps to table: role_tb
 */
@Entity
@Table(name = "role_tb")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "role_name", length = 50, nullable = false)
    private String roleName;

    /** Numeric value — likely used as a bitmask or priority rank. */
    @Column(name = "val")
    private Integer val;

    @Column(name = "create_date")
    private LocalDate createDate;
}
