package com.gsgd.generic_erp.util;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Minimal API response: an application status code and a message, no payload.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class SimpleResponse {
    private int code;
    private String message;
}
