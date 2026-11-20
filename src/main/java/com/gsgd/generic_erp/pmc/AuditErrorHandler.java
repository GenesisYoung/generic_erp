package com.gsgd.generic_erp.pmc;

import java.util.Map;
import java.util.UUID;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.gsgd.generic_erp.configuration.i18n.I18n;
import com.gsgd.generic_erp.controller.pmc.OrderAuditController;
import jakarta.persistence.OptimisticLockException;

@RestControllerAdvice(assignableTypes = { OrderAuditController.class, AuditEvidenceController.class,
        PersonalEvidenceController.class, RuleEvidenceController.class })
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuditErrorHandler {
    @ExceptionHandler(AuditException.class)
    public ResponseEntity<?> business(AuditException e) {
        return response(e.status, e.code, e.getMessage(), e.args);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> denied(AccessDeniedException e) {
        return response(403, "FORBIDDEN", "audit.error.accessDenied");
    }

    @ExceptionHandler({ DataIntegrityViolationException.class, OptimisticLockException.class,
            org.springframework.orm.ObjectOptimisticLockingFailureException.class })
    public ResponseEntity<?> conflict(Exception e) {
        return response(409, "CONFLICT", "audit.error.dataConflict");
    }

    @ExceptionHandler({ MissingRequestHeaderException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class })
    public ResponseEntity<?> invalid(Exception e) {
        return response(400, "INVALID_REQUEST", "audit.error.invalidRequest");
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<?> tooLarge(Exception e) {
        return response(413, "FILE_TOO_LARGE", "audit.error.fileTooLarge");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception e) {
        String trace = UUID.randomUUID().toString();
        org.slf4j.LoggerFactory.getLogger(getClass()).error("Order audit error {}", trace, e);
        return ResponseEntity.status(500)
                .body(Map.of("code", "INTERNAL_ERROR", "message", I18n.t("audit.error.unavailable"), "traceId", trace));
    }

    private ResponseEntity<?> response(int status, String code, String messageKey, Object... args) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", I18n.t(messageKey, args)));
    }
}
