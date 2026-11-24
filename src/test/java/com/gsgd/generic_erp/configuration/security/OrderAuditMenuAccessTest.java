package com.gsgd.generic_erp.configuration.security;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.data.jpa.repository.NativeQuery;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import com.gsgd.generic_erp.entity.auth.*;
import com.gsgd.generic_erp.repository.admin.*;
import com.gsgd.generic_erp.repository.auth.UserRepository;
import com.gsgd.generic_erp.service.admin.*;
import com.gsgd.generic_erp.dto.MenuDTO;

class OrderAuditMenuAccessTest {
    EmbeddedDatabase db;
    JdbcTemplate jdbc;
    String query;

    @BeforeEach
    void setup() throws Exception {
        db = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        jdbc = new JdbcTemplate(db);
        jdbc.execute("create table permission_tb(id bigint,permission_code varchar(100),is_enabled int)");
        jdbc.execute("create table role_permission_tb(role_id bigint,permission_id bigint)");
        jdbc.execute("create table user_roles_tb(user_id bigint,role_id bigint)");
        jdbc.execute("create table role_department_tb(role_id bigint,department_id bigint)");
        jdbc.execute("create table user_departments_tb(user_id bigint,dept_id bigint)");
        jdbc.execute(
                "create table page_permission_approvals_record(user_id bigint,permission_id bigint,permission_enabled int)");
        jdbc.execute("create table navigation_menu_tb(id bigint,route varchar(100),is_enabled int)");
        jdbc.execute("create table user_nav_menu(nav_id bigint,user_id bigint,permission_id bigint,is_enabled int)");
        jdbc.update(
                "insert into permission_tb values (1,'menu.order-audit-task.read',1),(2,'menu.order-audit-task.submit',1),(3,'pmc.order-audit.scope.team',1)");
        jdbc.update("insert into navigation_menu_tb values (17,'/pmc/order-audit-task',1),(18,'/another-page',1)");
        jdbc.update("insert into user_nav_menu values(17,320,null,1),(17,321,null,1)");
        query = PermissionRepository.class.getMethod("findGrantedCodesByUserId", Long.class)
                .getAnnotation(NativeQuery.class).value().replace("?1", ":userId");
    }

    @AfterEach
    void close() {
        SecurityContextHolder.clearContext();
        db.shutdown();
    }

    List<String> codes(long user) {
        return new NamedParameterJdbcTemplate(jdbc).queryForList(query, Map.of("userId", user), String.class);
    }

    void login(String... codes) {
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken("caller", null,
                Arrays.stream(codes).map(SimpleGrantedAuthority::new).toList()));
    }

    @Test
    void existingPmcBaselineGrantsYieldReadOnlyAuthorityForBothEmployees() {
        assertEquals(List.of("menu.order-audit-task.read"), codes(320));
        assertEquals(codes(320), codes(321));
        login(codes(320).toArray(String[]::new));
        PermissionChecker checker = new PermissionChecker();
        assertTrue(checker.has("menu.order-audit-task.read"));
        assertFalse(checker.has("menu.order-audit-task.submit"));
        assertFalse(checker.has("pmc.order-audit.scope.team"));
    }

    @Test
    void revokingMenuOrDisablingMenuRemovesDerivedRead() {
        jdbc.update("delete from user_nav_menu where user_id=320");
        assertTrue(codes(320).isEmpty());
        jdbc.update("update navigation_menu_tb set is_enabled=0 where id=17");
        assertTrue(codes(321).isEmpty());
    }

    @Test
    void disabledGrantAndUnrelatedMenusDoNotYieldRead() {
        jdbc.update("update user_nav_menu set is_enabled=0 where user_id=320");
        assertTrue(codes(320).isEmpty());
        jdbc.update("update user_nav_menu set nav_id=18 where user_id=321");
        assertTrue(codes(321).isEmpty());
    }

    @Test
    void disabledReadCapabilityIsNotBypassedByMenu() {
        jdbc.update("update permission_tb set is_enabled=0 where id=1");
        assertTrue(codes(320).isEmpty());
    }

    @Test
    void existingRoleAndExplicitApprovalsContinueToWorkWithoutAMenuGrant() {
        jdbc.update("delete from user_nav_menu");
        jdbc.update("insert into user_roles_tb values(320,10)");
        jdbc.update("insert into role_permission_tb values(10,1)");
        jdbc.update("insert into page_permission_approvals_record values(321,2,1)");
        assertEquals(List.of("menu.order-audit-task.read"), codes(320));
        assertEquals(List.of("menu.order-audit-task.submit"), codes(321));
    }

    @Test
    void departmentRoleGivesDefaultsAndPageOverridesWinOverThem() {
        jdbc.update("delete from user_nav_menu");
        // Department 5 has role 10 with read + submit; 320 and 321 are members.
        jdbc.update("insert into role_department_tb values(10,5)");
        jdbc.update("insert into user_departments_tb values(320,5),(321,5)");
        jdbc.update("insert into role_permission_tb values(10,1),(10,2)");
        assertEquals(List.of("menu.order-audit-task.read", "menu.order-audit-task.submit"), sorted(codes(320)));

        // 321: submit taken away on a page, team scope granted on a page.
        jdbc.update("insert into page_permission_approvals_record values(321,2,0),(321,3,1)");
        assertEquals(List.of("menu.order-audit-task.read", "pmc.order-audit.scope.team"), sorted(codes(321)));
        assertEquals(List.of("menu.order-audit-task.read", "menu.order-audit-task.submit"), sorted(codes(320)),
                "overrides are per user");

        // A user outside the department gets nothing from it.
        assertTrue(codes(999).isEmpty());
    }

    private static List<String> sorted(List<String> codes) {
        return codes.stream().sorted().toList();
    }

    @Test
    void permissionSpecificNavigationRowsDoNotCountAsBaselineGrants() {
        jdbc.update("update user_nav_menu set permission_id=2 where user_id=320");
        assertTrue(codes(320).isEmpty());
    }

    @Test
    void administratorWithoutReadCannotGrantItIndirectlyThroughMenu() {
        var links = mock(UserNavMenuRepository.class);
        var menus = mock(NavigationMenuRepository.class);
        when(menus.findById(17L)).thenReturn(
                Optional.of(NavigationMenu.builder().id(17L).route("/pmc/order-audit-task").isEnabled(true).build()));
        var service = new MenuAccessService(links, mock(UserRepository.class), mock(PermissionRepository.class),
                mock(MenuRegisteredPermissionRecordRepository.class), mock(PagePermissinApprovalRecordRepository.class),
                new PermissionChecker(), menus);
        login("menu.permissionManagement.update");
        assertEquals(403, service.setUserAccess(17L, 320L, true).getCode());
        verifyNoInteractions(links);
        login("menu.permissionManagement.update", "menu.order-audit-task.read");
        assertEquals(200, service.setUserAccess(17L, 320L, true).getCode());
        verify(links).save(any(UserNavMenu.class));
    }

    @Test
    void menuRouteCannotBeRewrittenToGrantReadOneDoesNotHold() {
        var menus = mock(NavigationMenuRepository.class);
        var service = new MenuService(menus, mock(UserNavMenuRepository.class), new PermissionChecker());
        var dto = new MenuDTO(null, null, "orderAuditTask", "mdi-clipboard-check-outline", "/pmc/order-audit-task",
                "indigo", null);
        login("menu.menuManagement.update");
        assertThrows(AccessDeniedException.class, () -> service.saveAndFlush(dto));
        verifyNoInteractions(menus);
    }

    @Test
    void personalEvidenceMenuDerivesOnlyItsOwnReadPermission() {
        jdbc.update("delete from user_nav_menu where user_id=320");
        jdbc.update("insert into permission_tb values(4,'menu.evidence.read',1),(5,'menu.evidence.create',1)");
        jdbc.update("insert into navigation_menu_tb values(19,'/pmc/evidence',1)");
        jdbc.update("insert into user_nav_menu values(19,320,null,1)");
        assertEquals(List.of("menu.evidence.read"), codes(320));
        jdbc.update("update user_nav_menu set is_enabled=0 where nav_id=19");
        assertTrue(codes(320).isEmpty());
    }

}
