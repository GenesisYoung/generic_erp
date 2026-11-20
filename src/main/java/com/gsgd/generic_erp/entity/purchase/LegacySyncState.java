package com.gsgd.generic_erp.entity.purchase;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Where each sync from the old ERP got to, so the next run only reads rows
 * changed since then. Maps to table: legacy_sync_state_tb.
 */
@Entity
@Table(name = "legacy_sync_state_tb")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class LegacySyncState {

    /** e.g. PURCHASE_ORDER. */
    @Id
    @Column(name = "sync_key", length = 50)
    private String syncKey;

    /** Highest source update time seen so far; the next run reads rows after it. */
    @Column(name = "last_source_updated_at")
    private LocalDateTime lastSourceUpdatedAt;

    @Column(name = "last_run_at")
    private LocalDateTime lastRunAt;

    /** OK or FAILED. */
    @Column(name = "last_status", length = 16)
    private String lastStatus;

    @Column(name = "last_message", length = 1000)
    private String lastMessage;

    @Column(name = "last_row_count")
    private Integer lastRowCount;
}
