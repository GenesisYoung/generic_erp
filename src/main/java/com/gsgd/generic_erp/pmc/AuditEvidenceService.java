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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.*;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional(isolation = org.springframework.transaction.annotation.Isolation.READ_COMMITTED, rollbackFor = Exception.class)
public class AuditEvidenceService {
    private final AuditService tasks;
    private final AuditAccess access;
    private final AuditEvidenceRepository evidence;
    private final Path root;
    private final PersonalEvidenceService personal;

    public AuditEvidenceService(AuditService tasks, AuditAccess access, AuditEvidenceRepository evidence,
            PersonalEvidenceService personal,
            @Value("${pmc.order-audit.evidence-path:${spring.global.file.home_path:./files}/order-audit}") String path) {
        this.tasks = tasks;
        this.personal = personal;
        this.access = access;
        this.evidence = evidence;
        this.root = Path.of(path).toAbsolutePath().normalize();
    }

    public record Receipt(String evidenceRef, String fileName, long size, Instant uploadedAt) {
    }

    public Receipt upload(String taskId, MultipartFile file, String purpose, long lockVersion, String key)
            throws IOException {
        access.need("read");
        var a = access.actor();
        access.lockActor(a);
        var t = tasks.detail(taskId);
        require(access.executes(t, a) || a.id().equals(t.createdBy), 403, "FORBIDDEN", "不能向该任务上传凭证");
        require(access.can("update") || access.can("submit") || access.can("verify") || access.can("rules.configure")
                || access.can("rules.approve") || access.can("approve") || access.can("confirm")
                || access.can("change.request"), 403, "FORBIDDEN", "没有凭证上传权限");
        require(key != null && key.matches("[a-zA-Z0-9-]{16,100}"), 400, "IDEMPOTENCY_KEY_REQUIRED", "请提供有效幂等键");
        require(file != null && !file.isEmpty() && file.getSize() <= 20 * 1024 * 1024, 422, "INVALID_FILE",
                "凭证须为非空且不超过20MB的文件");
        purpose = AuditService.text(purpose, 1000, true, "凭证用途");
        String name = AuditService.text(file.getOriginalFilename(), 128, true, "文件名");
        require(!name.contains("/") && !name.contains("\\") && name.chars().noneMatch(c -> c < 32), 422,
                "INVALID_FILENAME", "文件名不合法");
        byte[] bytes = file.getBytes();
        String ext = extension(name, bytes);
        String fingerprint;
        try {
            fingerprint = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(concat(
                    (taskId + "\n" + name + "\n" + purpose + "\n" + lockVersion).getBytes(StandardCharsets.UTF_8),
                    bytes)));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
        String id = UUID.nameUUIDFromBytes((a.id() + ":" + key).getBytes(StandardCharsets.UTF_8)).toString();
        var old = evidence.findById(id);
        if (old.isPresent()) {
            require(old.get().requestHash.equals(fingerprint), 409, "IDEMPOTENCY_CONFLICT", "同一上传幂等键不可用于不同内容");
            return receipt(old.get());
        }
        AuditService.version(lockVersion, t);
        Files.createDirectories(root);
        Path actualRoot = root.toRealPath();
        Path path = actualRoot.resolve(id + ext);

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status != STATUS_COMMITTED)
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ex) {
                        org.slf4j.LoggerFactory.getLogger(getClass()).warn("Could not clean rolled-back audit evidence",
                                ex);
                    }
            }
        });
        try {
            Files.write(path, bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException ex) {
            Files.deleteIfExists(path);
            throw ex;
        }
        var e = new AuditEvidenceEntity();
        e.id = id;
        e.taskId = taskId;
        e.createdBy = a.id();
        e.fileName = name;
        e.storageKey = id + ext;
        e.requestHash = fingerprint;
        e.byteSize = bytes.length;
        e.purpose = purpose;
        e.createdAt = Instant.now();
        evidence.saveAndFlush(e);
        return receipt(e);
    }

    private byte[] concat(byte[] a, byte[] b) {
        byte[] v = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, v, a.length, b.length);
        return v;
    }

    private Receipt receipt(AuditEvidenceEntity e) {
        return new Receipt("audit:" + e.id, e.fileName, e.byteSize, e.createdAt);
    }

    static String extension(String name, byte[] b) {
        String n = name.toLowerCase(Locale.ROOT);
        if (n.endsWith(".pdf") && b.length > 4 && new String(b, 0, 5, StandardCharsets.US_ASCII).equals("%PDF-"))
            return ".pdf";
        if (n.endsWith(".png") && b.length >= 8
                && Arrays.equals(Arrays.copyOf(b, 8), new byte[] { (byte) 137, 80, 78, 71, 13, 10, 26, 10 }))
            return ".png";
        if ((n.endsWith(".jpg") || n.endsWith(".jpeg")) && b.length >= 3 && b[0] == (byte) 255 && b[1] == (byte) 216
                && b[2] == (byte) 255)
            return ".jpg";
        if (n.endsWith(".txt")) {
            try {
                StandardCharsets.UTF_8.newDecoder().decode(java.nio.ByteBuffer.wrap(b));
                require(new String(b, StandardCharsets.UTF_8).indexOf('\0') < 0, 422, "INVALID_FILE", "文本文件含无效字符");
                return ".txt";
            } catch (java.nio.charset.CharacterCodingException ex) {
                throw new AuditException(422, "INVALID_FILE", "文本须使用UTF-8编码");
            }
        }
        throw new AuditException(422, "INVALID_FILE", "首版只接受内容匹配的PDF、PNG、JPEG或UTF-8文本凭证");
    }

    public record Download(FileSystemResource resource, String fileName) {
    }

    public Download download(String taskId, String ref) throws IOException {
        if (ref.startsWith("evidence:"))
            return personal.downloadForTask(taskId, ref);
        tasks.detail(taskId);
        String id = ref.startsWith("audit:") ? ref.substring(6) : ref;
        var e = evidence.findById(id).orElseThrow(() -> new AuditException(404, "NOT_FOUND", "凭证不存在"));
        require(e.taskId.equals(taskId), 404, "NOT_FOUND", "凭证不属于本任务");
        Path actualRoot = root.toRealPath(), file = actualRoot.resolve(e.storageKey).normalize();
        require(file.startsWith(actualRoot) && Files.isRegularFile(file, LinkOption.NOFOLLOW_LINKS)
                && file.toRealPath().startsWith(actualRoot), 404, "NOT_FOUND", "凭证文件不可用");
        return new Download(new FileSystemResource(file), e.fileName);
    }
}
