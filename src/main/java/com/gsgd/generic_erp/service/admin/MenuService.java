package com.gsgd.generic_erp.service.admin;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.dto.MenuDTO;
import com.gsgd.generic_erp.entity.auth.NavigationMenu;
import com.gsgd.generic_erp.repository.NavigationMenuRepository;
import com.gsgd.generic_erp.spec.MenuSpecification;
import com.gsgd.generic_erp.util.BasicPageResponse;

@Service
public class MenuService {

    private NavigationMenuRepository repository;

    public MenuService(NavigationMenuRepository m) {
        this.repository = m;
    }

    public BasicPageResponse<NavigationMenu, MenuDTO> fetchData(int page, int size, MenuDTO filter) {
        Page<NavigationMenu> m = repository.findAll(MenuSpecification.filter(filter), PageRequest.of(page, size));
        return new BasicPageResponse<NavigationMenu, MenuDTO>(transferData(m.getContent()), m);
    }

    private List<MenuDTO> transferData(List<NavigationMenu> content) {
        List<MenuDTO> result = new ArrayList<>();
        for (NavigationMenu menuDTO : content) {
            result.add(new MenuDTO(menuDTO.getId(), menuDTO.getTitleKey(), menuDTO.getIcon(), menuDTO.getRoute(),
                    menuDTO.getColor()));
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
        m.setColor(entity.getColor());
        m.setCreateTime(LocalDateTime.now());
        m.setIcon(entity.getIcon());
        m.setIsEnabled(true);
        m.setRoute(entity.getRoute());
        m.setTitleKey(entity.getTitleKey());
        return m;
    }

}
