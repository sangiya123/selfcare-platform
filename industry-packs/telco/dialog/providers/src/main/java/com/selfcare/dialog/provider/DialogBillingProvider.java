package com.selfcare.dialog.provider;

import com.selfcare.platform.common.adapter.ApiAdapter;
import com.selfcare.platform.common.adapter.BillingProvider;
import com.selfcare.platform.common.adapter.RegisterAdapter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Dialog Billing Provider — fetches billing information from Dialog BSS.
 *
 * <p>Implements the canonical {@link BillingProvider} contract from platform-common.
 * Returns postpaid bills, line items, outstanding amounts, and PDF download URLs
 * for the billing-service and BFFs.</p>
 *
 * <p>For prepaid balance see {@link DialogBalanceProvider}.</p>
 *
 * <p>Per-tenant config (BSS base URL, API key) is loaded from MongoDB via
 * {@link DialogHttpClient}. Configure via Selfcare Studio admin:
 * Integrations &gt; Dialog BSS.</p>
 */
@Slf4j
@Component
@RegisterAdapter(tenantId = "dialog-lk", providerInterface = BillingProvider.class)
@RequiredArgsConstructor
public class DialogBillingProvider implements ApiAdapter, BillingProvider {

    private static final String INTEGRATION_TYPE = "DIALOG_BSS";
    private static final String ADAPTER_ID = "dialog-lk";

    private final DialogHttpClient httpClient;

    @Override
    public String getAdapterId() {
        return ADAPTER_ID;
    }

    // ================================================================
    // BillingProvider implementation
    // ================================================================

    @Override
    public List<Bill> getBills(String tenantId, String customerId) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        DialogOperator op = new DialogOperator(cfg);
        if (!op.configured()) {
            log.warn("Dialog BSS not configured for tenant={}", tenantId);
            return List.of();
        }

        String path = op.path("billing.bills", "/billing/customer/{customer}/bills",
                Map.of("customer", customerId));
        String billsField = op.field("billing.bills", "bills");
        String currency = op.currency();
        try {
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(), path, null, null, cfg.apiKey());
            if (data == null) return List.of();
            Object billsObj = data.get(billsField);
            if (!(billsObj instanceof List<?> list)) return List.of();
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .<Bill>map(m -> new Bill(
                            str(m.get("billId"), null),
                            customerId,
                            str(m.get("billNumber"), null),
                            str(m.get("billingPeriod"), null),
                            parseLocalDate(m.get("issueDate")),
                            parseLocalDate(m.get("dueDate")),
                            toBd(m.get("totalAmount")),
                            toBd(m.get("amountPaid")),
                            toBd(m.get("amountDue")),
                            str(m.get("currency"), currency),
                            parseStatus((String) m.get("status")),
                            str(m.get("pdfUrl"), null)
                    ))
                    .toList();
        } catch (DialogApiException e) {
            log.error("Dialog getBills failed for tenant={} customer={}: {}",
                    tenantId, customerId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public Bill getBill(String tenantId, String billId) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        DialogOperator op = new DialogOperator(cfg);
        if (!op.configured()) {
            log.warn("Dialog BSS not configured for tenant={}", tenantId);
            return null;
        }

        String path = op.path("billing.bill", "/billing/bills/{bill}",
                Map.of("bill", billId));
        String currency = op.currency();
        try {
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(), path, null, null, cfg.apiKey());
            if (data == null) return null;
            return new Bill(
                    billId,
                    str(data.get("customerId"), null),
                    str(data.get("billNumber"), null),
                    str(data.get("billingPeriod"), null),
                    parseLocalDate(data.get("issueDate")),
                    parseLocalDate(data.get("dueDate")),
                    toBd(data.get("totalAmount")),
                    toBd(data.get("amountPaid")),
                    toBd(data.get("amountDue")),
                    str(data.get("currency"), currency),
                    parseStatus((String) data.get("status")),
                    str(data.get("pdfUrl"), null)
            );
        } catch (DialogApiException e) {
            log.error("Dialog getBill failed for tenant={} bill={}: {}",
                    tenantId, billId, e.getMessage());
            return null;
        }
    }

    @Override
    public OutstandingAmount getOutstandingAmount(String tenantId, String customerId) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        DialogOperator op = new DialogOperator(cfg);
        if (!op.configured()) {
            log.warn("Dialog BSS not configured for tenant={}", tenantId);
            return null;
        }

        String path = op.path("billing.outstanding", "/billing/customer/{customer}/outstanding",
                Map.of("customer", customerId));
        String currency = op.currency();
        try {
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(), path, null, null, cfg.apiKey());
            if (data == null) return null;
            return new OutstandingAmount(
                    customerId,
                    toBd(data.get("totalDue")),
                    toBd(data.get("overdueAmount")),
                    parseLocalDate(data.get("oldestDueDate")),
                    toInteger(data.get("overdueCount")),
                    str(data.get("currency"), currency)
            );
        } catch (DialogApiException e) {
            log.error("Dialog getOutstandingAmount failed for tenant={} customer={}: {}",
                    tenantId, customerId, e.getMessage());
            return null;
        }
    }

    @Override
    public String getBillPdfUrl(String tenantId, String billId) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        DialogOperator op = new DialogOperator(cfg);
        if (!op.configured()) {
            log.warn("Dialog BSS not configured for tenant={}", tenantId);
            return null;
        }

        String path = op.path("billing.pdf", "/billing/bills/{bill}/pdf",
                Map.of("bill", billId));
        try {
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(), path, null, null, cfg.apiKey());
            if (data == null) return null;
            return str(data.get("url"), null);
        } catch (DialogApiException e) {
            log.error("Dialog getBillPdfUrl failed for tenant={} bill={}: {}",
                    tenantId, billId, e.getMessage());
            return null;
        }
    }

    @Override
    public List<BillLineItem> getBillLineItems(String tenantId, String billId) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        DialogOperator op = new DialogOperator(cfg);
        if (!op.configured()) {
            log.warn("Dialog BSS not configured for tenant={}", tenantId);
            return List.of();
        }

        String path = op.path("billing.lineItems", "/billing/bills/{bill}/line-items",
                Map.of("bill", billId));
        String itemsField = op.field("billing.lineItems", "lineItems");
        try {
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(), path, null, null, cfg.apiKey());
            if (data == null) return List.of();
            Object itemsObj = data.get(itemsField);
            if (!(itemsObj instanceof List<?> list)) return List.of();
            return list.stream()
                    .filter(Map.class::isInstance)
                    .map(Map.class::cast)
                    .<BillLineItem>map(m -> new BillLineItem(
                            str(m.get("lineItemId"), null),
                            billId,
                            str(m.get("description"), null),
                            str(m.get("category"), null),
                            toInteger(m.get("quantity")),
                            toBd(m.get("unitPrice")),
                            toBd(m.get("totalPrice")),
                            toBd(m.get("taxAmount")),
                            str(m.get("taxRate"), null),
                            parseLocalDate(m.get("servicePeriodFrom")),
                            parseLocalDate(m.get("servicePeriodTo"))
                    ))
                    .toList();
        } catch (DialogApiException e) {
            log.error("Dialog getBillLineItems failed for tenant={} bill={}: {}",
                    tenantId, billId, e.getMessage());
            return List.of();
        }
    }

    @Override
    public PaymentPlan getPaymentPlan(String tenantId, String billId) {
        DialogHttpClient.DialogConfig cfg = httpClient.resolveConfig(tenantId, INTEGRATION_TYPE);
        DialogOperator op = new DialogOperator(cfg);
        if (!op.configured()) {
            log.warn("Dialog BSS not configured for tenant={}", tenantId);
            return null;
        }

        String path = op.path("billing.paymentPlan", "/billing/bills/{bill}/payment-plan",
                Map.of("bill", billId));
        try {
            Map<String, Object> data = httpClient.get(tenantId, cfg.baseUrl(), path, null, null, cfg.apiKey());
            if (data == null) return null;
            return new PaymentPlan(
                    str(data.get("planId"), null),
                    billId,
                    toInteger(data.get("totalInstallments")) != null ? toInteger(data.get("totalInstallments")) : 0,
                    toInteger(data.get("installmentsPaid")) != null ? toInteger(data.get("installmentsPaid")) : 0,
                    toBd(data.get("installmentAmount")),
                    parseLocalDate(data.get("nextDueDate")),
                    parsePlanStatus((String) data.get("status"))
            );
        } catch (DialogApiException e) {
            log.error("Dialog getPaymentPlan failed for tenant={} bill={}: {}",
                    tenantId, billId, e.getMessage());
            return null;
        }
    }

    // ================================================================
    // Helpers
    // ================================================================

    private String str(Object v, String fallback) {
        return v != null ? v.toString() : fallback;
    }

    private BigDecimal toBd(Object v) {
        if (v == null) return null;
        if (v instanceof BigDecimal bd) return bd;
        if (v instanceof Number n) return BigDecimal.valueOf(n.doubleValue());
        try { return new BigDecimal(v.toString()); } catch (NumberFormatException e) { return null; }
    }

    private Integer toInteger(Object v) {
        if (v == null) return null;
        if (v instanceof Number n) return n.intValue();
        return null;
    }

    private LocalDate parseLocalDate(Object v) {
        if (v == null) return null;
        if (v instanceof LocalDate ld) return ld;
        if (v instanceof String s) {
            try { return LocalDate.parse(s); } catch (Exception e) { return null; }
        }
        return null;
    }

    private BillStatus parseStatus(String s) {
        if (s == null) return BillStatus.UNPAID;
        try { return BillStatus.valueOf(s.toUpperCase()); } catch (Exception e) { return BillStatus.UNPAID; }
    }

    private PaymentPlanStatus parsePlanStatus(String s) {
        if (s == null) return PaymentPlanStatus.ACTIVE;
        try { return PaymentPlanStatus.valueOf(s.toUpperCase()); } catch (Exception e) { return PaymentPlanStatus.ACTIVE; }
    }
}
