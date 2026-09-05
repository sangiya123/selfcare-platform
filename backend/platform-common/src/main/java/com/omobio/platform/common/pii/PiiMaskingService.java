package com.omobio.platform.common.pii;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Centralized PII classification and masking service.
 *
 * Three enforcement points:
 * - Logging: maskForLog(message)
 * - Reports: maskForReport(message, reportType)
 * - AI: maskForAi(message, useCase)
 *
 * @see ADR-018: PII Classification & Masking Strategy
 */
@Service
public class PiiMaskingService {

    private static final Logger log = LoggerFactory.getLogger(PiiMaskingService.class);

    // Default masking tag for logs
    private static final Map<PiiClass, String> DEFAULT_LOG_TAG;

    static {
        DEFAULT_LOG_TAG = Map.ofEntries(
            Map.entry(PiiClass.EMAIL, "<EMAIL>"),
            Map.entry(PiiClass.PHONE, "<PHONE>"),
            Map.entry(PiiClass.MSISDN, "<MSISDN>"),
            Map.entry(PiiClass.NATIONAL_ID, "<NATIONAL_ID>"),
            Map.entry(PiiClass.PAYMENT_CARD, "<PAYMENT_CARD>"),
            Map.entry(PiiClass.POLICY_NUMBER, "<POLICY_NUMBER>"),
            Map.entry(PiiClass.ACCOUNT_NUMBER, "<ACCOUNT_NUMBER>"),
            Map.entry(PiiClass.IP_ADDRESS, "<IP_ADDRESS>"),
            Map.entry(PiiClass.JWT, "<JWT>"),
            Map.entry(PiiClass.SECRET_KEY, "<SECRET_KEY>"),
            Map.entry(PiiClass.CUSTOMER_NAME, "<CUSTOMER_NAME>"),
            Map.entry(PiiClass.DATE_OF_BIRTH, "<DATE_OF_BIRTH>"),
            Map.entry(PiiClass.ADDRESS, "<ADDRESS>")
        );
    }

    // Compiled regex patterns for each PII class
    private final Map<PiiClass, Pattern> patterns;

    public PiiMaskingService() {
        this.patterns = Map.ofEntries(
            entry(PiiClass.EMAIL,
                Pattern.compile("(?i)\\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Z|a-z]{2,}\\b")),
            entry(PiiClass.PHONE,
                Pattern.compile("(?i)\\+?\\d{1,3}[-.\\s]?\\(?\\d{1,4}\\)?[-.\\s]?\\d{1,4}[-.\\s]?\\d{1,9}")),
            entry(PiiClass.MSISDN,
                Pattern.compile("(?i)\\b(?:\\+94|94|0)(?:7[0-9]|77|76|75|71|70)\\d{7}\\b")),
            entry(PiiClass.NATIONAL_ID,
                Pattern.compile("(?i)\\b(?:\\d{9}[vVxX]|\\d{12})\\b")),
            entry(PiiClass.PAYMENT_CARD,
                Pattern.compile("\\b(?:\\d[ -]*?){13,19}\\b")),
            entry(PiiClass.IP_ADDRESS,
                Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b")),
            entry(PiiClass.DATE_OF_BIRTH,
                Pattern.compile("(?i)\\b(?:\\d{1,2}[/\\-]\\d{1,2}[/\\-]\\d{2,4}|\\d{4}[/\\-]\\d{1,2}[/\\-]\\d{1,2})\\b"))
        );
    }

    private static Map.Entry<PiiClass, Pattern> entry(PiiClass k, Pattern v) {
        return Map.entry(k, v);
    }

    /**
     * Mask all PII in a string for logging purposes.
     * Uses REPLACE_TAG strategy by default.
     */
    public String maskForLog(String message) {
        if (message == null || message.isEmpty()) return message;
        String result = message;
        for (Map.Entry<PiiClass, Pattern> entry : patterns.entrySet()) {
            result = maskWithTag(result, entry.getKey(), entry.getValue());
        }
        return result;
    }

    /**
     * Mask all PII in a string for use in AI prompts.
     * Uses HASH strategy for linkable analytics + REPLACE_TAG for display.
     */
    public String maskForAi(String message, String useCase) {
        if (message == null || message.isEmpty()) return message;
        // For AI, use PARTIAL masking for sensitive fields (last 4 chars shown)
        String result = message;
        for (Map.Entry<PiiClass, Pattern> entry : patterns.entrySet()) {
            PiiClass piiClass = entry.getKey();
            Pattern pattern = entry.getValue();
            if (piiClass == PiiClass.MSISDN || piiClass == PiiClass.PAYMENT_CARD || piiClass == PiiClass.NATIONAL_ID) {
                result = maskPartial(result, pattern, 4);
            } else {
                result = maskWithTag(result, piiClass, pattern);
            }
        }
        return result;
    }

    /**
     * Detect and classify PII in a message.
     * Returns a map of PII class → matched values.
     */
    public Map<PiiClass, java.util.List<String>> detectPii(String message) {
        if (message == null || message.isEmpty()) return Map.of();
        java.util.Map<PiiClass, java.util.List<String>> detected = new java.util.EnumMap<>(PiiClass.class);
        for (Map.Entry<PiiClass, Pattern> entry : patterns.entrySet()) {
            Matcher matcher = entry.getValue().matcher(message);
            java.util.List<String> matches = new java.util.ArrayList<>();
            while (matcher.find()) {
                matches.add(matcher.group());
            }
            if (!matches.isEmpty()) {
                detected.put(entry.getKey(), matches);
            }
        }
        return detected;
    }

    /**
     * Mask input with a replacement tag, but also record the classification.
     * Returns both masked text and a class map for audit purposes.
     */
    public MaskResult maskMap(String input) {
        if (input == null || input.isEmpty()) return new MaskResult(input, Map.of());
        String masked = input;
        java.util.Map<PiiClass, java.util.List<String>> classMap = new java.util.EnumMap<>(PiiClass.class);
        for (Map.Entry<PiiClass, Pattern> entry : patterns.entrySet()) {
            PiiClass piiClass = entry.getKey();
            Pattern pattern = entry.getValue();
            Matcher matcher = pattern.matcher(masked);
            java.util.List<String> matches = new java.util.ArrayList<>();
            StringBuffer sb = new StringBuffer();
            while (matcher.find()) {
                String match = matcher.group();
                matches.add(match);
                matcher.appendReplacement(sb, DEFAULT_LOG_TAG.getOrDefault(piiClass, "<PII>"));
            }
            matcher.appendTail(sb);
            masked = sb.toString();
            if (!matches.isEmpty()) {
                classMap.put(piiClass, matches);
            }
        }
        return new MaskResult(masked, classMap);
    }

    private String maskWithTag(String text, PiiClass piiClass, Pattern pattern) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        String tag = DEFAULT_LOG_TAG.getOrDefault(piiClass, "<PII>");
        while (matcher.find()) {
            matcher.appendReplacement(sb, tag);
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String maskPartial(String text, Pattern pattern, int visibleChars) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String match = matcher.group();
            if (match.length() <= visibleChars) {
                matcher.appendReplacement(sb, "****");
            } else {
                String visible = match.substring(match.length() - visibleChars);
                matcher.appendReplacement(sb, "***-" + visible);
            }
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    public record MaskResult(String maskedText, Map<PiiClass, java.util.List<String>> classMap) {}
}
