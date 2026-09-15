package com.selfcare.config.web;

import com.selfcare.config.domain.AssetDocument;
import com.selfcare.config.service.AssetService;
import com.selfcare.platform.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

/**
 * Admin REST API for asset metadata.
 * Binary file upload is handled by separate object storage service.
 * This controller manages metadata only.
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin/assets")
@RequiredArgsConstructor
@Tag(name = "Admin Assets", description = "Manage asset metadata (images, icons, documents)")
public class AdminAssetController {

    private final AssetService service;

    @GetMapping
    @Operation(summary = "List assets for a tenant")
    public ResponseEntity<ApiResponse<Page<AssetDocument>>> list(
            @RequestParam String tenantId,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {

        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<AssetDocument> assets = service.search(tenantId, type, status, pageable);
        return ResponseEntity.ok(ApiResponse.of(assets, ""));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get asset by ID")
    public ResponseEntity<ApiResponse<AssetDocument>> get(@PathVariable String id) {
        return ResponseEntity.ok(ApiResponse.of(service.get(id), ""));
    }

    @PostMapping
    @Operation(summary = "Save asset metadata")
    public ResponseEntity<ApiResponse<AssetDocument>> save(@RequestBody AssetDocument asset) {
        return ResponseEntity.ok(ApiResponse.of(service.save(asset), ""));
    }

    @PostMapping("/upload")
    @Operation(summary = "Upload asset file and create metadata")
    public ResponseEntity<ApiResponse<AssetDocument>> upload(
            @RequestParam String tenantId,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) String altText,
            @RequestParam(required = false) String tags) throws IOException {

        // In real implementation, upload to object storage (S3/MinIO) and get URL
        // For now, create metadata with placeholder URL
        String storageKey = tenantId + "/" + UUID.randomUUID() + "_" + file.getOriginalFilename();
        String url = "https://cdn.selfcare.io/" + storageKey;

        AssetDocument asset = AssetDocument.builder()
                .tenantId(tenantId)
                .type(type != null ? type : inferType(file.getContentType()))
                .filename(file.getOriginalFilename())
                .mimeType(file.getContentType())
                .sizeBytes(file.getSize())
                .url(url)
                .storageKey(storageKey)
                .altText(altText)
                .tags(tags != null ? List.of(tags.split(",")) : List.of())
                .status("ACTIVE")
                .build();

        return ResponseEntity.ok(ApiResponse.of(service.save(asset), ""));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete asset (soft delete)")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable String id) {
        service.delete(id);
        return ResponseEntity.ok(ApiResponse.of(null, ""));
    }

    private String inferType(String mimeType) {
        if (mimeType == null) return "OTHER";
        if (mimeType.startsWith("image/")) return "IMAGE";
        if (mimeType.startsWith("video/")) return "VIDEO";
        if (mimeType.startsWith("audio/")) return "AUDIO";
        if (mimeType.equals("application/pdf")) return "DOCUMENT";
        return "OTHER";
    }
}