package com.gsgd.generic_erp.controller.purchase;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gsgd.generic_erp.configuration.i18n.I18n;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.CalculateRequest;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.Plan;
import com.gsgd.generic_erp.dto.purchase.QuotationViews.Settings;
import com.gsgd.generic_erp.service.purchase.quotation.QuotationException;
import com.gsgd.generic_erp.service.purchase.quotation.QuotationExcelExporter;
import com.gsgd.generic_erp.service.purchase.quotation.QuotationService;
import com.gsgd.generic_erp.service.purchase.quotation.QuotationSettingsService;
import com.gsgd.generic_erp.util.BasicResponse;
import com.gsgd.generic_erp.util.GlobalVariable;

import lombok.RequiredArgsConstructor;

/**
 * Purchase quotations (报价方案, {@code /api/quotations}). Reading orders and
 * plans needs {@code menu.quotation.read}; changing the fee rates and margin
 * tiers needs {@code menu.quotation.update}. The order no. travels as a query
 * parameter because old-ERP numbers may contain characters that do not fit a
 * path segment.
 */
@RestController
@RequestMapping("/api/quotations")
@RequiredArgsConstructor
public class QuotationController {

    private final QuotationService service;
    private final QuotationSettingsService settings;
    private final GlobalVariable globalVariable;

    /** Whether the old ERP is connected for quotations. */
    @PreAuthorize("@perm.has('menu.quotation.read')")
    @GetMapping("/status")
    public BasicResponse status() {
        return new BasicResponse(200, I18n.t("common.ok"), Map.of("available", service.available()));
    }

    /** Orders whose purchase plan is made and nothing is bought yet. */
    @PreAuthorize("@perm.has('menu.quotation.read')")
    @GetMapping("/orders")
    public BasicResponse orders(@RequestParam(required = false) String bom,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, defaultValue = "false") boolean refresh) {
        try {
            return new BasicResponse(200, I18n.t("common.ok"), service.orders(bom, keyword, refresh));
        } catch (QuotationException e) {
            return new BasicResponse(e.getStatus(), e.getMessage(), null);
        }
    }

    /** The quotation plan of one order; the body carries supplier choices and rate overrides. */
    @PreAuthorize("@perm.has('menu.quotation.read')")
    @PostMapping("/plan")
    public BasicResponse plan(@RequestParam String orderNo, @RequestBody(required = false) CalculateRequest body) {
        try {
            return new BasicResponse(200, I18n.t("common.ok"), service.plan(orderNo, body));
        } catch (QuotationException e) {
            return new BasicResponse(e.getStatus(), e.getMessage(), null);
        }
    }

    /** The same plan as an .xlsx download. Errors come back as a JSON envelope with a real HTTP status. */
    @PreAuthorize("@perm.has('menu.quotation.read')")
    @PostMapping("/export")
    public ResponseEntity<?> export(@RequestParam String orderNo, @RequestBody(required = false) CalculateRequest body) {
        try {
            Plan plan = service.plan(orderNo, body);
            Locale locale = I18n.currentLocale();
            byte[] xlsx = QuotationExcelExporter.write(plan, locale);
            String name = QuotationExcelExporter.fileName(plan, locale);
            String encoded = URLEncoder.encode(name, StandardCharsets.UTF_8).replace("+", "%20");
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION,
                            "attachment; filename=\"quotation.xlsx\"; filename*=UTF-8''" + encoded)
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(xlsx);
        } catch (QuotationException e) {
            HttpStatus status = HttpStatus.resolve(e.getStatus());
            return ResponseEntity.status(status == null ? HttpStatus.BAD_REQUEST : status)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new BasicResponse(e.getStatus(), e.getMessage(), null));
        }
    }

    @PreAuthorize("@perm.has('menu.quotation.read')")
    @GetMapping("/settings")
    public BasicResponse getSettings() {
        return new BasicResponse(200, I18n.t("common.ok"), settings.settings());
    }

    @PreAuthorize("@perm.has('menu.quotation.update')")
    @PutMapping("/settings")
    public BasicResponse saveSettings(@RequestBody Settings body) {
        try {
            return new BasicResponse(200, I18n.t("quotation.settings.saved"),
                    settings.update(body, globalVariable.currentUserId()));
        } catch (QuotationException e) {
            return new BasicResponse(e.getStatus(), e.getMessage(), null);
        }
    }
}
