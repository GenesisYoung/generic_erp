package com.gsgd.generic_erp.aop;

import java.time.LocalDateTime;
import java.util.Map;

import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.AfterReturning;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.interceptor.KeyGenerator;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.expression.MethodBasedEvaluationContext;
import org.springframework.core.DefaultParameterNameDiscoverer;
import org.springframework.core.ParameterNameDiscoverer;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import com.gsgd.generic_erp.annotations.AuditRecord;
import com.gsgd.generic_erp.aop.events.AuditLogEvent;
import com.gsgd.generic_erp.repository.auth.UserRepository;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;

@Aspect
@Component
@RequiredArgsConstructor
public class AuditRecordAspect {
    private Logger logger = LoggerFactory.getLogger(AuditRecordAspect.class);
    private final ApplicationEventPublisher publisher;
    private final UserRepository userRepository;
    private final ExpressionParser parser = new SpelExpressionParser();
    private final ParameterNameDiscoverer parameterNameDiscoverer = new DefaultParameterNameDiscoverer();
    private final Map<String, KeyGenerator> keyGenerators;

    @AfterReturning(pointcut = "@annotation(auditRecord)", returning = "result")
    public void afterReturning(JoinPoint joinPoint, AuditRecord auditRecord, Object result) {
        try {
            MethodSignature signature = (MethodSignature) joinPoint.getSignature();
            String targetId = null;

            try {
                if (!auditRecord.generateKey().isEmpty()) {
                    KeyGenerator generator = keyGenerators.get(auditRecord.generateKey());
                    if (generator == null)
                        throw new IllegalArgumentException(
                                "Unable to create a kegenerator, illegal argument: " + auditRecord.generateKey());
                    Object key = generator.generate(joinPoint.getTarget(), signature.getMethod(), joinPoint.getArgs());
                    if (key != null)
                        targetId = key.toString();
                    else
                        targetId = null;
                } else
                    targetId = evaluate(auditRecord.targetId(), joinPoint, signature, result);
            } catch (Exception e) {
                logger.error(
                        "Failed to build audit event for {}",
                        joinPoint.getSignature(),
                        e);
            }
            String methodName = signature.getDeclaringType().getSimpleName()
                    + "." + signature.getName();
            AuditLogEvent event = new AuditLogEvent(
                    auditRecord.module(),
                    auditRecord.action().name(),
                    targetId,
                    auditRecord.description(),
                    methodName,
                    currentUserId(),
                    currentUsername(),
                    clientIp(),
                    LocalDateTime.now());
            publisher.publishEvent(event);
        } catch (Exception e) {
            // Auditing must never break the business call that already succeeded.
            logger.error("Failed to build audit event for {}", joinPoint.getSignature(), e);
        }
    }

    /** Evaluates a SpEL expression against the method arguments and result. */
    private String evaluate(String expression, JoinPoint joinPoint,
            MethodSignature signature, Object result) {
        if (expression == null || expression.isBlank()) {
            return null;
        }
        MethodBasedEvaluationContext context = new MethodBasedEvaluationContext(
                joinPoint.getTarget(),
                signature.getMethod(),
                joinPoint.getArgs(),
                parameterNameDiscoverer);
        context.setVariable("result", result);
        Object value = parser.parseExpression(expression).getValue(context);
        return value == null ? null : value.toString();
    }

    private String currentUsername() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof UserDetails details) {
            return details.getUsername();
        }
        return null;
    }

    private Long currentUserId() {
        String username = currentUsername();
        if (username == null) {
            return null;
        }
        return userRepository.findByUsername(username).map(u -> u.getId()).orElse(null);
    }

    private String clientIp() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attrs) {
            HttpServletRequest request = attrs.getRequest();
            // X-Forwarded-For is client-controlled; behind a trusted proxy enable
            // server.forward-headers-strategy so getRemoteAddr() reflects the real client.
            return request.getRemoteAddr();
        }
        return null;
    }
}
