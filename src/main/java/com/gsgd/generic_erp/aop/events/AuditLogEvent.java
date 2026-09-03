package com.gsgd.generic_erp.aop.events;

import java.time.LocalDateTime;

public record AuditLogEvent(
                String module,
                String action,
                String targetId,
                String description,
                String methodName,
                Long operatorId,
                String operatorName,
                String clientIp,
                LocalDateTime operateTime) {
}
