package com.gsgd.generic_erp.service.purchase.quotation;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import com.gsgd.generic_erp.configuration.i18n.I18n;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.FeeAmount;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.Item;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.OrderLine;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.OrderSummary;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.Plan;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.SupplierPrice;

/**
 * Writes a quotation plan as an .xlsx workbook in the reader's language.
 * Sheet 1: order information, order lines, product materials, packaging
 * materials, subtotals, fees, profit and totals (with any warnings). Sheet 2:
 * every supplier price that was compared, the chosen one marked.
 */
public final class QuotationExcelExporter {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ISO_LOCAL_DATE;
    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private QuotationExcelExporter() {
    }

    /** File name without characters that break a download header or a file system. */
    public static String fileName(Plan plan, Locale locale) {
        String no = plan.order().orderNo() == null ? "" : plan.order().orderNo().replaceAll("[\\\\/:*?\"<>|\\s]", "_");
        return I18n.t(locale, "quotation.excel.fileName", no) + ".xlsx";
    }

    public static byte[] write(Plan plan, Locale locale) {
        try (XSSFWorkbook wb = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Styles st = new Styles(wb);
            quotationSheet(wb.createSheet(sheetName(I18n.t(locale, "quotation.excel.sheetQuotation"))), plan,
                    locale, st);
            supplierSheet(wb.createSheet(sheetName(I18n.t(locale, "quotation.excel.sheetSuppliers"))), plan,
                    locale, st);
            wb.write(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String sheetName(String s) {
        String clean = s.replaceAll("[\\\\/?*\\[\\]:]", " ");
        return clean.length() > 31 ? clean.substring(0, 31) : clean;
    }

    private static final String[] ITEM_HEADERS = { "no", "product", "bom", "level", "materialCode", "materialName",
            "spec", "unit", "qtyPerUnit", "orderQty", "totalQty", "supplier", "unitPrice", "amount", "cheapest",
            "basePrice", "priceCheck", "priceSource", "lastPurchaseDate" };

    private static void quotationSheet(Sheet sh, Plan plan, Locale locale, Styles st) {
        int r = 0;
        OrderSummary o = plan.order();
        Row title = sh.createRow(r++);
        text(title, 0, I18n.t(locale, "quotation.excel.title", nv(o.orderNo())), st.title);
        sh.addMergedRegion(new CellRangeAddress(0, 0, 0, 8));
        r++;

        r = section(sh, r, I18n.t(locale, "quotation.excel.orderInfo"), st);
        r = pair(sh, r, I18n.t(locale, "quotation.excel.orderNo"), o.orderNo(), st);
        r = pair(sh, r, I18n.t(locale, "quotation.excel.customer"), join(o.customerCode(), o.customerName()), st);
        r = pair(sh, r, I18n.t(locale, "quotation.excel.orderDate"), date(o.orderDate()), st);
        r = pair(sh, r, I18n.t(locale, "quotation.excel.deliveryDate"), date(o.deliveryDate()), st);
        r = pair(sh, r, I18n.t(locale, "quotation.excel.planNo"), join(o.planNo(), date(o.planDate())), st);
        r = pair(sh, r, I18n.t(locale, "quotation.excel.bomStatus"), I18n.t(locale, "quotation.bom." + o.bomStatus()), st);
        r = pair(sh, r, I18n.t(locale, "quotation.excel.currency"),
                plan.currency() == null ? "" : plan.currency(), st);
        r = pair(sh, r, I18n.t(locale, "quotation.excel.generatedAt"),
                plan.generatedAt() == null ? "" : plan.generatedAt().format(DATE_TIME), st);
        r++;

        r = section(sh, r, I18n.t(locale, "quotation.excel.orderLines"), st);
        String[] lineHeaders = { "productCode", "productName", "spec", "qty", "unit", "productBom", "packagingBom",
                "lineMaterialCost", "lineTotal", "lineUnitPrice" };
        Row h = sh.createRow(r++);
        for (int c = 0; c < lineHeaders.length; c++) {
            text(h, c, I18n.t(locale, "quotation.excel.col." + lineHeaders[c]), st.header);
        }
        for (OrderLine l : plan.lines()) {
            Row row = sh.createRow(r++);
            text(row, 0, l.productCode(), st.cell);
            text(row, 1, l.productName(), st.cell);
            text(row, 2, l.spec(), st.cell);
            num(row, 3, l.qty(), st.qty);
            text(row, 4, l.unit(), st.cell);
            text(row, 5, l.productBomCode(), st.cell);
            text(row, 6, l.packagingBomCode(), st.cell);
            num(row, 7, l.materialCost(), st.money);
            num(row, 8, l.totalPrice(), st.money);
            num(row, 9, l.unitPrice(), st.price);
        }
        r++;

        r = items(sh, r, I18n.t(locale, "quotation.excel.productMaterials"), plan.productItems(),
                I18n.t(locale, "quotation.excel.productSubtotal"), plan.productMaterialCost(), locale, st);
        r++;
        r = items(sh, r, I18n.t(locale, "quotation.excel.packagingMaterials"), plan.packagingItems(),
                I18n.t(locale, "quotation.excel.packagingSubtotal"), plan.packagingMaterialCost(), locale, st);
        r++;

        r = section(sh, r, I18n.t(locale, "quotation.excel.calculation"), st);
        Row fh = sh.createRow(r++);
        String[] feeHeaders = { "item", "base", "rate", "baseAmount", "amount" };
        for (int c = 0; c < feeHeaders.length; c++) {
            text(fh, c, I18n.t(locale, "quotation.excel.col." + feeHeaders[c]), st.header);
        }
        r = amountRow(sh, r, I18n.t(locale, "quotation.excel.productSubtotal"), plan.productMaterialCost(), st, false);
        r = amountRow(sh, r, I18n.t(locale, "quotation.excel.packagingSubtotal"), plan.packagingMaterialCost(), st,
                false);
        r = amountRow(sh, r, I18n.t(locale, "quotation.excel.materialCost"), plan.materialCost(), st, true);
        List<FeeAmount> afterProfit = new ArrayList<>();
        for (FeeAmount f : plan.fees()) {
            if ("PRICE".equals(f.base())) {
                afterProfit.add(f);
            } else {
                r = feeRow(sh, r, f, locale, st);
            }
        }
        r = amountRow(sh, r, I18n.t(locale, "quotation.excel.costBeforeProfit"), plan.costBeforeProfit(), st, true);
        Row pr = sh.createRow(r++);
        text(pr, 0, I18n.t(locale, plan.marginOverridden() ? "quotation.excel.profitManual" : "quotation.excel.profit",
                plan.marginTierMin() == null ? "" : plan.marginTierMin().toPlainString()), st.cell);
        text(pr, 1, I18n.t(locale, "quotation.base.COST"), st.cell);
        pct(pr, 2, plan.marginRate(), st);
        num(pr, 3, plan.costBeforeProfit(), st.money);
        num(pr, 4, plan.profit(), st.money);
        for (FeeAmount f : afterProfit) {
            r = feeRow(sh, r, f, locale, st);
        }
        r = amountRow(sh, r, I18n.t(locale, "quotation.excel.total"), plan.totalPrice(), st, true);
        if (plan.unitPrice() != null) {
            Row ur = sh.createRow(r++);
            text(ur, 0, I18n.t(locale, "quotation.excel.unitPrice"), st.bold);
            num(ur, 4, plan.unitPrice(), st.price);
        }

        if (!plan.warnings().isEmpty()) {
            r++;
            r = section(sh, r, I18n.t(locale, "quotation.excel.warnings"), st);
            for (String w : plan.warnings()) {
                text(sh.createRow(r++), 0, w, st.cell);
            }
        }

        int[] widths = { 8, 16, 16, 8, 16, 24, 36, 8, 12, 12, 12, 26, 12, 14, 12, 12, 12, 14, 12 };
        for (int c = 0; c < widths.length; c++) {
            sh.setColumnWidth(c, widths[c] * 256);
        }
        sh.createFreezePane(0, 1);
    }

    private static int items(Sheet sh, int r, String title, List<Item> items, String subtotalLabel,
            BigDecimal subtotal, Locale locale, Styles st) {
        r = section(sh, r, title, st);
        Row h = sh.createRow(r++);
        for (int c = 0; c < ITEM_HEADERS.length; c++) {
            text(h, c, I18n.t(locale, "quotation.excel.col." + ITEM_HEADERS[c]), st.header);
        }
        int n = 1;
        for (Item i : items) {
            Row row = sh.createRow(r++);
            num(row, 0, BigDecimal.valueOf(n++), st.cell);
            text(row, 1, i.productCode(), st.cell);
            text(row, 2, i.bomCode(), st.cell);
            if (i.level() != null) {
                num(row, 3, BigDecimal.valueOf(i.level()), st.cell);
            } else {
                text(row, 3, "", st.cell);
            }
            text(row, 4, i.materialCode(), st.cell);
            text(row, 5, i.materialName(), st.cell);
            text(row, 6, i.spec(), st.cell);
            text(row, 7, i.unit(), st.cell);
            num(row, 8, i.qtyPerUnit(), st.qty);
            num(row, 9, i.orderQty(), st.qty);
            num(row, 10, i.totalQty(), st.qty);
            text(row, 11, join(i.selectedSupplierCode(), i.selectedSupplierName()), st.cell);
            num(row, 12, i.unitPrice(), st.price);
            num(row, 13, i.amount(), st.money);
            num(row, 14, i.cheapestPrice(), st.price);
            num(row, 15, i.basePrice(), st.price);
            boolean bad = !"OK".equals(i.priceCheck()) && !"NO_BASE".equals(i.priceCheck());
            text(row, 16, I18n.t(locale, "quotation.check." + i.priceCheck()), bad ? st.warn : st.cell);
            text(row, 17, i.priceSource() == null ? "" : I18n.t(locale, "quotation.source." + i.priceSource()),
                    st.cell);
            text(row, 18, date(i.lastPurchaseDate()), st.cell);
        }
        if (items.isEmpty()) {
            text(sh.createRow(r++), 0, I18n.t(locale, "quotation.excel.none"), st.cell);
        }
        Row s = sh.createRow(r++);
        text(s, 12, subtotalLabel, st.bold);
        num(s, 13, subtotal, st.moneyBold);
        return r;
    }

    private static void supplierSheet(Sheet sh, Plan plan, Locale locale, Styles st) {
        String[] headers = { "kind", "materialCode", "materialName", "supplierCode", "supplierName", "price",
                "taxRate", "currency", "validFrom", "validTo", "selected", "priceSource", "lastPurchaseDate",
                "poNo" };
        Row h = sh.createRow(0);
        for (int c = 0; c < headers.length; c++) {
            text(h, c, I18n.t(locale, "quotation.excel.col." + headers[c]), st.header);
        }
        int r = 1;
        List<Item> all = new ArrayList<>(plan.productItems());
        all.addAll(plan.packagingItems());
        java.util.Set<String> done = new java.util.HashSet<>();
        for (Item i : all) {
            if (!done.add(i.kind() + "|" + i.materialCode())) {
                continue;
            }
            if (i.suppliers().isEmpty()) {
                Row row = sh.createRow(r++);
                text(row, 0, I18n.t(locale, "quotation.kind." + i.kind()), st.cell);
                text(row, 1, i.materialCode(), st.cell);
                text(row, 2, i.materialName(), st.cell);
                text(row, 3, I18n.t(locale, "quotation.check.NO_PRICE"), st.warn);
                continue;
            }
            for (SupplierPrice p : i.suppliers()) {
                Row row = sh.createRow(r++);
                text(row, 0, I18n.t(locale, "quotation.kind." + i.kind()), st.cell);
                text(row, 1, i.materialCode(), st.cell);
                text(row, 2, i.materialName(), st.cell);
                text(row, 3, p.supplierCode(), st.cell);
                text(row, 4, p.supplierName(), st.cell);
                num(row, 5, p.price(), st.price);
                num(row, 6, p.taxRate(), st.qty);
                text(row, 7, p.currency(), st.cell);
                text(row, 8, date(p.validFrom()), st.cell);
                text(row, 9, date(p.validTo()), st.cell);
                text(row, 10, p.key() != null && p.key().equals(i.selectedKey()) ? "✓" : "", st.cell);
                text(row, 11, p.source() == null ? "" : I18n.t(locale, "quotation.source." + p.source()), st.cell);
                text(row, 12, date(p.purchaseDate()), st.cell);
                text(row, 13, p.poNo(), st.cell);
            }
        }
        int[] widths = { 10, 16, 28, 14, 28, 12, 10, 8, 12, 12, 8, 14, 12, 14 };
        for (int c = 0; c < widths.length; c++) {
            sh.setColumnWidth(c, widths[c] * 256);
        }
        sh.createFreezePane(0, 1);
    }

    private static int feeRow(Sheet sh, int r, FeeAmount f, Locale locale, Styles st) {
        Row row = sh.createRow(r++);
        text(row, 0, I18n.t(locale, "quotation.fee." + f.code()), st.cell);
        text(row, 1, I18n.t(locale, "quotation.base." + f.base()), st.cell);
        pct(row, 2, f.ratePercent(), st);
        num(row, 3, f.baseAmount(), st.money);
        num(row, 4, f.amount(), st.money);
        return r;
    }

    private static int amountRow(Sheet sh, int r, String label, BigDecimal amount, Styles st, boolean bold) {
        Row row = sh.createRow(r++);
        text(row, 0, label, bold ? st.bold : st.cell);
        num(row, 4, amount, bold ? st.moneyBold : st.money);
        return r;
    }

    private static int section(Sheet sh, int r, String title, Styles st) {
        text(sh.createRow(r), 0, title, st.section);
        return r + 1;
    }

    private static int pair(Sheet sh, int r, String label, String value, Styles st) {
        Row row = sh.createRow(r);
        text(row, 0, label, st.bold);
        text(row, 1, value, st.plain);
        sh.addMergedRegion(new CellRangeAddress(r, r, 1, 5));
        return r + 1;
    }

    private static void text(Row row, int col, String value, CellStyle style) {
        Cell c = row.createCell(col);
        c.setCellValue(value == null ? "" : value);
        c.setCellStyle(style);
    }

    private static void num(Row row, int col, BigDecimal value, CellStyle style) {
        Cell c = row.createCell(col);
        if (value != null) {
            c.setCellValue(value.doubleValue());
        }
        c.setCellStyle(style);
    }

    /** Rates are kept as percent (2.5) and written as a fraction (0.025) with a % format. */
    private static void pct(Row row, int col, BigDecimal ratePercent, Styles st) {
        num(row, col, ratePercent == null ? null : ratePercent.movePointLeft(2), st.percent);
    }

    private static String date(LocalDate d) {
        return d == null ? "" : d.format(DATE);
    }

    private static String join(String a, String b) {
        if (a == null || a.isBlank()) {
            return nv(b);
        }
        return b == null || b.isBlank() ? a : a + " " + b;
    }

    private static String nv(String s) {
        return s == null ? "" : s;
    }

    private static final class Styles {
        final CellStyle title;
        final CellStyle section;
        final CellStyle header;
        final CellStyle cell;
        final CellStyle plain;
        final CellStyle bold;
        final CellStyle warn;
        final CellStyle qty;
        final CellStyle price;
        final CellStyle money;
        final CellStyle moneyBold;
        final CellStyle percent;

        Styles(XSSFWorkbook wb) {
            Font titleFont = wb.createFont();
            titleFont.setBold(true);
            titleFont.setFontHeightInPoints((short) 14);
            Font boldFont = wb.createFont();
            boldFont.setBold(true);
            Font redFont = wb.createFont();
            redFont.setColor(IndexedColors.RED.getIndex());

            title = wb.createCellStyle();
            title.setFont(titleFont);
            section = wb.createCellStyle();
            section.setFont(boldFont);
            plain = wb.createCellStyle();
            bold = bordered(wb);
            bold.setFont(boldFont);
            header = bordered(wb);
            header.setFont(boldFont);
            header.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
            header.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            header.setWrapText(true);
            cell = bordered(wb);
            warn = bordered(wb);
            warn.setFont(redFont);
            qty = numeric(wb, "#,##0.####");
            price = numeric(wb, "#,##0.0000");
            money = numeric(wb, "#,##0.00");
            moneyBold = numeric(wb, "#,##0.00");
            moneyBold.setFont(boldFont);
            percent = numeric(wb, "0.00%");
        }

        private static CellStyle bordered(XSSFWorkbook wb) {
            CellStyle s = wb.createCellStyle();
            s.setBorderBottom(BorderStyle.THIN);
            s.setBorderTop(BorderStyle.THIN);
            s.setBorderLeft(BorderStyle.THIN);
            s.setBorderRight(BorderStyle.THIN);
            return s;
        }

        private static CellStyle numeric(XSSFWorkbook wb, String format) {
            CellStyle s = bordered(wb);
            s.setDataFormat(wb.createDataFormat().getFormat(format));
            return s;
        }
    }
}
