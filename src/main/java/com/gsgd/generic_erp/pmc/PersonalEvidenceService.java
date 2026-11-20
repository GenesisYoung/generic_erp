package com.gsgd.generic_erp.pmc;

import static com.gsgd.generic_erp.pmc.AuditException.require;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, rollbackFor = Exception.class)
public class PersonalEvidenceService {
    private final AuditAccess access;
    private final AuditService tasks;
    private final PersonalEvidenceRepository files;
    private final Path root;

    public PersonalEvidenceService(AuditAccess access, AuditService tasks, PersonalEvidenceRepository files,
            @Value("${pmc.order-audit.evidence-path:${spring.global.file.home_path:./files}/order-audit}") String base) {
        this.access = access;
        this.tasks = tasks;
        this.files = files;
        this.root = Path.of(base).toAbsolutePath().normalize().resolve("personal");
    }

    public record Item(String evidenceRef, String fileName, String purpose, long size, Instant uploadedAt) {
    }

    public record Page(List<Item> items, long total) {
    }

    public record Metadata(boolean canUpload) {
    }

    private Item item(PersonalEvidenceEntity e) {
        return new Item("evidence:" + e.id, e.fileName, e.purpose, e.byteSize, e.createdAt);
    }

    public Metadata metadata() {
        access.needEvidence("read");
        access.actor();
        return new Metadata(access.canEvidence("create"));
    }

    public Page list(String keyword, int page, int size) {
        access.needEvidence("read");
        var a = access.actor();
        require(page >= 1 && Set.of(20, 50, 100).contains(size) && (keyword == null || keyword.length() <= 100), 400,
                "INVALID_QUERY", "audit.error.invalidQuery");
        String term = keyword == null ? "" : keyword.trim().toLowerCase(Locale.ROOT);
        String pattern = "%" + term.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        var result = files.findAll(
                (r, q, b) -> b.and(b.equal(r.get("createdBy"), a.id()),
                        b.or(b.like(b.lower(r.get("fileName")), pattern, '\\'),
                                b.like(b.lower(r.get("purpose")), pattern, '\\'),
                                b.like(b.concat("evidence:", r.get("id")), pattern, '\\'))),
                PageRequest.of(page - 1, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"))));
        return new Page(result.getContent().stream().map(this::item).toList(), result.getTotalElements());
    }

    public Item upload(MultipartFile file, String purpose, String key) throws IOException {
        access.needEvidence("create");
        var a = access.actor();
        access.lockActor(a);
        require(key != null && key.matches("[a-zA-Z0-9-]{16,100}"), 400, "IDEMPOTENCY_KEY_REQUIRED", "audit.error.idempotencyKeyRequired");
        require(file != null && !file.isEmpty() && file.getSize() <= 20 * 1024 * 1024, 422, "INVALID_FILE",
                "audit.error.fileSize");
        purpose = AuditService.text(purpose, 1000, true, "audit.field.evidencePurpose");
        String name = AuditService.text(file.getOriginalFilename(), 128, true, "audit.field.fileName");
        require(!name.contains("/") && !name.contains("\\") && name.chars().noneMatch(c -> c < 32), 422,
                "INVALID_FILENAME", "audit.error.invalidFileName");
        byte[] bytes = file.getBytes();
        String ext = AuditEvidenceService.extension(name, bytes);
        String hash;
        try {
            var digest = MessageDigest.getInstance("SHA-256");
            digest.update((name + "\n" + purpose + "\n").getBytes(StandardCharsets.UTF_8));
            hash = HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
        String id = UUID.nameUUIDFromBytes(("personal:" + a.id() + ":" + key).getBytes(StandardCharsets.UTF_8))
                .toString();
        var old = files.findById(id);
        if (old.isPresent()) {
            require(hash.equals(old.get().requestHash), 409, "IDEMPOTENCY_CONFLICT", "audit.error.personalKeyReused");
            return item(old.get());
        }
        Files.createDirectories(root);
        Path path = root.toRealPath().resolve(id + ext);
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED)
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException e) {
                        org.slf4j.LoggerFactory.getLogger(getClass()).warn("Cannot clean rolled back evidence", e);
                    }
            }
        });
        try {
            Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            Files.deleteIfExists(path);
            throw e;
        }
        var e = new PersonalEvidenceEntity();
        e.id = id;
        e.createdBy = a.id();
        e.fileName = name;
        e.storageKey = id + ext;
        e.requestHash = hash;
        e.byteSize = bytes.length;
        e.purpose = purpose;
        e.createdAt = Instant.now();
        files.saveAndFlush(e);
        return item(e);
    }

    private PersonalEvidenceEntity find(String ref) {
        String id = ref.startsWith("evidence:") ? ref.substring(9) : ref;
        return files.findById(id).orElseThrow(() -> new AuditException(404, "NOT_FOUND", "audit.error.evidenceNotAccessible"));
    }

    public AuditEvidenceService.Download downloadOwn(String ref) throws IOException {
        access.needEvidence("read");
        var a = access.actor();
        var e = find(ref);
        require(e.createdBy.equals(a.id()), 404, "NOT_FOUND", "audit.error.evidenceNotAccessible");
        return download(e);
    }

    public AuditEvidenceService.Download downloadForTask(String taskId, String ref) throws IOException {
        var t = tasks.detail(taskId);
        access.evidence(ref, t, access.actor());
        return download(find(ref));
    }

    public AuditEvidenceService.Download downloadForRule(String ref) throws IOException {
        access.need("read");
        require(access.can("rules.configure") || access.can("rules.approve"), 403, "FORBIDDEN", "audit.error.noRuleEvidencePermission");
        var rule = tasks.getRules();
        boolean linked = rule.evidenceRefs.contains(ref) || (rule.draft != null && ref.equals(rule.draft.evidenceRef()))
                || (rule.approved != null && ref.equals(rule.approved.evidenceRef()));
        require(linked, 404, "NOT_FOUND", "audit.error.evidenceNotInRule");
        require(ref.startsWith("evidence:"), 422, "UNSUPPORTED_REFERENCE", "audit.error.legacyAttachment");
        return download(find(ref));
    }

    private AuditEvidenceService.Download download(PersonalEvidenceEntity e) throws IOException {
        require(Files.isDirectory(root), 404, "NOT_FOUND", "audit.error.evidenceFileUnavailable");
        Path base = root.toRealPath(), file = base.resolve(e.storageKey).normalize();
        require(file.startsWith(base) && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                && file.toRealPath().startsWith(base), 404, "NOT_FOUND", "audit.error.evidenceFileUnavailable");
        return new AuditEvidenceService.Download(new FileSystemResource(file), e.fileName);
    }
}
