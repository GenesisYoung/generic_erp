package com.gsgd.generic_erp.enums;

/**
 * One step in an exception's history (purchase_exception_log_tb.action).
 * RESOLVE proposes a resolution and sends it to PMC; APPROVE and REJECT are
 * the PMC department's answer.
 */
public enum PurchaseExceptionAction {
    REPORT, ACKNOWLEDGE, START, COMMENT, RESOLVE, APPROVE, REJECT, CLOSE, REOPEN
}
