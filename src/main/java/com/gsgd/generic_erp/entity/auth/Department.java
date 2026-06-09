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
