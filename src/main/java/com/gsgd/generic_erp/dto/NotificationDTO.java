package com.gsgd.generic_erp.dto;

import com.gsgd.generic_erp.enums.NotifactionPriority;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class NotificationDTO extends BaseNotifacation {
    private NotifactionPriority priority; // e.g., 1 (low), 2 (medium), 3 (high)

    public NotificationDTO(String message, String timestamp, String source, Object[] appendix,
            NotifactionPriority priority) {
        super(message, timestamp, source, appendix);
        this.priority = priority;
    }

}
