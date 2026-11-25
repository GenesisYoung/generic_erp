package com.gsgd.generic_erp.service.purchase.quotation;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayInputStream;
import java.util.Locale;
import java.util.Map;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import com.gsgd.generic_erp.dto.purchase.QuotationViews.Plan;

class QuotationExcelExporterTest {

    static String allText(Sheet sh) {
        StringBuilder sb = new StringBuilder();
        for (Row r : sh) {
            r.forEach(c -> sb.append(c.toString()).append('|'));
            sb.append('\n');
        }
        return sb.toString();
    }

    @Test
    void writesBothSheetsInTheReadersLanguage() throws Exception {
        Plan plan = QuotationCalculatorTest.calc("100", Map.of(), QuotationCalculatorTest.defaults(), null);
        byte[] zh = QuotationExcelExporter.write(plan, Locale.SIMPLIFIED_CHINESE);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(zh))) {
            assertEquals(2, wb.getNumberOfSheets());
            assertEquals("报价方案", wb.getSheetAt(0).getSheetName());
            String text = allText(wb.getSheetAt(0));
            assertTrue(text.contains("产品物料小计"));
            assertTrue(text.contains("包装物料"));
            assertTrue(text.contains("管理费"));
            assertTrue(text.contains("811.25"), "total is written");
            assertTrue(text.contains("单价超高"));
            assertTrue(allText(wb.getSheetAt(1)).contains("S2 Ltd"), "every compared supplier is listed");
        }
        byte[] en = QuotationExcelExporter.write(plan, Locale.US);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(en))) {
            assertTrue(allText(wb.getSheetAt(0)).contains("Quotation total"));
        }
        assertEquals("报价方案_26394-W53.xlsx", QuotationExcelExporter.fileName(plan, Locale.SIMPLIFIED_CHINESE));
    }
}
