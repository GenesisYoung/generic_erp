package com.gsgd.generic_erp.repository.auth;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.gsgd.generic_erp.entity.auth.Action;

/**
 * Repository for the Action entity (actions_tb).
 */
@Repository
public interface ActionRepository extends JpaRepository<Action, Long> {

    Optional<Action> findByActionName(String actionName);

    boolean existsByActionName(String actionName);
}
