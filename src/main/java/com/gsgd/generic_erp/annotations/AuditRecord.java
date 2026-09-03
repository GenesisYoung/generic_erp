package com.gsgd.generic_erp.annotations;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a service method whose successful execution must produce an audit row
 * in {@code audit_log_tb}.
 *
 * The audit row is written only after the surrounding transaction commits.
 * {
 * 
 * @param module      Business module, e.g. "PRODUCT", "ROLE".
 * 
 * @param action      What kind of change this method performs,e.g.
 *                    "CREATEORUPDATE",
 *                    "UPDATE","DELETE".
 * 
 * @param targetId    SpEL expression that resolves the id of the affected
 *                    record.
 *                    Method parameters are available by name (#productId) and
 *                    the
 *                    returned
 *                    value is available as #result. Example: "#result.id"
 * 
 * @param description Free-text description shown in the audit
 * 
 */
@Target({ ElementType.METHOD })
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface AuditRecord {
    /** Business module, e.g. "PRODUCT", "ROLE". */
    String module();

    /**
     * What kind of change this method performs,e.g. "CREATEORUPDATE", "UPDATE",
     * "DELETE".
     */
    AuditAction action();

    /**
     * SpEL expression that resolves the id of the affected record.
     * Method parameters are available by name (#productId) and the returned
     * value is available as #result. Example: "#result.id".
     */
    String targetId() default "";

    /** Free-text description shown in the audit UI. */
    String description() default "";
}
