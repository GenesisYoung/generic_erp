package com.gsgd.generic_erp.dto.purchase;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.gsgd.generic_erp.enums.PurchaseExceptionAction;
import com.gsgd.generic_erp.enums.PurchaseExceptionCategory;
import com.gsgd.generic_erp.enums.PurchaseExceptionSeverity;
import com.gsgd.generic_erp.enums.PurchaseExceptionStatus;

/**
 * A purchasing exception as the 采购异常 page sees it.
 *
 * @param allowedActions what the current user may do now
 * @param history        status changes and comments, oldest first; only in
 *                       the detail answer, empty in lists
 */
public record PurchaseExceptionView(
        Long id,
        String exceptionNo,
        Long purchaseOrderId,
        String poNo,
        String supplierName,
        PurchaseExceptionCategory category,
        PurchaseExceptionSeverity severity,
        String description,
        LocalDate impactDate,
        PurchaseExceptionStatus status,
        Long reporterId,
        String reporterName,
        Long handlerId,
        String handlerName,
        String resolution,
        LocalDateTime reportedAt,
        LocalDateTime resolvedAt,
        LocalDateTime closedAt,
        LocalDateTime updatedAt,
        long lockVersion,
        List<PurchaseExceptionAction> allowedActions,
        List<Log> history) {

    public record Log(PurchaseExceptionAction action, PurchaseExceptionStatus fromStatus,
            PurchaseExceptionStatus toStatus, Long operatorId, String operatorName, String comment,
            LocalDateTime createdAt) {
    }
}
