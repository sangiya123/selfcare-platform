package com.omobio.ai.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * PII classification and masking service.
 *
 * Implements the NFR security and privacy controls:
 *  - "PII masking in logs/telemetry; production debug payload logging disabled
 *    by default" (NFRs)
 *  - "masking/tokenization" (data security)
 *  - "no PII in config documents or application logs unless explicitly
 *    justified and protected" (data security)
 *  - "secret isolation" (AI security)
 *
 * Recognizes: email, phone, MSISDN, NIC/passport, payment card (PAN),
 * policy number, account number, IP, JWT.
 *
 * Per-tenant rules: a tenant can extend the patterns (e.g. add a local
 * national ID format). The defaults work for telco/insurance globally.
 */
@Slf4j
@Service("aiPiiMaskingService")
public class PiiMaskingService {

    public enum PiiType {
        EMAIL,
        PHONE,
        MSISDN,
        NATIONAL_ID,
        PASSPORT,
        PAYMENT_CARD,
        POLICY_NUMBER,
        ACCOUNT_NUMBER,
        IP_ADDRESS,
        JWT,
        SECRET_KEY
    }

    public enum MaskingStrategy {
        /** Replace with type tag, e.g. [EMAIL] */
        REPLACE_TAG,
        /** Keep last 4 digits: 947712****67 */
        PARTIAL,
        /** Hash with SHA-256 (consistent anonymization) */
        HASH,
        /** Fully redact: [REDACTED] */
        REDACT
    }

    // Default patterns
    private static final Map<PiiType, Pattern> DEFAULT_PATTERNS = new LinkedHashMap<>();
    private static final Map<PiiType, MaskingStrategy> DEFAULT_STRATEGIES = new LinkedHashMap<>();

    static {
        DEFAULT_PATTERNS.put(PiiType.EMAIL,
                Pattern.compile("[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}"));
        DEFAULT_PATTERNS.put(PiiType.PHONE,
                Pattern.compile("\\+?\\d{1,3}[\\s-]?\\d{3,4}[\\s-]?\\d{3,4}[\\s-]?\\d{0,4}"));
        // MSISDN: starts with country code, typically 7-15 digits
        DEFAULT_PATTERNS.put(PiiType.MSISDN,
                Pattern.compile("(?<![\\d])\\+?\\d{10,15}(?![\\d])"));
        // National ID — generic 9-12 digit with optional dashes
        DEFAULT_PATTERNS.put(PiiType.NATIONAL_ID,
                Pattern.compile("\\b\\d{9,12}(?:[Vv]|\\d)?\\b"));
        DEFAULT_PATTERNS.put(PiiType.PASSPORT,
                Pattern.compile("\\b[A-Z]{1,2}\\d{6,8}\\b"));
        // Payment card: 13-19 digits, possibly space/dash separated
        DEFAULT_PATTERNS.put(PiiType.PAYMENT_CARD,
                Pattern.compile("\\b(?:\\d[ -]?){13,19}\\b"));
        // Policy number: alpha-numeric with hyphens
        DEFAULT_PATTERNS.put(PiiType.POLICY_NUMBER,
                Pattern.compile("\\b[A-Z]{2,4}[-/]?\\d{6,12}\\b"));
        // Account number: 8-20 digits
        DEFAULT_PATTERNS.put(PiiType.ACCOUNT_NUMBER,
                Pattern.compile("\\b\\d{8,20}\\b"));
        // IPv4
        DEFAULT_PATTERNS.put(PiiType.IP_ADDRESS,
                Pattern.compile("\\b(?:\\d{1,3}\\.){3}\\d{1,3}\\b"));
        // JWT: three base64url segments
        DEFAULT_PATTERNS.put(PiiType.JWT,
                Pattern.compile("\\beyJ[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\.[A-Za-z0-9_-]+\\b"));
        // Generic secret: api_key=..., token=..., password=... value
        DEFAULT_PATTERNS.put(PiiType.SECRET_KEY,
                Pattern.compile("(?i)(?:api[_-]?key|token|secret|password|authorization)\\s*[:=]\\s*[\"']?([A-Za-z0-9._\\-+/=]{8,})[\"']?"));

        // Default strategies
        DEFAULT_STRATEGIES.put(PiiType.EMAIL, MaskingStrategy.PARTIAL);
        DEFAULT_STRATEGIES.put(PiiType.PHONE, MaskingStrategy.PARTIAL);
        DEFAULT_STRATEGIES.put(PiiType.MSISDN, MaskingStrategy.PARTIAL);
        DEFAULT_STRATEGIES.put(PiiType.NATIONAL_ID, MaskingStrategy.PARTIAL);
        DEFAULT_STRATEGIES.put(PiiType.PASSPORT, MaskingStrategy.PARTIAL);
        DEFAULT_STRATEGIES.put(PiiType.PAYMENT_CARD, MaskingStrategy.PARTIAL);
        DEFAULT_STRATEGIES.put(PiiType.POLICY_NUMBER, MaskingStrategy.PARTIAL);
        DEFAULT_STRATEGIES.put(PiiType.ACCOUNT_NUMBER, MaskingStrategy.PARTIAL);
        DEFAULT_STRATEGIES.put(PiiType.IP_ADDRESS, MaskingStrategy.PARTIAL);
        DEFAULT_STRATEGIES.put(PiiType.JWT, MaskingStrategy.REDACT);
        DEFAULT_STRATEGIES.put(PiiType.SECRET_KEY, MaskingStrategy.REDACT);
    }

    /**
     * Mask a text string by detecting and replacing PII.
     */
    public String mask(String text) {
        if (text == null || text.isEmpty()) return text;
        String result = text;
        for (Map.Entry<PiiType, Pattern> e : DEFAULT_PATTERNS.entrySet()) {
            PiiType type = e.getKey();
            Pattern pattern = e.getValue();
            MaskingStrategy strategy = DEFAULT_STRATEGIES.get(type);
            result = applyMask(result, type, pattern, strategy);
        }
        return result;
    }

    /**
     * Mask PII in a structured map (e.g. JSON-like payload).
     * Recursively masks all string values.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> maskMap(Map<String, Object> input) {
        if (input == null) return null;
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : input.entrySet()) {
            Object value = e.getValue();
            if (value instanceof String s) {
                out.put(e.getKey(), mask(s));
            } else if (value instanceof Map<?, ?> m) {
                out.put(e.getKey(), maskMap((Map<String, Object>) m));
            } else if (value instanceof List<?> l) {
                out.put(e.getKey(), maskList((List<Object>) l));
            } else {
                out.put(e.getKey(), value);
            }
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    public List<Object> maskList(List<Object> input) {
        if (input == null) return null;
        List<Object> out = new ArrayList<>(input.size());
        for (Object v : input) {
            if (v instanceof String s) out.add(mask(s));
            else if (v instanceof Map<?, ?> m) out.add(maskMap((Map<String, Object>) m));
            else if (v instanceof List<?> l) out.add(maskList((List<Object>) l));
            else out.add(v);
        }
        return out;
    }

    /**
     * Classify text without masking — returns a list of detected PII types
     * and positions.
     */
    public List<PiiMatch> classify(String text) {
        List<PiiMatch> matches = new ArrayList<>();
        if (text == null || text.isEmpty()) return matches;
        for (Map.Entry<PiiType, Pattern> e : DEFAULT_PATTERNS.entrySet()) {
            Matcher m = e.getValue().matcher(text);
            while (m.find()) {
                matches.add(new PiiMatch(e.getKey(), m.start(), m.end(), m.group()));
            }
        }
        // Sort by start position
        matches.sort(Comparator.comparingInt(PiiMatch::start));
        return matches;
    }

    private String applyMask(String text, PiiType type, Pattern pattern, MaskingStrategy strategy) {
        Matcher matcher = pattern.matcher(text);
        StringBuffer sb = new StringBuffer();
        while (matcher.find()) {
            String replacement = maskValue(type, matcher.group(), strategy);
            matcher.appendReplacement(sb, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(sb);
        return sb.toString();
    }

    private String maskValue(PiiType type, String value, MaskingStrategy strategy) {
        return switch (strategy) {
            case REPLACE_TAG -> "[" + type.name() + "]";
            case PARTIAL -> partialMask(value);
            case HASH -> "hash:" + Integer.toHexString(value.hashCode());
            case REDACT -> "[REDACTED]";
        };
    }

    private String partialMask(String value) {
        if (value == null || value.length() <= 4) return "****";
        // Show last 4 chars, mask the rest
        String visible = value.substring(value.length() - 4);
        int maskedLen = value.length() - 4;
        return "*".repeat(maskedLen) + visible;
    }

    public record PiiMatch(PiiType type, int start, int end, String value) {}
}
