package com.gsgd.generic_erp.pmc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/pmc/order-audit-tasks/{id}/evidence")
@PreAuthorize("@perm.has('menu.order-audit-task.read')")
public class AuditEvidenceController {
    private final AuditEvidenceService service;

    public AuditEvidenceController(AuditEvidenceService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public AuditEvidenceService.Receipt upload(@PathVariable String id, @RequestPart MultipartFile file,
            @RequestParam String purpose, @RequestParam long lockVersion, @RequestHeader("Idempotency-Key") String key)
            throws IOException {
        return service.upload(id, file, purpose, lockVersion, key);
    }

    @GetMapping("/{ref}/download")
    public ResponseEntity<Resource> download(@PathVariable String id, @PathVariable String ref) throws IOException {
        var file = service.download(id, ref);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("X-Content-Type-Options", "nosniff")
                .header("Access-Control-Expose-Headers", "Content-Disposition").header("Cache-Control", "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(file.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(file.resource());
    }
}
