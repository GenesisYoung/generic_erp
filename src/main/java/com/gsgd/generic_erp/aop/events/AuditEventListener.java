package com.gsgd.generic_erp.aop.events;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import com.gsgd.generic_erp.entity.product.AuditLog;
import com.gsgd.generic_erp.repository.audit.AuditLogRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class AuditEventListener {
    private static final Logger log = LoggerFactory.getLogger(AuditEventListener.class);
    private final AuditLogRepository auditLogRepository;

    @Async("auditExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onAuditEvent(AuditLogEvent event) {
        try {
            auditLogRepository.save(AuditLog.builder()
                    .module(event.module())
                    .action(event.action())
                    .targetId(event.targetId())
                    .description(event.description())
                    .methodName(event.methodName())
                    .operatorId(event.operatorId())
                    .operatorName(event.operatorName())
                    .clientIp(event.clientIp())
                    .operateTime(event.operateTime())
                    .build());
        } catch (Exception e) {
            log.error("Failed to persist audit record: {}", event, e);
        }
    }
}
