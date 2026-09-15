package com.selfcare.platform.common.adapter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Billing Provider contract for telco industry packs.
 *
 * Each operator (Dialog, Hutch, Airtel, ...) implements this interface to
 * expose billing information required by the billing-service and BFFs:
 *
 * <ul>
 *   <li>Invoice / bill list for a customer</li>
 *   <li>Bill details including line items</li>
 *   <li>Outstanding balance and total amount due</li>
 *   <li>Bill PDF download URL</li>
 * </ul>
 *
 * This interface covers postpaid billing. Prepaid balance is handled by
 * {@link com.selfcare.platform.common.adapter.BalanceProvider}.
 *
 * All methods take {@code tenantId} as the first argument.
 * Upstream config is loaded at runtime from {@link com.selfcare.platform.common.tenant.TenantConfigurationService}.
 */
public interface BillingProvider extends ApiAdapter {

    /**
     * Get all bills / invoices for a customer.
     *
     * @param tenantId the tenant identifier (e.g. {@code "dialog-lk"})
     * @param customerId the customer identifier
     * @return list of bills, newest first; never null
     */
    List<Bill> getBills(String tenantId, String customerId);

    /**
     * Get a specific bill by its ID.
     *
     * @param tenantId the tenant identifier
     * @param billId the bill identifier
     * @return the bill, or null if not found
     */
    Bill getBill(String tenantId, String billId);

    /**
     * Get the total outstanding amount for a customer.
     *
     * @param tenantId the tenant identifier
     * @param customerId the customer identifier
     * @return total outstanding, or null if not applicable (e.g. prepaid-only)
     */
    OutstandingAmount getOutstandingAmount(String tenantId, String customerId);

    /**
     * Get the PDF download URL for a bill.
     *
     * @param tenantId the tenant identifier
     * @param billId the bill identifier
     * @return signed or pre-signed URL; null if PDF is not available
     */
    String getBillPdfUrl(String tenantId, String billId);

    /**
     * Get bill line items for a specific bill.
     *
     * @param tenantId the tenant identifier
     * @param billId the bill identifier
     * @return list of line items
     */
    List<BillLineItem> getBillLineItems(String tenantId, String billId);

    /**
     * Get the payment plan / installment schedule for a bill (if applicable).
     *
     * @param tenantId the tenant identifier
     * @param billId the bill identifier
     * @return payment plan, or null
     */
    PaymentPlan getPaymentPlan(String tenantId, String billId);

    // ================================================================
    // Domain Objects
    // ================================================================

    /**
     * A bill / invoice.
     */
    record Bill(
            String billId,
            String customerId,
            String billNumber,
            String billingPeriod,
            LocalDate issueDate,
            LocalDate dueDate,
            BigDecimal totalAmount,
            BigDecimal amountPaid,
            BigDecimal amountDue,
            String currency,
            BillStatus status,       // UNPAID, PARTIAL, PAID, OVERDUE, CANCELLED
            String pdfUrl
    ) {}

    enum BillStatus {
        UNPAID, PARTIAL, PAID, OVERDUE, CANCELLED
    }

    /**
     * A line item within a bill.
     */
    record BillLineItem(
            String lineItemId,
            String billId,
            String description,
            String category,         // USAGE, SUBSCRIPTION, DEVICE, OTHER
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal totalPrice,
            BigDecimal taxAmount,
            String taxRate,
            LocalDate servicePeriodFrom,
            LocalDate servicePeriodTo
    ) {}

    /**
     * Total outstanding amount for a customer.
     */
    record OutstandingAmount(
            String customerId,
            BigDecimal totalDue,
            BigDecimal overdueAmount,
            LocalDate oldestDueDate,
            Integer overdueCount,
            String currency
    ) {}

    /**
     * Payment plan / installment schedule for a bill.
     */
    record PaymentPlan(
            String planId,
            String billId,
            int totalInstallments,
            int installmentsPaid,
            BigDecimal installmentAmount,
            LocalDate nextDueDate,
            PaymentPlanStatus status
    ) {}

    enum PaymentPlanStatus {
        ACTIVE, COMPLETED, DEFAULTED, CANCELLED
    }
}
