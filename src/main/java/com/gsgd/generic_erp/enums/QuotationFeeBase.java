package com.gsgd.generic_erp.enums;

/**
 * What a quotation fee is a percentage of.
 * <ul>
 * <li>{@code MATERIAL}: product + packaging material cost.</li>
 * <li>{@code COST}: material cost plus every MATERIAL fee (before profit).</li>
 * <li>{@code PRICE}: COST plus profit (used for tax).</li>
 * </ul>
 */
public enum QuotationFeeBase {
    MATERIAL, COST, PRICE
}
