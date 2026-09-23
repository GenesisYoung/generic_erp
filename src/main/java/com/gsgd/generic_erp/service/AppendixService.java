package com.gsgd.generic_erp.service;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import com.gsgd.generic_erp.configuration.exception.FileOwnershipMismatchException;
import com.gsgd.generic_erp.entity.FileChunk;
import com.gsgd.generic_erp.entity.UploadSession;
import com.gsgd.generic_erp.entity.UploadSessionStatus;
import com.gsgd.generic_erp.entity.product.Appendix;
import com.gsgd.generic_erp.repository.AppendixRepository;
import com.gsgd.generic_erp.repository.FileChunkRepository;
import com.gsgd.generic_erp.repository.UploadSessionRepository;
import com.gsgd.generic_erp.util.GlobalVariable;
import com.gsgd.generic_erp.util.SimpleResponse;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AppendixService {
        private final AppendixRepository repository;
        private final UploadSessionRepository uploadSessionRepository;
        private final GlobalVariable variable;
        private final FileChunkRepository fileChunkRepository;
        @Value("${spring.global.file.home_path}")
        private String fileHomePath;

        @Transactional
        public SimpleResponse createUploads(String id, String name, String type, Long chunkSize, Long totalSize) {
                UploadSession session = uploadSessionRepository.findByFileUuid(id);
                if (session != null) {
                        return SimpleResponse.builder().code(210).message(String.valueOf(session.getCurrentIdx()))
                                        .build();
                }
                String extension = null;
                if (name != null) {
                        String[] split = name.split(Pattern.quote("."));
                        extension = split[1];
                }
                Appendix appendix = Appendix.builder()
                                .storageKey(id)
                                .fileName(name)
                                .fileExtension(extension)
                                .active(false)
                                .createdBy(variable.currentUserId())
                                .byteSize(totalSize)
                                .mimeType(type).build();
                Appendix saved = repository.save(appendix);
                UploadSession upload = UploadSession.builder()
                                .chunkSize(chunkSize)
                                .totalSize(totalSize)
                                .currentIdx(0)
                                .fileUuid(id)
                                .appendixId(appendix.getAppendixId())
                                .status(UploadSessionStatus.UPLOADING)
                                .build();
                uploadSessionRepository.save(upload);
                return SimpleResponse.builder().code(200).message(saved.getAppendixId().toString()).build();
        }

        @Transactional(rollbackFor = Exception.class)
        public SimpleResponse uploadChunks(
                        MultipartFile chunk,
                        Long id,
                        String uuid,
                        Integer size,
                        Integer currentFileSize,
                        Integer idx,
                        Boolean resumed) throws IOException {

                Long currentUserId = variable.currentUserId();

                if (idx == null || idx < 0) {
                        throw new IllegalArgumentException("Invalid chunk index");
                }

                try {
                        UUID.fromString(uuid);
                } catch (IllegalArgumentException e) {
                        throw new IllegalArgumentException("Invalid UUID", e);
                }

                Appendix appendix = repository.findById(id)
                                .orElseThrow(() -> new IllegalArgumentException(
                                                "Appendix not found: " + id));

                if (!Objects.equals(
                                appendix.getCreatedBy(),
                                currentUserId)) {

                        throw new FileOwnershipMismatchException(
                                        "File operation rejected: user does not own this appendix");
                }

                UploadSession session = uploadSessionRepository.findByFileUuid(uuid);
                if (session == null) {
                        throw new IllegalArgumentException(
                                        "Upload session not found: " + uuid);
                }
                if (!Objects.equals(session.getAppendixId(), session.getAppendixId()))
                        throw new FileOwnershipMismatchException(
                                        "Appendix id mismatch");

                long chunkSize = session.getChunkSize();
                long totalSize = session.getTotalSize();

                int totalChunks = Math.ceilDiv(Math.toIntExact(totalSize), Math.toIntExact(chunkSize));

                if (idx >= totalChunks) {
                        throw new IllegalArgumentException(
                                        "Chunk index exceeds total chunk count");
                }

                long expectedSize;

                if (idx == totalChunks - 1) {
                        expectedSize = totalSize - (long) idx * chunkSize;
                } else {
                        expectedSize = chunkSize;
                }

                if (chunk.getSize() != expectedSize) {
                        throw new IllegalArgumentException(
                                        "Unexpected chunk size. Expected "
                                                        + expectedSize
                                                        + ", actual "
                                                        + chunk.getSize());
                }

                Path basePath = Path.of(fileHomePath)
                                .toAbsolutePath()
                                .normalize();

                Files.createDirectories(basePath);

                Path chunkPath = basePath.resolve(uuid + "_" + idx);

                try (
                                InputStream in = new BufferedInputStream(
                                                chunk.getInputStream());

                                OutputStream out = new BufferedOutputStream(
                                                Files.newOutputStream(
                                                                chunkPath,
                                                                StandardOpenOption.CREATE,
                                                                StandardOpenOption.TRUNCATE_EXISTING))) {
                        in.transferTo(out);
                }

                FileChunk fileChunk = FileChunk.builder()
                                .appendixId(id)
                                .uuid(uuid)
                                .standardSize(
                                                Math.toIntExact(chunkSize))
                                .chuckIdx(idx)
                                .finished(true)
                                .updateTime(LocalDateTime.now())
                                .operatorId(currentUserId)
                                .build();
                if (!resumed)
                        fileChunkRepository.save(fileChunk);
                else {
                        FileChunk saved = fileChunkRepository.findByUuidAndChuckIdx(uuid, idx);
                        if (saved == null)
                                fileChunkRepository.save(fileChunk);
                        else {
                                fileChunk.setFinished(true);
                                fileChunkRepository.save(saved);
                        }
                }
                long finishedChunks = fileChunkRepository
                                .countByUuidAndFinishedTrue(uuid);

                if (finishedChunks == totalChunks) {
                        return SimpleResponse.builder()
                                        .code(220)
                                        .message("Ready for mergeing")
                                        .build();
                }

                uploadSessionRepository.save(session);

                return SimpleResponse.builder()
                                .code(200)
                                .message(uuid)
                                .build();
        }

        public SimpleResponse merge(String uuid, String SHAValidationCode)
                        throws IOException, NoSuchAlgorithmException {
                UploadSession session = uploadSessionRepository.findByFileUuid(uuid);
                long chunkSize = session.getChunkSize();
                long totalSize = session.getTotalSize();
                Appendix appendix = repository.findByStorageKey(uuid);
                String extension = appendix.getFileExtension();
                int totalChunks = Math.ceilDiv(Math.toIntExact(totalSize), Math.toIntExact(chunkSize));
                Path basePath = Path.of(fileHomePath)
                                .toAbsolutePath()
                                .normalize();
                session.setStatus(
                                UploadSessionStatus.MERGING);
                Path outputPath = basePath.resolve(uuid + "." + extension);
                try (
                                OutputStream out = new BufferedOutputStream(
                                                Files.newOutputStream(
                                                                outputPath,
                                                                StandardOpenOption.CREATE,
                                                                StandardOpenOption.TRUNCATE_EXISTING))) {

                        for (int i = 0; i < totalChunks; i++) {

                                Path currentChunk = basePath.resolve(
                                                uuid + "_" + i);

                                if (!Files.exists(currentChunk)) {
                                        throw new IOException(
                                                        "Missing chunk: " + i);
                                }

                                try (
                                                InputStream in = new BufferedInputStream(
                                                                Files.newInputStream(
                                                                                currentChunk))) {
                                        in.transferTo(out);
                                }
                        }
                }

                // Validate the complete file after the output stream has been closed.
                try (InputStream in = new BufferedInputStream(
                                Files.newInputStream(outputPath))) {
                        if (!checkValidtyUsingSHA256(SHAValidationCode, in)) {
                                return SimpleResponse.builder()
                                                .code(201)
                                                .message("File checksum mismatch, please reupload")
                                                .build();
                        }
                }

                session.setStatus(
                                UploadSessionStatus.FINISHED);
                return SimpleResponse.builder()
                                .code(200)
                                .message("File merged successfully")
                                .build();
        }

        private boolean checkValidtyUsingSHA256(
                        String targetCode, InputStream file)
                        throws NoSuchAlgorithmException, IOException {

                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                byte[] buffer = new byte[8192];
                int count;

                while ((count = file.read(buffer)) != -1) {
                        digest.update(buffer, 0, count);
                }

                StringBuilder hex = new StringBuilder(64);
                for (byte b : digest.digest()) {
                        hex.append(String.format("%02x", b & 0xff));
                }

                return targetCode != null
                                && hex.toString().equalsIgnoreCase(targetCode.trim());
        }
}
