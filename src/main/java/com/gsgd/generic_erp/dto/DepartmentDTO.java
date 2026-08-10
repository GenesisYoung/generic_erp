package com.gsgd.generic_erp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Department DTO used by the department-management list: the department's id,
 * display name, numeric value and optional parent id. The list renders
 * {@code deptName} as the title and {@code id} as the subtitle.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class DepartmentDTO {
    private Long id;
    private String deptName;
    private Integer val;
    private Long parentId;
}
