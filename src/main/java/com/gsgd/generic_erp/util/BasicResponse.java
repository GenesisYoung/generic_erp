package com.gsgd.generic_erp.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Standard API response envelope: an application status code, a
 * (possibly localized) message, and an arbitrary payload object.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class BasicResponse {
    private int status;
    private String message;
    private Object object;
}
