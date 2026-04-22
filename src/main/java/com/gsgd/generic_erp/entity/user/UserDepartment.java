package com.gsgd.generic_erp.entity.user;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Join table: maps users to their departments (many-to-many).
 * Maps to table: user_departments_tb
 */
@Entity
@Table(name = "user_departments_tb")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class UserDepartment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "dept_id", nullable = false)
    private Long deptId;

    @Column(name = "create_date")
    private LocalDate createDate;
}