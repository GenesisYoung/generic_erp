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
import com.gsgd.generic_erp.controller.pmc.OrderAuditController;
import jakarta.persistence.OptimisticLockException;

@RestControllerAdvice(assignableTypes = { OrderAuditController.class, AuditEvidenceController.class,
        PersonalEvidenceController.class, RuleEvidenceController.class })
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuditErrorHandler {
    @ExceptionHandler(AuditException.class)
    public ResponseEntity<?> business(AuditException e) {
        return response(e.status, e.code, e.getMessage());
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<?> denied(AccessDeniedException e) {
        return response(403, "FORBIDDEN", "没有访问权限");
    }

    @ExceptionHandler({ DataIntegrityViolationException.class, OptimisticLockException.class,
            org.springframework.orm.ObjectOptimisticLockingFailureException.class })
    public ResponseEntity<?> conflict(Exception e) {
        return response(409, "CONFLICT", "数据已更新或订单轮次重复，请刷新核对");
    }

    @ExceptionHandler({ MissingRequestHeaderException.class,
            org.springframework.web.multipart.support.MissingServletRequestPartException.class,
            org.springframework.web.bind.MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            org.springframework.http.converter.HttpMessageNotReadableException.class })
    public ResponseEntity<?> invalid(Exception e) {
        return response(400, "INVALID_REQUEST", "请求参数、幂等键或内容不合法");
    }

    @ExceptionHandler(org.springframework.web.multipart.MaxUploadSizeExceededException.class)
    public ResponseEntity<?> tooLarge(Exception e) {
        return response(413, "FILE_TOO_LARGE", "文件超过上传大小限制");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> unexpected(Exception e) {
        String trace = UUID.randomUUID().toString();
        org.slf4j.LoggerFactory.getLogger(getClass()).error("Order audit error {}", trace, e);
        return ResponseEntity.status(500)
                .body(Map.of("code", "INTERNAL_ERROR", "message", "系统暂时不可用", "traceId", trace));
    }

    private ResponseEntity<?> response(int status, String code, String message) {
        return ResponseEntity.status(status).body(Map.of("code", code, "message", message));
    }
}
