package com.gsgd.generic_erp.configuration.security;

import static org.junit.jupiter.api.Assertions.*;
import java.time.LocalDateTime;
import java.util.Map;
import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.embedded.*;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import com.gsgd.generic_erp.entity.auth.PagePermissionApprovalsRecord;
import com.gsgd.generic_erp.entity.auth.MenuRegisteredPermissionsRecord;

/** Real ORM inserts into NOT NULL columns without database timestamp defaults. */
@SpringJUnitConfig(PermissionRecordTimestampTest.Config.class)
class PermissionRecordTimestampTest {
    @Configuration static class Config {
        @Bean DataSource dataSource() { return new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build(); }
        @Bean LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds) {
            var factory=new LocalContainerEntityManagerFactoryBean(); factory.setDataSource(ds);
            factory.setPackagesToScan("com.gsgd.generic_erp.entity.auth"); factory.setJpaVendorAdapter(new HibernateJpaVendorAdapter());
            factory.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","create-drop")); return factory;
        }
        @Bean JdbcTemplate jdbc(DataSource ds) { return new JdbcTemplate(ds); }
    }
    @Autowired EntityManagerFactory factory;
    @Autowired JdbcTemplate jdbc;
    void insert(Object record) {
        try(var em=factory.createEntityManager()) {
            em.getTransaction().begin(); em.persist(record); em.flush(); em.getTransaction().commit();
        }
    }
    @Test void grantingUserPermissionPersistsCreationTimeWithoutDatabaseDefault() {
        var record=new PagePermissionApprovalsRecord(null,320L,106L,17L,(byte)1,null);
        insert(record); assertNotNull(record.getCreateDate());
        assertNotNull(jdbc.queryForObject("select create_date from page_permission_approvals_record where id=?", java.sql.Timestamp.class,record.getId()));
    }
    @Test void registeringMenuPermissionAlsoPersistsCreationTimeWithoutDatabaseDefault() {
        var record=new MenuRegisteredPermissionsRecord(null,17L,106L,(byte)1,null);
        insert(record); assertNotNull(record.getCreateDate());
        assertNotNull(jdbc.queryForObject("select create_date from menu_registered_permissions_record where id=?", java.sql.Timestamp.class,record.getId()));
    }
    @Test void explicitlySuppliedApprovalTimestampIsPreserved() {
        var time=LocalDateTime.of(2025,1,2,3,4,5); var record=new PagePermissionApprovalsRecord(null,321L,104L,17L,(byte)1,time);
        insert(record); assertEquals(time,record.getCreateDate());
        assertEquals(time,jdbc.queryForObject("select create_date from page_permission_approvals_record where id=?",java.sql.Timestamp.class,record.getId()).toLocalDateTime());
    }
    @Test void explicitlySuppliedRegistrationTimestampIsPreserved() {
        var time=LocalDateTime.of(2025,1,2,3,4,5); var record=new MenuRegisteredPermissionsRecord(null,17L,104L,(byte)1,time);
        insert(record); assertEquals(time,record.getCreateDate());
        assertEquals(time,jdbc.queryForObject("select create_date from menu_registered_permissions_record where id=?",java.sql.Timestamp.class,record.getId()).toLocalDateTime());
    }
}
