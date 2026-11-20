package com.gsgd.generic_erp.service.admin;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Objects;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.dto.PermissionDTO;
import com.gsgd.generic_erp.entity.auth.Permission;
import com.gsgd.generic_erp.repository.admin.PermissionRepository;
import com.gsgd.generic_erp.spec.PermissionSpecification;
import com.gsgd.generic_erp.util.BasicPageResponse;
import com.gsgd.generic_erp.util.SimpleResponse;

/**
 * CRUD operations for {@link Permission} records. Permission codes are
 * hierarchical strings (children share their parent's code as a prefix), which
 * is what enables cascading deletion of sub-permissions.
 */
@Service
public class PermissionService {

    private final PermissionRepository permissionRepository;
    private final PermissionAssociationCleanup associations;

    public PermissionService(PermissionRepository permissionRepository, PermissionAssociationCleanup associations) {
        this.permissionRepository = permissionRepository;
        this.associations = associations;
    }

    /** Returns a page of permissions, filtered by name when one is provided. */
    public BasicPageResponse<Permission, PermissionDTO> getAllPermissions(Pageable pageable, String name) {
        if (name == null || name.isEmpty()) {
            Page<Permission> page = permissionRepository.findAll(PermissionSpecification.enabled(), pageable);
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
            return new Permission(null, dto.getPermissionCode(), 1, null);
        else {
            Permission p = permissionRepository.findById(dto.getId()).get();
            p.setPermissionCode(dto.getPermissionCode());
            return p;
        }
    }

    /** Saves a permission, rejecting duplicate permission codes. */
    @Transactional
    public SimpleResponse saveOrUpdate(Permission transferObj) {
        Boolean exist = permissionRepository.existsByPermissionCode(transferObj.getPermissionCode());
        if (exist) {
            Permission existingPermission = permissionRepository.findByPermissionCode(transferObj.getPermissionCode());
            if (!Integer.valueOf(1).equals(existingPermission.getIsEnabled())) {
                associations.remove(List.of(existingPermission.getId()));
            }
            permissionRepository.enable(existingPermission.getId());
            return new SimpleResponse(200, "Old Version reactivated");
        }
        transferObj.setCreateDate(LocalDate.now());
        permissionRepository.saveAndFlush(transferObj);
        return new SimpleResponse(200, "");
    }

    /**
     * Soft-delete exactly the selected ids and revoke their associations
     * atomically.
     */
    @Transactional
    public long deleteByGroup(Long[] deleteVal) {
        if (deleteVal == null)
            return 0;
        var permissions = permissionRepository
                .findAllById(Arrays.stream(deleteVal).filter(Objects::nonNull).distinct().toList());
        return deactivate(permissions);
    }

    /**
     * Legacy root endpoint: match complete dot-separated segments, never sibling
     * prefixes.
     */
    @Transactional
    public long deleteRoots(String[] roots) {
        if (roots == null)
            return 0;
        var codes = Arrays.stream(roots).filter(Objects::nonNull).filter(c -> !c.isBlank()).toList();
        var permissions = permissionRepository.findAll().stream()
                .filter(p -> codes.stream()
                        .anyMatch(c -> p.getPermissionCode().equals(c) || p.getPermissionCode().startsWith(c + ".")))
                .toList();
        return deactivate(permissions);
    }

    private long deactivate(List<Permission> permissions) {
        if (permissions.isEmpty())
            return 0;
        // Clear even previously disabled ids, making repeated deletion repair stale
        // links.
        associations.remove(permissions.stream().map(Permission::getId).toList());
        permissions.forEach(p -> p.setIsEnabled(0));
        permissionRepository.saveAllAndFlush(permissions);
        return permissions.size();
    }
}
