package com.gsgd.generic_erp.service.auth;

import java.util.List;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.dto.UserNavMenuDTO;
import com.gsgd.generic_erp.entity.auth.NavigationMenu;
import com.gsgd.generic_erp.repository.auth.NavigationMenuRepository;
import com.gsgd.generic_erp.repository.auth.UserNavMenuRepository;
import com.gsgd.generic_erp.util.BasicResponse;

@Service
public class UserService {

    @Autowired
    private UserNavMenuRepository userNavMenuRepository;
    @Autowired
    private NavigationMenuRepository navigationMenuRepository;

    public BasicResponse fetchNavMenu(Long id) {
        Stream<Long> userIds = userNavMenuRepository.fetchByUserId(id)
                .stream()
                .map(ele -> ele.getNavId());
        List<NavigationMenu> nav = navigationMenuRepository.findAllById(userIds.toList());
        List<UserNavMenuDTO> menu = nav.stream().map(ele -> new UserNavMenuDTO(ele.getId(),
                ele.getTitleKey(), ele.getIcon(), ele.getRoute(), ele.getColor())).toList();
        return new BasicResponse(200, "Navigation Menu", menu);
    }
}
