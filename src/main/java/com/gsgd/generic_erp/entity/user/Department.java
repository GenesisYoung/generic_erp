package com.gsgd.generic_erp.entity.user;


import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDate;

/**
 * Department — supports a tree structure via parent_id.
 * Root departments have parent_id = null (or 0, depending on convention).
 * Maps to table: department_tb
 */
@Entity
@Table(name = "department_tb")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Department {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "dept_name", length = 50, nullable = false)
    private String deptName;

    @Column(name = "val")
    private Integer val;

    /** null if this is a root department */
    @Column(name = "parent_id")
    private Long parentId;

    @Column(name = "create_date")
    private LocalDate createDate;
}
