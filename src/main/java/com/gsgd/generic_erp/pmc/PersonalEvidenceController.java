package com.gsgd.generic_erp.pmc;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/pmc/evidence")
@PreAuthorize("@perm.has('menu.evidence.read')")
public class PersonalEvidenceController {
    private final PersonalEvidenceService service;

    public PersonalEvidenceController(PersonalEvidenceService service) {
        this.service = service;
    }

    @GetMapping("/metadata")
    public PersonalEvidenceService.Metadata metadata() {
        return service.metadata();
    }

    @GetMapping
    public PersonalEvidenceService.Page list(@RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int size) {
        return service.list(keyword, page, size);
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public PersonalEvidenceService.Item upload(@RequestPart MultipartFile file, @RequestParam String purpose,
            @RequestHeader("Idempotency-Key") String key) throws IOException {
        return service.upload(file, purpose, key);
    }

    @GetMapping("/{ref}/download")
    public ResponseEntity<Resource> download(@PathVariable String ref) throws IOException {
        var f = service.downloadOwn(ref);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header("Access-Control-Expose-Headers", "Content-Disposition")
                .header("X-Content-Type-Options", "nosniff").header("Cache-Control", "no-store")
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(f.fileName(), StandardCharsets.UTF_8).build().toString())
                .body(f.resource());
    }
}
