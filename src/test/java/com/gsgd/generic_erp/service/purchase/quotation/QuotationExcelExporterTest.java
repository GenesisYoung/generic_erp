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

    @Test
    void amountsFollowUsageAndEachSubtotalHasAUnitCost() throws Exception {
        Plan plan = QuotationCalculatorTest.calc("100", Map.of(), QuotationCalculatorTest.defaults(), null);
        try (XSSFWorkbook wb = new XSSFWorkbook(new ByteArrayInputStream(
                QuotationExcelExporter.write(plan, Locale.US)))) {
            Sheet sh = wb.getSheetAt(0);
            org.apache.poi.ss.usermodel.FormulaEvaluator ev = wb.getCreationHelper().createFormulaEvaluator();
            Row a = null;
            Row unitCost = null;
            Row component = null;
            for (Row r : sh) {
                if (r.getCell(4) != null && "A".equals(r.getCell(4).toString())) {
                    a = r;
                }
                if (r.getCell(13) != null && r.getCell(13).toString().equals("Product material cost per unit")) {
                    unitCost = r;
                }
                if (component == null && r.getCell(11) != null
                        && r.getCell(11).toString().startsWith("Component cost per unit")) {
                    component = r;
                }
            }
            int n = a.getRowNum() + 1;
            assertEquals("I" + n + "*J" + n, a.getCell(10).getCellFormula(), "total qty = usage x order qty");
            assertEquals("M" + n + "*I" + n, a.getCell(13).getCellFormula(), "unit total = price x usage");
            assertEquals("K" + n + "*M" + n, a.getCell(14).getCellFormula(), "amount = total qty x price");
            assertEquals(200, ev.evaluate(a.getCell(10)).getNumberValue(), 1e-9, "2 per product x 100");
            assertEquals(3, ev.evaluate(a.getCell(13)).getNumberValue(), 1e-9, "1.50 x 2");
            assertEquals(300, ev.evaluate(a.getCell(14)).getNumberValue(), 1e-9);
            assertEquals(5.0, ev.evaluate(component.getCell(13)).getNumberValue(), 1e-9, "3 + 2 per product");
            assertEquals(100, unitCost.getCell(10).getNumericCellValue(), 1e-9, "total production qty");
            assertEquals(5.0, ev.evaluate(unitCost.getCell(14)).getNumberValue(), 1e-9, "500 / 100");
            String all = allText(sh);
            assertTrue(all.contains("Calculation per unit: product P-1 (qty 100)"));
            assertTrue(all.contains("BOM cost per unit"));
            assertTrue(allText(sh).contains("Packaging material cost per unit"));
        }
    }
}
