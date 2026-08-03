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

@Service
public class PermissionService {

    private final PermissionRepository permissionRepository;

    public PermissionService(PermissionRepository permissionRepository) {
        this.permissionRepository = permissionRepository;
    }

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

    public Permission transferObj(PermissionDTO dto) {
        if (dto.getId() == null)
            return new Permission(null, dto.getPermissionCode(), null);
        else {
            Permission p = permissionRepository.findById(dto.getId()).get();
            p.setPermissionCode(dto.getPermissionCode());
            return p;
        }
    }

    public SimpleResponse saveOrUpdate(Permission transferObj) {
        Boolean exist = permissionRepository.existsByPermissionCode(transferObj.getPermissionCode());
        if (exist) {
            return new SimpleResponse(201, Language_CN.NAME_DULICATED.getMessage());
        }
        transferObj.setCreateDate(LocalDate.now());
        permissionRepository.saveAndFlush(transferObj);
        return new SimpleResponse(200, "");
    }

    public long deleteByGroup(Long[] deleteVal) {
        for (Long id : deleteVal) {
            permissionRepository.deleteById(id);
        }
        return deleteVal.length;
    }

    public long deleteByGroup(String[] deleteVal) {
        for (String id : deleteVal) {
            permissionRepository.deleteByPermissionCode(id);
        }
        return deleteVal.length;
    }

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
