package com.omobio.conformance;

import com.omobio.conformance.utils.TestConfig;
import com.omobio.conformance.utils.WireMockManager;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testng.ISuite;
import org.testng.ISuiteListener;
import org.testng.ITestContext;
import org.testng.ITestListener;
import org.testng.ITestResult;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Main test runner for the OMOBIO conformance suite.
 *
 * Acts as both a TestNG listener and a utility class. It:
 *  - configures RestAssured (base URL, timeouts, mappers) once for the suite;
 *  - starts/stops the shared WireMock instance for downstream-provider stubs;
 *  - records per-class metrics and writes a JSON summary to target/ for dashboards;
 *  - tags suites so CI can group reports by category (tenant-isolation, auth, etc.).
 */
public class TestRunner implements ISuiteListener, ITestListener {

    private static final Logger LOG = LoggerFactory.getLogger(TestRunner.class);

    private static final Instant SUITE_START = Instant.now();
    private static final List<SuiteResult> RESULTS = new ArrayList<>();
    private static final Map<String, Instant> CLASS_STARTS = new LinkedHashMap<>();

    public enum ReportFormat {
        HTML, JUNIT_XML, JSON
    }

    public static class SuiteResult {
        public String suiteName;
        public String className;
        public String methodName;
        public String status;
        public long durationMs;
        public String failureMessage;

        public SuiteResult(String suiteName, String className, String methodName,
                           String status, long durationMs, String failureMessage) {
            this.suiteName = suiteName;
            this.className = className;
            this.methodName = methodName;
            this.status = status;
            this.durationMs = durationMs;
            this.failureMessage = failureMessage;
        }
    }

    @Override
    public void onStart(ISuite suite) {
        LOG.info("=== Conformance suite '{}' starting ===", suite.getName());
        LOG.info("Base URL: {}", TestConfig.BASE_URL);
        LOG.info("Default tenant: {}", TestConfig.DEFAULT_TENANT);
        LOG.info("Admin tenant: {}", TestConfig.ADMIN_TENANT);

        TestConfig.configureRestAssured();

        if (Boolean.parseBoolean(System.getProperty("wiremock.enabled", "true"))) {
            try {
                WireMockManager.getInstance().start();
            } catch (Exception e) {
                LOG.warn("WireMock failed to start: {}. Tests requiring stubs will fail.", e.getMessage());
            }
        }
    }

    @Override
    public void onFinish(ISuite suite) {
        LOG.info("=== Conformance suite '{}' finished in {} ms ===",
                suite.getName(), Duration.between(SUITE_START, Instant.now()).toMillis());
        writeJsonSummary();
    }

    @Override
    public void onTestStart(ITestResult result) {
        String className = result.getTestClass().getName();
        CLASS_STARTS.putIfAbsent(className, Instant.now());
        LOG.info("[START] {}.{}", className, result.getMethod().getMethodName());
    }

    @Override
    public void onTestSuccess(ITestResult result) {
        long duration = result.getEndMillis() - result.getStartMillis();
        RESULTS.add(new SuiteResult(
                result.getTestContext().getSuite().getName(),
                result.getTestClass().getName(),
                result.getMethod().getMethodName(),
                "PASS",
                duration,
                null
        ));
        LOG.info("[PASS] {}.{} in {} ms",
                result.getTestClass().getName(), result.getMethod().getMethodName(), duration);
    }

    @Override
    public void onTestFailure(ITestResult result) {
        long duration = result.getEndMillis() - result.getStartMillis();
        Throwable t = result.getThrowable();
        String msg = t == null ? "<no exception>" : (t.getClass().getSimpleName() + ": " + t.getMessage());
        RESULTS.add(new SuiteResult(
                result.getTestContext().getSuite().getName(),
                result.getTestClass().getName(),
                result.getMethod().getMethodName(),
                "FAIL",
                duration,
                msg
        ));
        LOG.error("[FAIL] {}.{} in {} ms: {}",
                result.getTestClass().getName(), result.getMethod().getMethodName(), duration, msg);
    }

    @Override
    public void onTestSkipped(ITestResult result) {
        RESULTS.add(new SuiteResult(
                result.getTestContext().getSuite().getName(),
                result.getTestClass().getName(),
                result.getMethod().getMethodName(),
                "SKIPPED",
                0L,
                result.getSkipCausedBy() == null ? "skipped" : result.getSkipCausedBy().getMessage()
        ));
        LOG.warn("[SKIP] {}.{}",
                result.getTestClass().getName(), result.getMethod().getMethodName());
    }

    @Override
    public void onStart(ITestContext context) {
        // No-op: per-class setup is handled in onStart(ISuite).
    }

    @Override
    public void onFinish(ITestContext context) {
        // Aggregate per-class results printed at end of suite.
    }

    private void writeJsonSummary() {
        File target = new File("target");
        if (!target.exists() && !target.mkdirs()) {
            LOG.warn("Could not create target/ for summary");
            return;
        }
        File out = new File(target, "conformance-summary.json");
        try (FileWriter w = new FileWriter(out)) {
            w.write("{");
            w.write("\"generatedAt\":\"" + Instant.now() + "\",");
            w.write("\"baseUrl\":\"" + TestConfig.BASE_URL + "\",");
            w.write("\"defaultTenant\":\"" + TestConfig.DEFAULT_TENANT + "\",");
            w.write("\"results\":[");
            boolean first = true;
            int passed = 0, failed = 0, skipped = 0;
            for (SuiteResult r : RESULTS) {
                if (!first) w.write(",");
                first = false;
                w.write("{");
                w.write("\"suite\":\"" + esc(r.suiteName) + "\",");
                w.write("\"class\":\"" + esc(r.className) + "\",");
                w.write("\"method\":\"" + esc(r.methodName) + "\",");
                w.write("\"status\":\"" + r.status + "\",");
                w.write("\"durationMs\":" + r.durationMs);
                if (r.failureMessage != null) {
                    w.write(",\"failure\":\"" + esc(r.failureMessage) + "\"");
                }
                w.write("}");
                if ("PASS".equals(r.status)) passed++;
                else if ("FAIL".equals(r.status)) failed++;
                else skipped++;
            }
            w.write("],");
            w.write("\"totals\":{\"passed\":" + passed + ",\"failed\":" + failed + ",\"skipped\":" + skipped + "}");
            w.write("}");
            LOG.info("Conformance summary written to {} (passed={}, failed={}, skipped={})",
                    out.getAbsolutePath(), passed, failed, skipped);
        } catch (IOException e) {
            LOG.error("Failed to write summary", e);
        }
    }

    private static String esc(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ");
    }

    public static List<SuiteResult> getResults() {
        return new ArrayList<>(RESULTS);
    }
}
