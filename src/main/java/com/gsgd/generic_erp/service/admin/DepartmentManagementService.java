package com.gsgd.generic_erp.service.admin;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.configuration.exception.NoneDeleteableException;
import com.gsgd.generic_erp.dto.DepartmentDTO;
import com.gsgd.generic_erp.dto.RoleGrantDTO;
import com.gsgd.generic_erp.entity.auth.Department;
import com.gsgd.generic_erp.entity.auth.Role;
import com.gsgd.generic_erp.entity.auth.RoleDepartment;
import com.gsgd.generic_erp.enums.Language_CN;
import com.gsgd.generic_erp.repository.admin.DepartmentRepository;
import com.gsgd.generic_erp.repository.auth.RoleDepartmentRepository;
import com.gsgd.generic_erp.repository.auth.RoleRepository;
import com.gsgd.generic_erp.repository.auth.UserDepartmentRepository;
import com.gsgd.generic_erp.util.BasicPageResponse;
import com.gsgd.generic_erp.util.BasicResponse;
import com.gsgd.generic_erp.util.SimpleResponse;

import jakarta.transaction.Transactional;

/**
 * Backs the "Department Management" screen (department &harr; role), mirroring
 * {@link RoleManagementService}.
 *
 * <ul>
 * <li>Department list: paginated CRUD. A department cannot be deleted while any
 * user is still assigned to it ({@code user_departments_tb}); deleting an
 * unused
 * department first clears its role links so no orphaned join rows remain.</li>
 * <li>Associated roles: for a selected department, every role is listed
 * (paginated) flagged with whether a {@code role_department_tb} link already
 * exists ("Processed" vs "Not granted"), and that link can be toggled.</li>
 * </ul>
 */
@Service
public class DepartmentManagementService {

    private final DepartmentRepository departmentRepository;
    private final UserDepartmentRepository userDepartmentRepository;
    private final RoleRepository roleRepository;
    private final RoleDepartmentRepository roleDepartmentRepository;

    public DepartmentManagementService(DepartmentRepository departmentRepository,
            UserDepartmentRepository userDepartmentRepository, RoleRepository roleRepository,
            RoleDepartmentRepository roleDepartmentRepository) {
        this.departmentRepository = departmentRepository;
        this.userDepartmentRepository = userDepartmentRepository;
        this.roleRepository = roleRepository;
        this.roleDepartmentRepository = roleDepartmentRepository;
    }

    /** Returns a page of departments mapped to {@link DepartmentDTO}. */
    public BasicPageResponse<Department, DepartmentDTO> fetchDepartments(Pageable pageable) {
        Page<Department> page = departmentRepository.findAll(pageable);
        List<DepartmentDTO> dto = page.getContent().stream()
                .map(d -> new DepartmentDTO(d.getId(), d.getDeptName(), d.getVal(), d.getParentId()))
                .toList();
        return new BasicPageResponse<>(dto, page);
    }

    /** Creates a department (id == null) or renames/updates an existing one. */
    public SimpleResponse saveOrUpdate(DepartmentDTO dto) {
        if (dto.getId() == null) {
            if (departmentRepository.existsByDeptName(dto.getDeptName())) {
                return new SimpleResponse(201, Language_CN.NAME_DULICATED.getMessage());
            }
            departmentRepository.save(Department.builder()
                    .deptName(dto.getDeptName())
                    .val(dto.getVal())
                    .parentId(dto.getParentId())
                    .createDate(LocalDate.now())
                    .build());
            return new SimpleResponse(200, "");
        }
        Department dept = departmentRepository.findById(dto.getId()).orElseThrow();
        dept.setDeptName(dto.getDeptName());
        dept.setVal(dto.getVal());
        dept.setParentId(dto.getParentId());
        departmentRepository.save(dept);
        return new SimpleResponse(200, "");
    }

    /**
     * Deletes a department, but only when no user is still assigned to it. The
     * department's role links are removed first to satisfy the foreign key in
     * {@code role_department_tb}.
     */
    @Transactional
    public SimpleResponse deleteDepartment(Long deptId) throws NoneDeleteableException {
        if (!userDepartmentRepository.findByDeptId(deptId).isEmpty()) {
            throw new NoneDeleteableException(Language_CN.DEPT_ASSIGNED.getMessage());
        }
        roleDepartmentRepository.deleteByDepartmentId(deptId);
        departmentRepository.deleteById(deptId);
        return new SimpleResponse(200, "");
    }

    /**
     * Every role (paginated), each flagged with whether the given department is
     * linked to it via {@code role_department_tb}.
     */
    public BasicPageResponse<Role, RoleGrantDTO> listRolesForDepartment(Long deptId, Pageable pageable) {
        Set<Long> grantedRoleIds = roleDepartmentRepository.findByDepartmentId(deptId).stream()
                .map(RoleDepartment::getRoleId)
                .collect(Collectors.toSet());
        Page<Role> page = roleRepository.findAll(pageable);
        List<RoleGrantDTO> dto = page.getContent().stream()
                .map(r -> new RoleGrantDTO(r.getId(), r.getRoleName(), grantedRoleIds.contains(r.getId())))
                .toList();
        return new BasicPageResponse<>(dto, page);
    }

    /** Links or unlinks a single role to a department (idempotent). */
    @Transactional
    public SimpleResponse setDepartmentRole(Long deptId, Long roleId, boolean granted) {
        boolean exists = roleDepartmentRepository.existsByRoleIdAndDepartmentId(roleId, deptId);
        if (granted) {
            if (!exists) {
                roleDepartmentRepository.save(RoleDepartment.builder()
                        .roleId(roleId)
                        .departmentId(deptId)
                        .build());
            }
        } else if (exists) {
            roleDepartmentRepository.deleteByRoleIdAndDepartmentId(roleId, deptId);
        }
        return new SimpleResponse(200, "");
    }

    public BasicResponse fetDepts() {
        return new BasicResponse(
                200,
                "Success",
                departmentRepository.findAll().stream().map(ele -> new DeptOptions(ele.getId(), ele.getDeptName())));
    }
}

record DeptOptions(Long val, String title) {
};
