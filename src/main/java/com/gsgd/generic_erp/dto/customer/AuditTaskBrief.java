package com.gsgd.generic_erp.dto.customer;

/** The few fields of an order audit task a customer order list needs. */
public record AuditTaskBrief(String id, String orderNumber, int round, String status) {
}
