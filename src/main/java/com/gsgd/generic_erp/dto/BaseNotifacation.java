package com.gsgd.generic_erp.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class BaseNotifacation {
    private String message; // The alert message to be displayed
    private String timestamp; // ISO 8601 format
    private String source; // The source of the alert, e.g., "System", "User", etc.
    private Object[] appendix; // Additional data related to the alert, can be any object
}
