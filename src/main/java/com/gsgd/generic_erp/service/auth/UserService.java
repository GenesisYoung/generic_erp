package com.gsgd.generic_erp.service.auth;

import com.gsgd.generic_erp.configuration.i18n.I18n;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;

import com.gsgd.generic_erp.dto.UserNavMenuDTO;
import com.gsgd.generic_erp.entity.auth.NavigationMenu;
import com.gsgd.generic_erp.entity.auth.PagePermissionApprovalsRecord;
import com.gsgd.generic_erp.repository.admin.MenuRegisteredPermissionRecordRepository;
import com.gsgd.generic_erp.repository.admin.NavigationMenuRepository;
import com.gsgd.generic_erp.repository.admin.PagePermissinApprovalRecordRepository;
import com.gsgd.generic_erp.repository.admin.PermissionRepository;
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
        private final PermissionRepository permissionRepository;
        private final MenuRegisteredPermissionRecordRepository registrationRepository;

        UserService(UserNavMenuRepository userNavMenuRepository, NavigationMenuRepository navigationMenuRepository,
                        PagePermissinApprovalRecordRepository approvalRepository,
                        UserDepartmentRepository departmentRepository, PermissionRepository permissionRepository,
                        MenuRegisteredPermissionRecordRepository registrationRepository) {
                this.userNavMenuRepository = userNavMenuRepository;
                this.navigationMenuRepository = navigationMenuRepository;
                this.approvalRepository = approvalRepository;
                this.departmentRepository = departmentRepository;
                this.permissionRepository = permissionRepository;
                this.registrationRepository = registrationRepository;
        }

        /**
         * Fetches the sidebar menu for a user. A menu is visible through a direct
         * {@code user_nav_menu} grant, through a page grant on the "By Permission"
         * screen, or because the user holds (for example from their department role)
         * a permission registered on that menu. The sources are merged and
         * de-duplicated before mapping to DTOs.
         */
        public BasicResponse fetchNavMenu(Long id) {
                Stream<Long> directNavIds = userNavMenuRepository.findByUserId(id)
                                .stream()
                                .map(ele -> ele.getNavId());
                Stream<Long> viaPermissionNavIds = approvalRepository.findByUserId(id)
                                .stream()
                                .filter(a -> Byte.valueOf((byte) 1).equals(a.getPermissionEnabled()))
                                .map(PagePermissionApprovalsRecord::getMenuId);
                List<String> codes = permissionRepository.findGrantedCodesByUserId(id);
                Stream<Long> viaHeldCodeNavIds = codes.isEmpty() ? Stream.empty()
                                : registrationRepository.findMenuIdsByPermissionCodes(codes).stream();
                List<Long> navIds = Stream.concat(Stream.concat(directNavIds, viaPermissionNavIds), viaHeldCodeNavIds)
                                .distinct()
                                .collect(Collectors.toList());
                List<NavigationMenu> nav = navigationMenuRepository.findAllById(navIds);
                List<UserNavMenuDTO> menu = nav.stream().map(ele -> new UserNavMenuDTO(ele.getId(), ele.getParentId(),
                                ele.getTitleKey(), ele.getIcon(), ele.getRoute(), ele.getColor())).toList();
                return new BasicResponse(200, I18n.t("auth.navigationMenu"), menu);
        }

        public Long fetchDashboard(Long userId) {
                return departmentRepository.findByUserId(userId)
                                .stream()
                                .map(ele -> ele.getDeptId())
                                .findFirst()
                                .orElse(null);
        }

}
