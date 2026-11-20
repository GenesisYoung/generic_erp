package com.gsgd.generic_erp.pmc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/pmc/order-audit-tasks/rules/evidence")
@PreAuthorize("@perm.has('menu.order-audit-task.read')")
public class RuleEvidenceController {
    private final PersonalEvidenceService service;

    public RuleEvidenceController(PersonalEvidenceService service) {
        this.service = service;
    }

    @GetMapping("/{ref}/download")
    public ResponseEntity<Resource> download(@PathVariable String ref) throws IOException {
        var f = service.downloadForRule(ref);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Access-Control-Expose-Headers", "Content-Disposition")
                .header("X-Content-Type-Options", "nosniff").header("Cache-Control", "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(f.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(f.resource());
    }
}
