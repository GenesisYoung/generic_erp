package com.gsgd.generic_erp.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.gsgd.generic_erp.entity.product.Appendix;

public interface AppendixRepository extends JpaRepository<Appendix, Long>, JpaSpecificationExecutor<Appendix> {

    Appendix findByStorageKey(String id);

}
