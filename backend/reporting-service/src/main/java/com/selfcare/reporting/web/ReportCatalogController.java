package com.selfcare.reporting.web;

import com.selfcare.reporting.catalog.ReportCategory;
import com.selfcare.reporting.catalog.ReportDefinition;
import com.selfcare.reporting.service.ReportCatalogService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * Public REST API for the canonical report catalog.
 * Used by:
 * - Selfcare Studio admin UI (browse + select reports)
 * - AI reporting assistant (semantic label lookup)
 * - External integrations (catalog export)
 */
@RestController
@RequestMapping("/api/v1/reports/catalog")
public class ReportCatalogController {

    private final ReportCatalogService service;

    public ReportCatalogController(ReportCatalogService service) {
        this.service = service;
    }

    /** List all reports, optionally filtered by category. */
    @GetMapping
    public List<ReportDefinition> list(
            @RequestParam(value = "category", required = false) ReportCategory category,
            @RequestParam(value = "search", required = false) String search) {
        List<ReportDefinition> reports = category == null
            ? service.getAllReports()
            : service.getByCategory(category);

        if (search != null && !search.isBlank()) {
            String lower = search.toLowerCase();
            reports = reports.stream()
                .filter(r -> r.getName().toLowerCase().contains(lower)
                    || r.getId().contains(lower)
                    || r.getDescription().toLowerCase().contains(lower))
                .toList();
        }
        return reports;
    }

    /** Get a single report by ID. */
    @GetMapping("/{reportId}")
    public ReportDefinition get(@PathVariable("reportId") String reportId) {
        return service.getById(reportId)
            .orElseThrow(() -> new com.selfcare.platform.common.web.NotFoundException(
                "Report '" + reportId + "' not found"));
    }

    /** Get counts per category. */
    @GetMapping("/counts")
    public Map<String, Integer> counts() {
        return service.getCountsByCategory().stream()
            .collect(java.util.stream.Collectors.toMap(
                cc -> cc.category().name(),
                ReportCatalogService.CategoryCount::count));
    }

    /** Get all report IDs (for AI assistant / validation). */
    @GetMapping("/ids")
    public List<String> ids() {
        return service.getAllReportIds();
    }
}
