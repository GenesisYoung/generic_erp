package com.gsgd.generic_erp.service.admin;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.dto.PermissionDTO;
import com.gsgd.generic_erp.entity.auth.Permission;
import com.gsgd.generic_erp.repository.auth.PermissionRepository;
import com.gsgd.generic_erp.spec.PermissionSpecification;

@Service
public class PermissionService {

    private final PermissionRepository permissionRepository;

    public PermissionService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

    public List<Permission> getAllPermissions(Pageable pageable, String name) {
        if (name == null || name.isEmpty()) {
            return permissionRepository.findAll(pageable).getContent();
        }
        return permissionRepository.findAll(PermissionSpecification.hasPermissionName(name), pageable).getContent();
    }

    public List<PermissionDTO> transferDTO(List<Permission> permissions) {
        List<PermissionDTO> result = new ArrayList<>();
        for (Permission p : permissions) {
            result.add(new PermissionDTO(p.getId(), p.getPermissionName(), p.getVal()));
        }
        return result;
    }

    public Permission transferObj(PermissionDTO dto) {
        if (dto.getId() == null)
            return new Permission(null, dto.getPermissionName(), dto.getVal(), null);
        else {
            Permission p = permissionRepository.findById(dto.getId()).get();
            p.setPermissionName(dto.getPermissionName());
            return p;
        }
    }

    public void saveOrUpdate(Permission transferObj) {
        permissionRepository.saveAndFlush(transferObj);
    }
}
