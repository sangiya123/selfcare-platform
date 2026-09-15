package com.selfcare.reporting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfcare.reporting.domain.ReportDefinition;
import com.selfcare.reporting.domain.ReportExecution;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Renders report execution results into a downloadable artifact.
 *
 * Supported formats:
 * - CSV: comma-separated values (RFC 4180)
 * - JSON: pretty-printed JSON array
 * - EXCEL: CSV with a .xls extension marker (full Excel rendering out of scope here)
 * - PDF: stub - returns the CSV data with a .pdf extension marker
 *
 * Output files are written to the configured output directory.
 *
 * @see ReportDefinition.OutputFormat
 */
@Slf4j
@Component
public class ReportRenderer {

    private final ObjectMapper objectMapper;

    @Value("${selfcare.reporting.output-dir:/tmp/reports}")
    private String outputDir;

    @Value("${selfcare.reporting.csv-separator:,}")
    private String csvSeparator;

    @Value("${selfcare.reporting.max-rows-per-report:1000000}")
    private long maxRows;

    /**
     * Whether to mask PII in exports. On by default per the security spec
     * ("field/row masking for reports").
     */
    @Value("${selfcare.reporting.pii-mask-enabled:true}")
    private boolean piiMaskEnabled;

    /**
     * Columns whose names match any of these (case-insensitive substring) will
     * be FULLY REDACTED in exports. The full value is replaced with {@code ***}.
     */
    private static final Set<String> FULL_REDACT_COLUMNS = new HashSet<>(java.util.Arrays.asList(
            "password", "passwd", "secret", "token", "apikey", "api_key",
            "private_key", "privatekey", "jwt", "refresh_token", "session_id",
            "credit_card", "card_number", "cardnumber", "cvv", "cvc", "pin"
    ));

    /**
     * Columns whose names match any of these will be PARTIALLY masked
     * (last 4 visible, rest redacted).
     */
    private static final Set<String> PARTIAL_MASK_COLUMNS = new HashSet<>(java.util.Arrays.asList(
            "msisdn", "phone", "mobile", "email", "national_id", "nic",
            "passport", "policy_number", "account_number", "iban"
    ));

    /**
     * Inline pattern for ad-hoc masking (in case a PII field is named
     * something non-obvious, we still mask email/phone patterns).
     */
    private static final Pattern EMAIL_PATTERN = Pattern.compile(
            "[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}");
    private static final Pattern PHONE_PATTERN = Pattern.compile(
            "(\\+?\\d[\\d\\s-]{7,}\\d)");
    private static final Pattern NATIONAL_ID_PATTERN = Pattern.compile(
            "\\b\\d{9}[Vv]?\\b|\\b\\d{12}\\b");
    private static final Pattern PAYMENT_CARD_PATTERN = Pattern.compile(
            "\\b(?:\\d[ -]*?){13,19}\\b");

    public ReportRenderer(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /**
     * Render rows of data into a result file.
     *
     * @param execution the report execution
     * @param definition the report definition
     * @param rows the data rows to render (each row is a map of column name to value)
     * @return the {@link RenderResult} with file URL, size, and row count
     * @throws IOException if file I/O fails
     */
    public RenderResult render(ReportExecution execution,
                                ReportDefinition definition,
                                List<Map<String, Object>> rows) throws IOException {
        ensureOutputDir();

        String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now())
                .replace(":", "-")
                .replace(".", "-");
        String filename = String.format("%s_%s_%s.%s",
                definition.getName().replaceAll("[^a-zA-Z0-9_-]", "_"),
                execution.getId(),
                timestamp,
                getFileExtension(definition.getOutputFormat()));

        Path filePath = Paths.get(outputDir, execution.getTenantId(), filename);
        Files.createDirectories(filePath.getParent());

        long rowCount = 0;
        long sizeBytes = 0;

        switch (definition.getOutputFormat()) {
            case "CSV" -> sizeBytes = writeCsv(filePath, rows);
            case "JSON" -> sizeBytes = writeJson(filePath, rows);
            case "EXCEL" -> sizeBytes = writeExcelStub(filePath, rows);
            case "PDF" -> sizeBytes = writePdfStub(filePath, rows);
            default -> throw new IllegalArgumentException("Unsupported format: " + definition.getOutputFormat());
        }

        rowCount = rows.size();

        String resultUrl = String.format("/api/v1/reports/executions/%s/download", execution.getId());
        log.info("Report rendered: tenant={}, reportId={}, executionId={}, format={}, rows={}, size={}",
                execution.getTenantId(), definition.getId(), execution.getId(),
                definition.getOutputFormat(), rowCount, sizeBytes);

        return RenderResult.builder()
                .resultUrl(resultUrl)
                .resultSizeBytes(sizeBytes)
                .rowCount(rowCount)
                .filePath(filePath.toString())
                .build();
    }

    private long writeCsv(Path filePath, List<Map<String, Object>> rows) throws IOException {
        try (BufferedWriter writer = Files.newBufferedWriter(filePath)) {
            if (rows.isEmpty()) {
                return 0L;
            }
            // Header row
            List<String> columns = new ArrayList<>(rows.get(0).keySet());
            writer.write(String.join(csvSeparator, columns));
            writer.newLine();
            long count = 0;
            for (Map<String, Object> row : rows) {
                if (count++ > maxRows) {
                    log.warn("CSV row cap reached ({}), truncating", maxRows);
                    break;
                }
                List<String> values = new ArrayList<>();
                for (String col : columns) {
                    Object val = row.get(col);
                    values.add(escapeCsv(maskValue(col, val)));
                }
                writer.write(String.join(csvSeparator, values));
                writer.newLine();
            }
            return Files.size(filePath);
        }
    }

    private long writeJson(Path filePath, List<Map<String, Object>> rows) throws IOException {
        // Apply PII masking to each row before writing
        List<Map<String, Object>> masked = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            java.util.LinkedHashMap<String, Object> m = new java.util.LinkedHashMap<>();
            for (Map.Entry<String, Object> e : row.entrySet()) {
                m.put(e.getKey(), maskValue(e.getKey(), e.getValue()));
            }
            masked.add(m);
        }
        objectMapper.writerWithDefaultPrettyPrinter().writeValue(filePath.toFile(), masked);
        return Files.size(filePath);
    }

    private long writeExcelStub(Path filePath, List<Map<String, Object>> rows) throws IOException {
        // Stub: write CSV with .xls extension
        log.debug("Writing EXCEL stub as CSV at {}", filePath);
        return writeCsv(filePath, rows);
    }

    private long writePdfStub(Path filePath, List<Map<String, Object>> rows) throws IOException {
        // Stub: write CSV with .pdf extension. Real PDF rendering out of scope.
        log.debug("Writing PDF stub as CSV at {}", filePath);
        return writeCsv(filePath, rows);
    }

    private String escapeCsv(String value) {
        if (value == null) {
            return "";
        }
        if (value.contains(csvSeparator) || value.contains("\"") || value.contains("\n") || value.contains("\r")) {
            return "\"" + value.replace("\"", "\"\"") + "\"";
        }
        return value;
    }

    /**
     * Mask a single value based on its column name and the data-residency
     * rules in {@code PiiMaskingService}. The mask is applied:
     *   - FULL REDACT if the column name is in {@link #FULL_REDACT_COLUMNS}
     *   - PARTIAL MASK if the column name is in {@link #PARTIAL_MASK_COLUMNS}
     *   - INLINE PATTERN MASK if the value contains email/phone/national-id
     *     patterns (catches PII that wasn't in a known column)
     *
     * @return the masked value, or the original value if no mask applies
     */
    private String maskValue(String columnName, Object value) {
        if (!piiMaskEnabled || value == null) {
            return value != null ? value.toString() : "";
        }
        String s = value.toString();
        if (s.isEmpty()) return s;
        String colLower = columnName != null ? columnName.toLowerCase() : "";
        // Column-name based masking
        if (FULL_REDACT_COLUMNS.contains(colLower)) {
            return "***";
        }
        for (String keyword : FULL_REDACT_COLUMNS) {
            if (colLower.contains(keyword)) {
                return "***";
            }
        }
        for (String keyword : PARTIAL_MASK_COLUMNS) {
            if (colLower.contains(keyword)) {
                return partialMask(s);
            }
        }
        // Inline pattern-based masking
        String result = s;
        result = EMAIL_PATTERN.matcher(result).replaceAll("[EMAIL_REDACTED]");
        result = PHONE_PATTERN.matcher(result).replaceAll(m -> partialMask(m.group()));
        result = NATIONAL_ID_PATTERN.matcher(result).replaceAll("[NIC_REDACTED]");
        result = PAYMENT_CARD_PATTERN.matcher(result).replaceAll("[CARD_REDACTED]");
        return result;
    }

    private static String partialMask(String s) {
        if (s == null || s.length() <= 4) return "***";
        StringBuilder sb = new StringBuilder();
        int visible = Math.min(4, s.length());
        for (int i = 0; i < s.length() - visible; i++) {
            sb.append('*');
        }
        sb.append(s.substring(s.length() - visible));
        return sb.toString();
    }

    private void ensureOutputDir() throws IOException {
        Path base = Paths.get(outputDir);
        if (!Files.exists(base)) {
            Files.createDirectories(base);
        }
    }

    private String getFileExtension(String format) {
        return switch (format) {
            case "CSV" -> "csv";
            case "JSON" -> "json";
            case "EXCEL" -> "xls";
            case "PDF" -> "pdf";
            default -> "txt";
        };
    }

    /**
     * Result of rendering a report.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class RenderResult {
        private String resultUrl;
        private Long resultSizeBytes;
        private Long rowCount;
        private String filePath;
    }
}
