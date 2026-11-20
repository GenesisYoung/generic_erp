package com.gsgd.generic_erp.controller.pmc;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.gsgd.generic_erp.pmc.*;
import com.gsgd.generic_erp.pmc.AuditTypes.*;

@RestController
@RequestMapping("/api/pmc/order-audit-tasks")
@PreAuthorize("@perm.has('menu.order-audit-task.read')")
public class OrderAuditController {
    private final AuditService service;
    private final AuditJson json;

    public OrderAuditController(AuditService service, AuditJson json) {
        this.service = service;
        this.json = json;
    }

    @GetMapping("/metadata")
    public Metadata metadata() {
        return service.metadata();
    }

    @GetMapping
    public Page list(@RequestParam(required = false) String keyword, @RequestParam(required = false) String ownerUserId,
            @RequestParam(required = false) String status, @RequestParam(required = false) String dueFrom,
            @RequestParam(required = false) String dueTo, @RequestParam(required = false) String timing,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(query(keyword, ownerUserId, status, dueFrom, dueTo, timing, page, size));
    }

    private Query query(String k, String u, String s, String from, String to, String timing, int p, int size) {
        return new Query(k, u, s, time(from), time(to), timing, p, size);
    }

    private Instant time(String value) {
        try {
            return value == null || value.isBlank() ? null : Instant.parse(value);
        } catch (Exception ex) {
            throw new AuditException(400, "INVALID_TIME", "时间须使用带时区的ISO-8601格式");
        }
    }

    @GetMapping("/assignees")
    public Map<String, Object> assignees(@RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        AuditException.require(page >= 1 && List.of(20, 50, 100).contains(size) && keyword.length() <= 100, 400,
                "INVALID_QUERY", "人员查询参数无效");
        var people = service.metadata().users().stream()
                .filter(p -> p.name().contains(keyword) || p.id().equals(keyword)).toList();
        long start = (long) (page - 1) * size;
        return Map.of("items",
                people.subList((int) Math.min(start, people.size()), (int) Math.min(start + size, people.size())),
                "total", people.size());
    }

    @GetMapping("/source-orders")
    public Object sourceOrders() {
        service.metadata();
        throw new AuditException(501, "SOURCE_NOT_CONNECTED", "尚未对接旧ERP订单来源；可保存手工来源草稿，不表示原单已核对");
    }

    @GetMapping("/{id}")
    public Task detail(@PathVariable String id) {
        return service.detail(id);
    }

    @PostMapping
    public ResponseEntity<Task> create(@RequestBody String body, @RequestHeader("Idempotency-Key") String key) {
        return ResponseEntity.status(201).body(service.create(json.read(body, Draft.class), key));
    }

    @PutMapping("/{id}")
    public Task update(@PathVariable String id, @RequestBody String body,
            @RequestHeader("Idempotency-Key") String key) {
        return service.update(id, json.read(body, Draft.class), key);
    }

    @PostMapping("/{id}/actions/{action}")
    public Task act(@PathVariable String id, @PathVariable String action, @RequestBody String body,
            @RequestHeader("Idempotency-Key") String key) {
        return service.act(id, action, json.read(body, ActionInput.class), key);
    }

    @GetMapping("/{id}/submissions/{version}")
    public Submission submission(@PathVariable String id, @PathVariable int version) {
        return service.submission(id, version);
    }

    @GetMapping("/rules")
    public RuleState rules() {
        return service.getRules();
    }

    @PutMapping("/rules/{ruleId}/draft")
    public RuleState saveRule(@PathVariable String ruleId, @RequestBody String body,
            @RequestHeader("Idempotency-Key") String key) {
        return service.saveRule(ruleId, json.read(body, Rule.class), key);
    }

    @PostMapping("/rules/{ruleId}/approval")
    public RuleState approveRule(@PathVariable String ruleId, @RequestBody String body,
            @RequestHeader("Idempotency-Key") String key) {
        return service.approveRule(ruleId, json.read(body, RuleDecision.class), key);
    }

    public record Confirmation(Long lockVersion, int submissionVersion, String decision, String reason,
            String evidenceRef) {
    }

    @PostMapping("/{id}/business-confirmations")
    public Task confirm(@PathVariable String id, @RequestBody String body,
            @RequestHeader("Idempotency-Key") String key) {
        var p = json.read(body, Confirmation.class);
        AuditException.require(p != null, 400, "INVALID_BODY", "请提供确认内容");
        return service.businessConfirmation(id, p.submissionVersion(), p.decision(), p.reason(), p.evidenceRef(),
                p.lockVersion(), key);
    }

    @PostMapping("/{id}/change-requests")
    public Task change(@PathVariable String id, @RequestBody String body,
            @RequestHeader("Idempotency-Key") String key) {
        return service.requestChange(id, json.read(body, ChangeInput.class), key);
    }

    @GetMapping("/{id}/change-requests")
    public Map<String, Object> changes(@PathVariable String id, @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        AuditException.require(page >= 1 && List.of(20, 50, 100).contains(size), 400, "INVALID_PAGE", "分页参数无效");
        var changes = service.changes(id);
        long start = (long) (page - 1) * size;
        return Map.of("items",
                changes.subList((int) Math.min(start, changes.size()), (int) Math.min(start + size, changes.size())),
                "total", changes.size());
    }

    @PostMapping("/{id}/change-requests/{requestId}/decision")
    public Task decide(@PathVariable String id, @PathVariable String requestId, @RequestBody String body,
            @RequestHeader("Idempotency-Key") String key) {
        return service.decideChange(id, requestId, json.read(body, DecisionInput.class), key);
    }

    @GetMapping(value = "/export", produces = "text/csv")
    public ResponseEntity<String> export(@RequestParam(required = false) String keyword,
            @RequestParam(required = false) String ownerUserId, @RequestParam(required = false) String status,
            @RequestParam(required = false) String dueFrom, @RequestParam(required = false) String dueTo,
            @RequestParam(required = false) String timing, @RequestParam(defaultValue = "csv") String format) {
        AuditException.require("csv".equals(format), 400, "UNSUPPORTED_FORMAT", "首版仅提供CSV导出");
        return ResponseEntity.ok().header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=order-audit.csv")
                .contentType(new MediaType("text", "csv", java.nio.charset.StandardCharsets.UTF_8))
                .body(service.export(query(keyword, ownerUserId, status, dueFrom, dueTo, timing, 1, 100)));
    }
}
