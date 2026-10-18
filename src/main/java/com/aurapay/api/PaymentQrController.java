package com.aurapay.api;

import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

/**
 * Personal UPI QR storage for display only. This endpoint never marks an order paid.
 * Uploads require AURAPAY_QR_ADMIN_TOKEN, which must be configured in the hosting environment.
 */
@RestController
@RequestMapping("/api/payment-qr")
public class PaymentQrController {
    private static final int MAX_BYTES = 1_500_000;
    private final JdbcTemplate jdbc;
    private final String adminToken;

    public PaymentQrController(JdbcTemplate jdbc,
            @Value("${aurapay.qr-admin-token:}") String adminToken) {
        this.jdbc = jdbc;
        this.adminToken = adminToken == null ? "" : adminToken;
    }

    @GetMapping
    public ResponseEntity<byte[]> getQr() {
        try {
            QrImage image = jdbc.queryForObject(
                "SELECT image_data, content_type FROM payment_qr WHERE id = 1",
                (rs, row) -> new QrImage(rs.getBytes("image_data"), rs.getString("content_type")));
            if (image == null || image.bytes() == null || image.bytes().length == 0) {
                return ResponseEntity.notFound().build();
            }
            return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(image.contentType()))
                .cacheControl(CacheControl.noCache())
                .header("X-Content-Type-Options", "nosniff")
                .body(image.bytes());
        } catch (EmptyResultDataAccessException e) {
            return ResponseEntity.notFound().build();
        }
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Transactional
    public ResponseEntity<?> uploadQr(
            @RequestHeader(value = "X-AuraPay-Admin-Token", required = false) String suppliedToken,
            @RequestParam("file") MultipartFile file) {
        if (adminToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                "QR upload is disabled until AURAPAY_QR_ADMIN_TOKEN is configured in the hosting environment.");
        }
        if (suppliedToken == null || !MessageDigest.isEqual(
                adminToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                suppliedToken.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin token.");
        }
        if (file == null || file.isEmpty() || file.getSize() > MAX_BYTES) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose an image no larger than 1.5 MB.");
        }
        try {
            byte[] bytes = file.getBytes();
            String type = detectImageType(bytes);
            if (type == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Upload a valid PNG, JPEG, or WebP image.");
            }
            int updated = jdbc.update(
                "UPDATE payment_qr SET image_data = ?, content_type = ?, updated_at = ? WHERE id = 1",
                bytes, type, Timestamp.from(Instant.now()));
            if (updated == 0) {
                jdbc.update("INSERT INTO payment_qr (id, image_data, content_type, updated_at) VALUES (1, ?, ?, ?)",
                    bytes, type, Timestamp.from(Instant.now()));
            }
            return ResponseEntity.ok(Map.of("saved", true,
                "message", "QR image saved. This only publishes the QR; it does not verify payments or grant credits."));
        } catch (java.io.IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Could not read the uploaded image.");
        }
    }

    private static String detectImageType(byte[] b) {
        if (b.length >= 8 && (b[0] & 255) == 137 && b[1] == 80 && b[2] == 78 && b[3] == 71
                && b[4] == 13 && b[5] == 10 && b[6] == 26 && b[7] == 10) return "image/png";
        if (b.length >= 3 && (b[0] & 255) == 255 && (b[1] & 255) == 216 && (b[2] & 255) == 255)
            return "image/jpeg";
        if (b.length >= 12 && b[0] == 'R' && b[1] == 'I' && b[2] == 'F' && b[3] == 'F'
                && b[8] == 'W' && b[9] == 'E' && b[10] == 'B' && b[11] == 'P') return "image/webp";
        return null;
    }

    private record QrImage(byte[] bytes, String contentType) {}
}
