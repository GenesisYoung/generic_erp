package com.gsgd.generic_erp.entity.purchase;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

/**
 * One purchase plan type value seen on purchase order lines (or added by
 * hand), and the plan group it belongs to. Types with the same group count
 * as the same plan. A type with no group stays in the order's main follow-up
 * task. Maps to table: followup_plan_type_tb.
 */
@Entity
@Table(name = "followup_plan_type_tb", uniqueConstraints = @UniqueConstraint(name = "uk_plan_type_code", columnNames = "type_code"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class FollowUpPlanType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "type_code", length = 50, nullable = false)
    private String typeCode;

    /** Name from the old ERP (or typed by hand), shown next to the code. */
    @Column(name = "type_name", length = 100)
    private String typeName;

    /** followup_plan_group_tb.id; null = not grouped (main task). */
    @Column(name = "group_id")
    private Long groupId;

    /** Last time the sync saw a line with this type; null for a type added by hand and never seen. */
    @Column(name = "last_seen_at")
    private LocalDateTime lastSeenAt;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
