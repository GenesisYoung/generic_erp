package com.gsgd.generic_erp.service.purchase.quotation;

import com.gsgd.generic_erp.configuration.i18n.I18n;

import lombok.Getter;

/** A quotation request that cannot be served; the message is already localized. */
@Getter
public class QuotationException extends RuntimeException {

    private final int status;

    public QuotationException(int status, String key, Object... args) {
        super(I18n.t(key, args));
        this.status = status;
    }
}
