package com.gsgd.generic_erp.repository.auth;

import org.springframework.data.jpa.repository.JpaRepository;

import com.gsgd.generic_erp.entity.auth.LoginLog;

/** Repository for login audit log entries (UUID-keyed). */
public interface LoginLogRepository extends JpaRepository<LoginLog, String> {

}
