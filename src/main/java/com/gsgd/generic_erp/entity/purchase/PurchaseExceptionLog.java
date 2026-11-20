package com.gsgd.generic_erp.entity.purchase;

import java.time.LocalDateTime;

import com.gsgd.generic_erp.enums.PurchaseExceptionAction;
import com.gsgd.generic_erp.enums.PurchaseExceptionStatus;

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

/** One status change or comment on a purchasing exception. Maps to table: purchase_exception_log_tb. */
@Entity
@Table(name = "purchase_exception_log_tb", indexes = {
        @Index(name = "idx_pexc_log_exception", columnList = "exception_id, created_at")
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PurchaseExceptionLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "exception_id", nullable = false)
    private Long exceptionId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action", length = 16, nullable = false)
    private PurchaseExceptionAction action;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 16)
    private PurchaseExceptionStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 16, nullable = false)
    private PurchaseExceptionStatus toStatus;

    @Column(name = "operator_id", nullable = false)
    private Long operatorId;

    @Column(name = "comment", length = 2000)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
}
