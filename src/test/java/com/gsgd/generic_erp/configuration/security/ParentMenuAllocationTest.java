package com.gsgd.generic_erp.configuration.security;

import static org.junit.jupiter.api.Assertions.*;
import java.util.List;
import jakarta.persistence.EntityManagerFactory;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.jpa.repository.support.JpaRepositoryFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.orm.jpa.SharedEntityManagerCreator;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import com.gsgd.generic_erp.dto.MenuDTO;
import com.gsgd.generic_erp.repository.admin.NavigationMenuRepository;
import com.gsgd.generic_erp.spec.MenuSpecification;

@SpringJUnitConfig(PermissionDeletionTest.Config.class)
class ParentMenuAllocationTest {
    @Autowired EntityManagerFactory factory;
    @Autowired JdbcTemplate jdbc;
    @Test void allocationIncludesEnabledDirectoriesAndPagesButExcludesDisabledMenus() {
        jdbc.update("delete from navigation_menu_tb");
        jdbc.update("insert into navigation_menu_tb(id,parent_id,title_key,icon,route,color,is_enabled,create_time) values(1,null,'pmc','mdi-home','','blue',true,CURRENT_TIMESTAMP),(2,1,'audit','mdi-file','/pmc/order-audit-task','blue',true,CURRENT_TIMESTAMP),(3,null,'disabledGroup','mdi-home','','blue',false,CURRENT_TIMESTAMP),(4,1,'disabledPage','mdi-file','/disabled','blue',false,CURRENT_TIMESTAMP)");
        var repository = new JpaRepositoryFactory(SharedEntityManagerCreator.createSharedEntityManager(factory)).getRepository(NavigationMenuRepository.class);
        var filter = new MenuDTO(null, null, null, null, null, null, null);
        var rows = repository.findAll(MenuSpecification.filterValid(filter));
        assertEquals(List.of(1L,2L), rows.stream().map(m -> m.getId()).sorted().toList());
        filter.setTitleKey("pmc");
        assertEquals(1L,repository.findAll(MenuSpecification.filterValid(filter)).get(0).getId());
    }
}
