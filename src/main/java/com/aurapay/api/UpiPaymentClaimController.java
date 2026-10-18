package com.aurapay.api;

import java.security.MessageDigest;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

/**
 * Safe intake and manual reconciliation workflow for personal UPI QR payments.
 * A customer claim is never proof of payment. Only an administrator who has
 * independently checked the bank/acquirer transaction record can mark it verified.
 * This prototype does not grant credits or enable live payments.
 */
@RestController
@RequestMapping("/api/upi-claims")
public class UpiPaymentClaimController {
    private final JdbcTemplate jdbc;
    private final String adminToken;

    public UpiPaymentClaimController(JdbcTemplate jdbc,
            @Value("${aurapay.qr-admin-token:}") String adminToken) {
        this.jdbc = jdbc;
        this.adminToken = adminToken == null ? "" : adminToken;
    }

    @PostMapping
    public Map<String, Object> submitClaim(@RequestBody ClaimRequest request) {
        if (request == null || request.orderId() == null || request.orderId().isBlank()
                || request.upiReference() == null || !request.upiReference().matches("[A-Za-z0-9-]{6,64}")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "A valid AuraPay demo order ID and UPI reference are required.");
        }

        // Reference numbers are collected for reconciliation only; they are not verified by this endpoint.
        OrderRow order;
        try {
            order = jdbc.queryForObject(
                    "SELECT order_id, amount_in_paise, status FROM demo_orders WHERE order_id = ?",
                    (rs, row) -> new OrderRow(rs.getString("order_id"), rs.getLong("amount_in_paise"), rs.getString("status")),
                    request.orderId().trim());
        } catch (org.springframework.dao.EmptyResultDataAccessException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "AuraPay order not found.");
        }

        if (!"DEMO_CREATED".equals(order.status())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "This order is not eligible for a UPI claim.");
        }
        if (request.amountInPaise() == null || request.amountInPaise() != order.amountInPaise()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Claim amount must match the order amount.");
        }

        String id = UUID.randomUUID().toString();
        try {
            jdbc.update("""
                    INSERT INTO upi_payment_claims
                    (claim_id, order_id, amount_in_paise, upi_reference, status, created_at)
                    VALUES (?, ?, ?, ?, 'PENDING_REVIEW', ?)
                    """, id, order.orderId(), order.amountInPaise(), request.upiReference().trim(),
                    Timestamp.from(Instant.now()));
        } catch (DuplicateKeyException e) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "This UPI reference has already been submitted. A reference alone does not prove payment.");
        }

        return Map.of("claimId", id, "status", "PENDING_REVIEW",
                "message", "Claim recorded for manual reconciliation only. No payment was verified and no credits were granted.");
    }

    @GetMapping
    public List<ClaimView> listClaims(
            @RequestHeader(value = "X-AuraPay-Admin-Token", required = false) String suppliedToken) {
        requireAdmin(suppliedToken);
        return jdbc.query("""
                SELECT claim_id, order_id, amount_in_paise, upi_reference, status, created_at, reviewed_at, review_note
                FROM upi_payment_claims ORDER BY created_at DESC LIMIT 200
                """, (rs, row) -> new ClaimView(rs.getString("claim_id"), rs.getString("order_id"),
                rs.getLong("amount_in_paise"), rs.getString("upi_reference"), rs.getString("status"),
                rs.getTimestamp("created_at").toInstant().toString(),
                rs.getTimestamp("reviewed_at") == null ? null : rs.getTimestamp("reviewed_at").toInstant().toString(),
                rs.getString("review_note")));
    }

    @PostMapping("/{claimId}/review")
    @Transactional
    public Map<String, Object> reviewClaim(
            @PathVariable String claimId,
            @RequestHeader(value = "X-AuraPay-Admin-Token", required = false) String suppliedToken,
            @RequestBody ReviewRequest request) {
        requireAdmin(suppliedToken);
        if (request == null || !List.of("VERIFY_MANUALLY", "REJECT").contains(request.decision())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Decision must be VERIFY_MANUALLY or REJECT.");
        }
        if ("VERIFY_MANUALLY".equals(request.decision()) && !Boolean.TRUE.equals(request.bankStatementChecked())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "To verify manually, confirm you independently checked the bank/provider transaction record.");
        }
        String status = "VERIFY_MANUALLY".equals(request.decision()) ? "VERIFIED_MANUALLY" : "REJECTED";
        int updated = jdbc.update("""
                UPDATE upi_payment_claims SET status = ?, reviewed_at = ?, review_note = ?
                WHERE claim_id = ? AND status = 'PENDING_REVIEW'
                """, status, Timestamp.from(Instant.now()), safeNote(request.note()), claimId);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Claim not found or already reviewed. Only pending claims can be reviewed.");
        }
        return Map.of("claimId", claimId, "status", status,
                "creditsGranted", false,
                "message", "Review recorded. This prototype does not grant credits; verified bank reconciliation is recorded for audit only.");
    }

    private void requireAdmin(String suppliedToken) {
        if (adminToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Admin review is disabled until AURAPAY_QR_ADMIN_TOKEN is configured.");
        }
        if (suppliedToken == null || !MessageDigest.isEqual(
                adminToken.getBytes(java.nio.charset.StandardCharsets.UTF_8),
                suppliedToken.getBytes(java.nio.charset.StandardCharsets.UTF_8))) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Invalid admin token.");
        }
    }

    private static String safeNote(String note) {
        if (note == null) return "";
        String cleaned = note.replaceAll("[\\p{Cntrl}&&[^\\r\\n\\t]]", "").trim();
        return cleaned.length() > 500 ? cleaned.substring(0, 500) : cleaned;
    }

    public record ClaimRequest(String orderId, Long amountInPaise, String upiReference) {}
    public record ReviewRequest(String decision, Boolean bankStatementChecked, String note) {}
    public record ClaimView(String claimId, String orderId, long amountInPaise, String upiReference,
            String status, String createdAt, String reviewedAt, String reviewNote) {}
    private record OrderRow(String orderId, long amountInPaise, String status) {}
}