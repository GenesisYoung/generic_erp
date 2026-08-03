package com.gsgd.generic_erp.service.admin;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.dto.PermissionDTO;
import com.gsgd.generic_erp.entity.auth.Permission;
import com.gsgd.generic_erp.enums.Language_CN;
import com.gsgd.generic_erp.repository.admin.PermissionRepository;
import com.gsgd.generic_erp.spec.PermissionSpecification;
import com.gsgd.generic_erp.util.BasicPageResponse;
import com.gsgd.generic_erp.util.SimpleResponse;

/**
 * CRUD operations for {@link Permission} records. Permission codes are
 * hierarchical strings (children share their parent's code as a prefix),
 * which is what enables cascading deletion of sub-permissions.
 */
@Service
public class PermissionService {

    private final PermissionRepository permissionRepository;

    public PermissionService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    /** Returns a page of permissions, filtered by name when one is provided. */
    public BasicPageResponse<Permission, PermissionDTO> getAllPermissions(Pageable pageable, String name) {
        if (name == null || name.isEmpty()) {
            Page<Permission> page = permissionRepository.findAll(pageable);
            return new BasicPageResponse<>(transferDTO(page.getContent()), page);
        }
        Page<Permission> page = permissionRepository.findAll(PermissionSpecification.hasPermissionName(name), pageable);
        return new BasicPageResponse<>(transferDTO(page.getContent()), page);
    }

    public List<PermissionDTO> transferDTO(List<Permission> permissions) {
        List<PermissionDTO> result = new ArrayList<>();
        for (Permission p : permissions) {
            result.add(new PermissionDTO(p.getId(), p.getPermissionCode()));
        }
        return result;
    }

    /** Maps a DTO to an entity: loads the existing row when an id is present. */
    public Permission transferObj(PermissionDTO dto) {
        if (dto.getId() == null)
            return new Permission(null, dto.getPermissionCode(), null);
        else {
            Permission p = permissionRepository.findById(dto.getId()).get();
            p.setPermissionCode(dto.getPermissionCode());
            return p;
        }
    }

    /** Saves a permission, rejecting duplicate permission codes. */
    public SimpleResponse saveOrUpdate(Permission transferObj) {
        Boolean exist = permissionRepository.existsByPermissionCode(transferObj.getPermissionCode());
        if (exist) {
            return new SimpleResponse(201, Language_CN.NAME_DULICATED.getMessage());
        }
        transferObj.setCreateDate(LocalDate.now());
        permissionRepository.saveAndFlush(transferObj);
        return new SimpleResponse(200, "");
    }

    /** Deletes permissions by id; returns the number requested. */
    public long deleteByGroup(Long[] deleteVal) {
        for (Long id : deleteVal) {
            permissionRepository.deleteById(id);
        }
        return deleteVal.length;
    }

    /** Deletes permissions by exact permission code. */
    public long deleteByGroup(String[] deleteVal) {
        for (String id : deleteVal) {
            permissionRepository.deleteByPermissionCode(id);
        }
        return deleteVal.length;
    }

    /**
     * Deletes all sub-permissions of the given root codes, matched by the
     * {@code code%} prefix pattern. Called before deleting the roots themselves.
     */
    public void findAndDeleteSubPermission(String[] deleteVal) {
        Arrays.asList(deleteVal).stream()
                .forEach(ele -> {
                    List<Permission> children = permissionRepository.findChildrenPermission(ele + "%");
                    children.stream().forEach(e -> {
                        permissionRepository.deleteById(e.getId());
                    });
                });
    }
}
