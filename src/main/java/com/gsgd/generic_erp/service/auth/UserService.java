package com.gsgd.generic_erp.service.auth;

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.dto.UserNavMenuDTO;
import com.gsgd.generic_erp.entity.auth.NavigationMenu;
import com.gsgd.generic_erp.entity.auth.PagePermissionApprovalsRecord;
import com.gsgd.generic_erp.repository.admin.NavigationMenuRepository;
import com.gsgd.generic_erp.repository.admin.PagePermissinApprovalRecordRepository;
import com.gsgd.generic_erp.repository.admin.UserNavMenuRepository;
import com.gsgd.generic_erp.repository.auth.UserDepartmentRepository;
import com.gsgd.generic_erp.util.BasicResponse;

/**
 * User-scoped business logic — currently resolving which sidebar
 * navigation menu items are visible to a given user.
 */
@Service
public class UserService {

        private final UserNavMenuRepository userNavMenuRepository;
        private final NavigationMenuRepository navigationMenuRepository;
        private final PagePermissinApprovalRecordRepository approvalRepository;
        private final UserDepartmentRepository departmentRepository;

        UserService(UserNavMenuRepository userNavMenuRepository, NavigationMenuRepository navigationMenuRepository,
                        PagePermissinApprovalRecordRepository approvalRepository,
                        UserDepartmentRepository departmentRepository) {
                this.userNavMenuRepository = userNavMenuRepository;
                this.navigationMenuRepository = navigationMenuRepository;
                this.approvalRepository = approvalRepository;
                this.departmentRepository = departmentRepository;
        }

        /**
         * Fetches the sidebar menu for a user. A menu is visible either through a
         * direct {@code user_nav_menu} grant, or because the user holds at least one
         * permission approved for that menu; the two sources are merged and
         * de-duplicated before mapping to DTOs.
         */
        public BasicResponse fetchNavMenu(Long id) {
                Stream<Long> directNavIds = userNavMenuRepository.findByUserId(id)
                                .stream()
                                .map(ele -> ele.getNavId());
                Stream<Long> viaPermissionNavIds = approvalRepository.findByUserId(id)
                                .stream()
                                .map(PagePermissionApprovalsRecord::getMenuId);
                List<Long> navIds = Stream.concat(directNavIds, viaPermissionNavIds).distinct()
                                .collect(Collectors.toList());
                List<NavigationMenu> nav = navigationMenuRepository.findAllById(navIds);
                List<UserNavMenuDTO> menu = nav.stream().map(ele -> new UserNavMenuDTO(ele.getId(), ele.getParentId(),
                                ele.getTitleKey(), ele.getIcon(), ele.getRoute(), ele.getColor())).toList();
                return new BasicResponse(200, "Navigation Menu", menu);
        }

        public Long fetchDashboard(Long userId) {
                return departmentRepository.findByUserId(userId)
                                .stream()
                                .map(ele -> ele.getDeptId())
                                .findFirst()
                                .orElse(null);
        }

}
