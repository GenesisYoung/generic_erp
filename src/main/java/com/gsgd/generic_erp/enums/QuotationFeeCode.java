package com.gsgd.generic_erp.enums;

import java.math.BigDecimal;

/**
 * The additional fees of a quotation (报价附加费用) with their default rate
 * (percent) and base, as given by the purchasing department on 2026-11-25.
 * The rates are edited on the quotation settings page.
 */
public enum QuotationFeeCode {
    SHIPPING("0.5", QuotationFeeBase.MATERIAL),
    LOADING("2.5", QuotationFeeBase.MATERIAL),
    MANAGEMENT("15", QuotationFeeBase.MATERIAL),
    MATERIAL_LOSS("1", QuotationFeeBase.MATERIAL),
    DEVELOPMENT("2", QuotationFeeBase.MATERIAL),
    /** 0 since 2026-11-26: material prices include VAT, so a tax on top would count it twice. */
    TAX("0", QuotationFeeBase.PRICE);

    private final BigDecimal defaultRate;
    private final QuotationFeeBase defaultBase;

    QuotationFeeCode(String defaultRate, QuotationFeeBase defaultBase) {
        this.defaultRate = new BigDecimal(defaultRate);
        this.defaultBase = defaultBase;
    }

    public BigDecimal defaultRate() {
        return defaultRate;
    }

    public QuotationFeeBase defaultBase() {
        return defaultBase;
    }
}
