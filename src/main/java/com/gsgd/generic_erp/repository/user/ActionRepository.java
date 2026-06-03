package com.gsgd.generic_erp.repository.user;

import com.gsgd.generic_erp.entity.user.Action;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Repository for the Action entity (actions_tb).
 */
@Repository
public interface ActionRepository extends JpaRepository<Action, Long> {

    Optional<Action> findByActionName(String actionName);

    boolean existsByActionName(String actionName);
}
