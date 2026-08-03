package com.gsgd.generic_erp.util;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/** Minimal API response: an application status code and a message, no payload. */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class SimpleResponse {
    private int code;
    private String message;
}
