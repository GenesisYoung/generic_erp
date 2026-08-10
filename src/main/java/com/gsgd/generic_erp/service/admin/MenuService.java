package com.gsgd.generic_erp.service.admin;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.dto.MenuDTO;
import com.gsgd.generic_erp.entity.auth.NavigationMenu;
import com.gsgd.generic_erp.repository.admin.NavigationMenuRepository;
import com.gsgd.generic_erp.repository.admin.UserNavMenuRepository;
import com.gsgd.generic_erp.spec.MenuSpecification;
import com.gsgd.generic_erp.util.BasicPageResponse;

/**
 * CRUD operations for sidebar navigation menu items, with
 * specification-based filtering and pagination.
 */
@Service
public class MenuService {

    private NavigationMenuRepository repository;

    private UserNavMenuRepository navMenuRepository;

    public MenuService(NavigationMenuRepository m, UserNavMenuRepository navMenuRepository) {
        this.repository = m;
        this.navMenuRepository = navMenuRepository;
    }

    /** Returns a page of menu items matching the filter (enabled or not). */
    public BasicPageResponse<NavigationMenu, MenuDTO> fetchData(int page, int size, MenuDTO filter) {
        Page<NavigationMenu> m = repository.findAll(MenuSpecification.filter(filter), PageRequest.of(page, size));
        return new BasicPageResponse<NavigationMenu, MenuDTO>(transferData(m.getContent()), m);
    }

    private List<MenuDTO> transferData(List<NavigationMenu> content) {
        List<MenuDTO> result = new ArrayList<>();
        for (NavigationMenu menuDTO : content) {
            result.add(new MenuDTO(menuDTO.getId(), menuDTO.getParentId(), menuDTO.getTitleKey(), menuDTO.getIcon(),
                    menuDTO.getRoute(),
                    menuDTO.getColor(),
                    menuDTO.getPId()));
        }
        return result;
    }

    public void saveAndFlush(MenuDTO entity) {
        NavigationMenu menu = transToEntity(entity);
        repository.saveAndFlush(menu);
    }

    private NavigationMenu transToEntity(MenuDTO entity) {
        NavigationMenu m = new NavigationMenu();
        m.setId(entity.getId());
        m.setParentId(entity.getParentId());
        m.setColor(entity.getColor());
        m.setCreateTime(LocalDateTime.now());
        m.setIcon(entity.getIcon());
        m.setIsEnabled(true);
        m.setRoute(entity.getRoute());
        m.setTitleKey(entity.getTitleKey());
        m.setPId(entity.getPermission());

        return m;
    }

    /**
     * Deletes menu items in batch. User-to-menu links are removed first so no
     * orphaned {@code user_nav_menu} rows are left behind.
     */
    public void delete(Long[] ids) {
        if (ids != null) {
            navMenuRepository.deleteByNavIds(Arrays.asList(ids));
            repository.deleteAllByIdInBatch(Arrays.asList(ids));
        }
    }

    /** Same as {@link #fetchData}, restricted to enabled ("valid") menu items. */
    public BasicPageResponse<NavigationMenu, MenuDTO> fetchValidData(int page, int size, MenuDTO filter) {
        Page<NavigationMenu> m = repository.findAll(MenuSpecification.filterValid(filter), PageRequest.of(page, size));
        return new BasicPageResponse<NavigationMenu, MenuDTO>(transferData(m.getContent()), m);
    }

}
