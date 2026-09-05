package com.omobio.reporting;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

/**
 * Reporting Service — Operational and business reports.
 *
 * Report catalog:
 * - User/connection reports
 * - Payment/financial reports
 * - Campaign reports
 * - API/provider health
 * - App quality (crash, ANR)
 * - Audit/security reports
 * - AI reports
 *
 * Features:
 * - Saved/scheduled reports
 * - Export (CSV, Excel, PDF)
 * - Row-level operator scope
 * - Report-definition audit
 * - Metric glossary
 * - Large exports run async
 * - Semantic/reporting layer (not log-grep)
 *
 * Migration target: ports ActivityReportsController, ChargingHistoryController,
 * CdrController, FbCdrController, PeriodicReportController, ConnectionUsageController,
 * UsageHistoryController, SendDrgReportController, etc. (19 controllers, ~12,200 LOC)
 */
@SpringBootApplication
@ComponentScan(basePackages = {
    "com.omobio.reporting",
    "com.omobio.platform.common"
})
public class ReportingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ReportingServiceApplication.class, args);
    }
}