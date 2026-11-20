package com.gsgd.generic_erp.configuration.security;

import static org.junit.jupiter.api.Assertions.*;
import java.util.*;
import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.*;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import com.gsgd.generic_erp.entity.auth.Permission;
import com.gsgd.generic_erp.repository.admin.PermissionRepository;
import com.gsgd.generic_erp.service.admin.*;

@SpringJUnitConfig(PermissionDeletionTest.Config.class)
class PermissionDeletionTest {
    @Configuration
    @EnableTransactionManagement
    @Import({ PermissionService.class, PermissionAssociationCleanup.class })
    static class Config {
        @Bean
        DataSource dataSource() {
            return new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource ds) {
            return new JdbcTemplate(ds);
        }

        @Bean
        LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var f = new LocalContainerEntityManagerFactoryBean();
            f.setDataSource(ds);
            f.setPackagesToScan("com.gsgd.generic_erp.entity.auth");
            f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto", "create-drop"));
            return f;
        }

        @Bean
        PlatformTransactionManager transactionManager(EntityManagerFactory f) {
            return new JpaTransactionManager(f);
        }

        @Bean
        PermissionRepository permissions(EntityManagerFactory f) {
            return new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(f))
                    .getRepository(PermissionRepository.class);
        }
    }

    @Autowired
    PermissionService service;
    @Autowired
    JdbcTemplate jdbc;
    static final List<String> LINKS = List.of("menu_registered_permissions_record", "page_permission_approvals_record",
            "role_permission_tb", "user_nav_menu", "action_permission_tb");

    @BeforeEach
    void seed() {
        LINKS.forEach(t -> jdbc.update("delete from " + t));
        jdbc.update("delete from permission_tb");
        jdbc.update(
                "insert into permission_tb(id,permission_code,is_enabled) values(112,'menu.evidence.read',1),(113,'menu.evidence.create',1),(114,'menu.evidence.create.child',1),(115,'menu.evidence.creator',1)");
        jdbc.update(
                "insert into menu_registered_permissions_record(menu_id,permission_id,permission_enabled,create_date) values(18,113,1,CURRENT_TIMESTAMP),(18,112,1,CURRENT_TIMESTAMP)");
        jdbc.update(
                "insert into page_permission_approvals_record(user_id,menu_id,permission_id,permission_enabled,create_date) values(320,18,113,1,CURRENT_TIMESTAMP),(320,18,112,1,CURRENT_TIMESTAMP)");
        jdbc.update(
                "insert into role_permission_tb(role_id,permission_id,created_by,create_time) values(2,113,1,CURRENT_TIMESTAMP),(2,112,1,CURRENT_TIMESTAMP)");
        jdbc.update(
                "insert into user_nav_menu(nav_id,user_id,permission_id,is_enabled,create_date) values(18,320,113,true,CURRENT_TIMESTAMP),(18,320,112,true,CURRENT_TIMESTAMP),(18,320,null,true,CURRENT_TIMESTAMP)");
        jdbc.update("insert into action_permission_tb(permission_id,action_id) values(113,1),(112,1)");
    }

    int count(String table, long id) {
        return jdbc.queryForObject("select count(*) from " + table + " where permission_id=?", Integer.class, id);
    }

    int enabled(long id) {
        return jdbc.queryForObject("select is_enabled from permission_tb where id=?", Integer.class, id);
    }

    @Test
    void exactIdDeletionRevokesAllLinksAndPreservesChildrenOtherPermissionsAndBaseline() {
        assertEquals(1, service.deleteByGroup(new Long[] { 113L, 113L }));
        assertEquals(0, enabled(113));
        assertEquals(1, enabled(114));
        LINKS.forEach(t -> {
            assertEquals(0, count(t, 113), t);
            assertEquals(1, count(t, 112), t);
        });
        assertEquals(1,
                jdbc.queryForObject("select count(*) from user_nav_menu where permission_id is null", Integer.class));
        assertEquals(1, service.deleteByGroup(new Long[] { 113L }));
    }

    @Test
    void rootDeletionOnlyMatchesCompleteSegmentsAndCleansLinks() {
        assertEquals(2, service.deleteRoots(new String[] { "menu.evidence.create" }));
        assertEquals(0, enabled(113));
        assertEquals(0, enabled(114));
        assertEquals(1, enabled(115));
        LINKS.forEach(t -> assertEquals(0, count(t, 113), t));
    }

    @Test
    void reactivatingLegacyDisabledCodeDoesNotRestoreOldGrants() {
        jdbc.update("update permission_tb set is_enabled=0 where id=113");
        service.saveOrUpdate(new Permission(null, "menu.evidence.create", 1, null));
        assertEquals(1, enabled(113));
        LINKS.forEach(t -> assertEquals(0, count(t, 113), t));
    }

    @Test
    void failedCleanupRollsBackAllEarlierLinkDeletesAndPermissionState() {
        jdbc.execute("alter table action_permission_tb rename to action_permission_missing");
        try {
            assertThrows(RuntimeException.class, () -> service.deleteByGroup(new Long[] { 113L }));
            assertEquals(1, enabled(113));
            LINKS.stream().filter(t -> !t.equals("action_permission_tb"))
                    .forEach(t -> assertEquals(1, count(t, 113), t));
        } finally {
            jdbc.execute("alter table action_permission_missing rename to action_permission_tb");
        }
    }
}
