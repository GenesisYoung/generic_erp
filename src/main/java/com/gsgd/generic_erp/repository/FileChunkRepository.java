package com.gsgd.generic_erp.repository;

import org.springframework.data.jdbc.repository.query.Query;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

import com.gsgd.generic_erp.entity.FileChunk;

public interface FileChunkRepository extends JpaRepository<FileChunk, Long>, JpaSpecificationExecutor<FileChunk> {

    @Query("SELECT COUNT(*) FROM FileChunk f WHERE f.uuid=?1 AND f.finished=true")
    long countByUuidAndFinishedTrue(String uuid);

    FileChunk findByUuidAndChuckIdx(String uuid, Integer idx);

}
