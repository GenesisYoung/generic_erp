package com.gsgd.generic_erp.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A single uploaded file chunk, mapped to {@code file_chunk_tb}.
 */
@Entity
@Table(name = "file_chunk_tb", indexes = {
        @Index(name = "file_chunk_tb_uuid_index", columnList = "uuid")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileChunk {

    /** Chunk record primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** Identifier of the file record this chunk belongs to; references {@code appendix_tb.appendix_id}. */
    @Column(name = "appendix_id", nullable = false)
    private Long appendixId;

    /** UUID of the upload session this chunk belongs to. */
    @Column(name = "uuid", length = 36, nullable = false)
    private String uuid;

    /** Standard chunk size configured for the upload. */
    @Column(name = "standard_size")
    private Integer standardSize;

    /** Zero-based index of this chunk within the upload. */
    @Column(name = "chuck_idx")
    private Integer chuckIdx;

    /** Whether this chunk has finished uploading. */
    @Column(name = "finished")
    private Boolean finished;

    /** Creation time; defaults to the current time at the database level. */
    @Column(name = "create_time")
    private LocalDateTime createTime;

    /** Time of the last update. */
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    /** Identifier of the user who performed the upload; references {@code user_tb.id}. */
    @Column(name = "operator_id")
    private Long operatorId;
}
