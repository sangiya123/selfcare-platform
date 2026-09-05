package com.omobio.reporting.service;

import com.omobio.reporting.catalog.ReportCatalog;
import com.omobio.reporting.catalog.ReportCategory;
import com.omobio.reporting.catalog.ReportDefinition;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service for accessing the canonical report catalog.
 * All report metadata is served from this component — the catalog is
 * immutable and lives in code (per ADR-004 source-of-truth principle).
 */
@Service
public class ReportCatalogService {

    private final ReportCatalog catalog;

    public ReportCatalogService(ReportCatalog catalog) {
        this.catalog = catalog;
    }

    /** Returns all report definitions. */
    public List<ReportDefinition> getAllReports() {
        return catalog.getAllReports();
    }

    /** Returns reports in a specific category. */
    public List<ReportDefinition> getByCategory(ReportCategory category) {
        return catalog.getAllReports().stream()
            .filter(r -> r.getCategory() == category)
            .toList();
    }

    /** Returns a single report by ID, or empty if not found. */
    public Optional<ReportDefinition> getById(String id) {
        return catalog.getAllReports().stream()
            .filter(r -> r.getId().equals(id))
            .findFirst();
    }

    /** Returns all report IDs — used for validation. */
    public List<String> getAllReportIds() {
        return catalog.getAllReports().stream()
            .map(ReportDefinition::getId)
            .toList();
    }

    /** Returns the count of reports in each category. */
    public List<CategoryCount> getCountsByCategory() {
        return catalog.getAllReports().stream()
            .collect(java.util.stream.Collectors.groupingBy(
                ReportDefinition::getCategory,
                java.util.stream.Collectors.counting()))
            .entrySet().stream()
            .map(e -> new CategoryCount(e.getKey(), e.getValue().intValue()))
            .sorted((a, b) -> a.category().name().compareTo(b.category().name()))
            .toList();
    }

    public record CategoryCount(ReportCategory category, int count) {}
}
