package com.omobio.conformance.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.json.JSONObject;

import java.time.Instant;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Factory for generating meaningful, realistic test data for the conformance tests.
 * Each call returns a unique instance to avoid cross-test contamination.
 */
public final class TestDataFactory {

    private static final AtomicInteger SEQUENCE = new AtomicInteger(0);
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_INSTANT;

    private TestDataFactory() {
    }

    public static String uniqueId(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    public static String uniqueConnectionId() {
        return uniqueId("CONN");
    }

    public static String uniqueMsisdn() {
        // Sri Lankan mobile numbers in the format +94 7X XXX XXXX
        int seq = SEQUENCE.incrementAndGet();
        return String.format("+9477%07d", seq);
    }

    public static String uniquePolicyId() {
        return uniqueId("AIA-POL");
    }

    public static String uniqueClaimId() {
        return uniqueId("AIA-CLM");
    }

    public static String uniqueBillId() {
        return uniqueId("BILL");
    }

    public static String uniqueTransactionId() {
        return uniqueId("TXN");
    }

    public static String uniqueIdempotencyKey() {
        return UUID.randomUUID().toString();
    }

    public static String uniqueCorrelationId() {
        return "conformance-" + UUID.randomUUID();
    }

    public static String currentTimestamp() {
        return ISO.format(Instant.now());
    }

    public static String currentDate() {
        return LocalDate.now().toString();
    }

    public static String dueDate() {
        return LocalDate.now().plusDays(30).toString();
    }

    public static Map<String, Object> otpRequest(String identifier) {
        Map<String, Object> request = new HashMap<>();
        request.put("identifier", identifier);
        request.put("channel", "SMS");
        return request;
    }

    public static Map<String, Object> otpVerifyRequest(String identifier, String code, String correlationId) {
        Map<String, Object> request = new HashMap<>();
        request.put("identifier", identifier);
        request.put("code", code);
        request.put("correlationId", correlationId);
        return request;
    }

    public static Map<String, Object> paymentChargeRequest(String fromConnectionId, String toConnectionId, double amount) {
        Map<String, Object> request = new HashMap<>();
        request.put("fromConnectionId", fromConnectionId);
        request.put("toConnectionId", toConnectionId);
        request.put("amount", amount);
        request.put("currency", "LKR");
        request.put("paymentMethodId", uniqueId("PM"));
        return request;
    }

    public static Map<String, Object> billPaymentRequest(double amount, String paymentMethodId) {
        Map<String, Object> request = new HashMap<>();
        request.put("amount", amount);
        request.put("paymentMethodId", paymentMethodId);
        return request;
    }

    public static Map<String, Object> claimSubmissionRequest(String policyId, String claimType, double amount) {
        Map<String, Object> request = new HashMap<>();
        request.put("policyId", policyId);
        request.put("claimType", claimType);
        request.put("incidentDate", LocalDate.now().minusDays(5).toString());
        request.put("amount", amount);
        request.put("currency", "LKR");
        request.put("documents", new String[]{"doc-" + UUID.randomUUID().toString().substring(0, 6),
                "doc-" + UUID.randomUUID().toString().substring(0, 6)});
        return request;
    }

    public static JSONObject policyResponse(String policyId, String policyholderId) {
        JSONObject policy = new JSONObject();
        policy.put("policyId", policyId);
        policy.put("productType", "LIFE");
        policy.put("policyholderId", policyholderId);
        policy.put("sumAssured", 5_000_000.00);
        policy.put("currency", "LKR");
        policy.put("premiumAmount", 12_500.00);
        policy.put("premiumFrequency", "MONTHLY");
        policy.put("status", "ACTIVE");
        policy.put("startDate", "2024-01-01");
        policy.put("nextPremiumDue", LocalDate.now().plusDays(28).toString());
        return policy;
    }

    public static JSONObject balanceResponse(String connectionId, double amount, String currency) {
        JSONObject balance = new JSONObject();
        balance.put("connectionId", connectionId);
        balance.put("amount", amount);
        balance.put("currency", currency);
        balance.put("asOf", currentTimestamp());
        return balance;
    }

    public static String toJson(Object obj) {
        try {
            ObjectMapper mapper = TestConfig.objectMapper();
            return mapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize to JSON", e);
        }
    }
}
