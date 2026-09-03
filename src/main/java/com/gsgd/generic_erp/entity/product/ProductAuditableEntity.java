package com.gsgd.generic_erp.entity.product;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.SuperBuilder;

/**
 * Common audit fields for product-module entities.
 *
 * <p>Except for {@link ProductAuditLog}, every table in the product module stores
 * its creator, creation time, last updater, and last update time. The service
 * layer must populate the user identifiers from the authenticated user.</p>
 */
@MappedSuperclass
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public abstract class ProductAuditableEntity {

    /** Identifier of the user who created the record. */
    @Column(name = "created_by", nullable = false, updatable = false)
    private Long createdBy;

    /** Creation time; initialized to the current time when first persisted if absent. */
    @Column(name = "create_time", nullable = false, updatable = false)
    private LocalDateTime createTime;

    /** Identifier of the last user who updated the record; null until the first update. */
    @Column(name = "updated_by")
    private Long updatedBy;

    /** Time of the last update; null until the first update. */
    @Column(name = "update_time")
    private LocalDateTime updateTime;

    /** Initializes the creation time before the first insert. */
    @PrePersist
    protected void onCreate() {
        if (createTime == null) {
            createTime = LocalDateTime.now();
        }
    }

    /** Refreshes the last-update time before each update. */
    @PreUpdate
    protected void onUpdate() {
        updateTime = LocalDateTime.now();
    }
}
