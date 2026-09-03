package com.gsgd.generic_erp.entity.product;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Stored-file reference mapped to {@code appendix_tb}.
 *
 * <p>This entity describes the file and its storage location, not how a product
 * uses the file.</p>
 */
@Entity
@Table(name = "appendix_tb", indexes = {
        @Index(name = "idx_appendix_storage_key", columnList = "storage_key", unique = true),
        @Index(name = "idx_appendix_file_name", columnList = "file_name")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Appendix extends ProductAuditableEntity {

    /** File-record primary key. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "appendix_id")
    private Long appendixId;

    /** Immutable opaque storage identifier, normally a UUID, used in external paths. */
    @Column(name = "storage_key", length = 64, nullable = false, unique = true, updatable = false)
    private String storageKey;

    /** File-system path or object-storage key. */
    @Column(name = "path", length = 512, nullable = false)
    private String path;

    /** Original uploaded filename, including its extension. */
    @Column(name = "file_name", length = 128, nullable = false)
    private String fileName;

    /** Lowercase file extension without the leading dot. */
    @Column(name = "file_exten", length = 8, nullable = false)
    private String fileExtension;

    /** File MIME type, for example image/webp. */
    @Column(name = "mime_type", length = 128)
    private String mimeType;

    /** File size in bytes. */
    @Column(name = "byte_size")
    private Long byteSize;

    /** Whether the file record is active. */
    @Column(name = "active", nullable = false)
    @lombok.Builder.Default
    private Boolean active = true;
}
