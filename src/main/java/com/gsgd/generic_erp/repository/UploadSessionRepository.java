package com.gsgd.generic_erp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.gsgd.generic_erp.entity.UploadSession;

public interface UploadSessionRepository
                extends JpaRepository<UploadSession, Long>, JpaSpecificationExecutor<UploadSession> {

        UploadSession findByFileUuid(String id);

}
