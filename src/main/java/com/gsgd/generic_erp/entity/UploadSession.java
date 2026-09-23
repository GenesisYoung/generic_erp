package com.gsgd.generic_erp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * A chunked-upload session tracking overall progress, mapped to
 * {@code upload_session}.
 */
@Entity
@Table(name = "upload_session", indexes = {
        @Index(name = "upload_session_file_uuid_index", columnList = "file_uuid")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UploadSession {

    /** Upload session primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    /** UUID identifying the file being uploaded across its chunks. */
    @Column(name = "file_uuid", length = 36, nullable = false)
    private String fileUuid;

    @Column(name = "appendix_id")
    private Long appendixId;

    /** Total size of the file being uploaded, in bytes. */
    @Column(name = "total_size", nullable = false)
    private Long totalSize;

    /** Index of the next chunk expected by the server. */
    @Builder.Default
    @Column(name = "current_idx", nullable = false)
    private Integer currentIdx = 0;

    /** Chunk size used for this upload. */
    @Column(name = "chuck_size", nullable = false)
    private Long chunkSize;

    /** Current status of the upload session. */
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(name = "status")
    private UploadSessionStatus status = UploadSessionStatus.UPLOADING;
}
