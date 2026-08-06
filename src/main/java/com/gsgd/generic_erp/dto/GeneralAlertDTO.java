package com.gsgd.generic_erp.dto;

import com.gsgd.generic_erp.enums.AlertPriority;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GeneralAlertDTO extends BaseNotifacation {
    private AlertPriority type; // e.g., "info", "warning", "error"

    public GeneralAlertDTO(String message, String timestamp, String source, Object[] appendix, AlertPriority type) {
        super(message, timestamp, source, appendix);
        this.type = type;
    }
}
