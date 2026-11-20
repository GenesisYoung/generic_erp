package com.gsgd.generic_erp.entity.purchase;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * Maps a person code from the old ERP (for example a buyer code) to a user of
 * this system. Used when the code is not the same as the username.
 * Maps to table: legacy_user_map_tb.
 */
@Entity
@Table(name = "legacy_user_map_tb", uniqueConstraints = @UniqueConstraint(name = "uk_legacy_user_code", columnNames = {
        "source_system", "legacy_code" }))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class LegacyUserMap {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "source_system", length = 30, nullable = false)
    private String sourceSystem;

    @Column(name = "legacy_code", length = 50, nullable = false)
    private String legacyCode;

    @Column(name = "user_id", nullable = false)
    private Long userId;
}
