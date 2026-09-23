package com.gsgd.generic_erp.controller;

import java.io.IOException;
import java.security.NoSuchAlgorithmException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.gsgd.generic_erp.service.AppendixService;
import com.gsgd.generic_erp.util.SimpleResponse;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class FileController {
    private final AppendixService service;

    @RequestMapping(path = "/uploads", method = RequestMethod.POST)
    public SimpleResponse uploads(@RequestParam String fileName, @RequestParam String type,
            @RequestParam Long chunkSize, @RequestParam Long totalSize, String hash) {
        return service.createUploads(hash, fileName, type, chunkSize, totalSize);
    }

    @PutMapping("/uploads/chunk")
    public SimpleResponse uploadChunk(@RequestPart MultipartFile chunk,
            @RequestParam Long id,
            @RequestParam String uuid,
            @RequestParam Integer size,
            @RequestParam Integer currentFileSize,
            @RequestParam Integer idx,
            @RequestParam Boolean resumed) throws IOException {
        return service.uploadChunks(chunk,
                id,
                uuid,
                size,
                currentFileSize,
                idx,
                resumed);
    }

    @GetMapping("/file/merge")
    public SimpleResponse merge(@RequestParam String uuid, @RequestParam String code)
            throws NoSuchAlgorithmException, IOException {
        return service.merge(uuid, code);
    }
}
