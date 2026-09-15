package com.selfcare.billing.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.selfcare.billing.domain.Bill;
import com.selfcare.billing.domain.BillItem;
import com.selfcare.billing.repository.BillItemRepository;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.tenant.TenantConfigurationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * Generates a plain-text bill statement as a downloadable file.
 *
 * In production this would render an HTML template → PDF via a
 * PDF library (iText, OpenPDF, or Flying Saucer). For now we emit
 * a structured text format that the mobile app can display natively,
 * and a content-type of "text/plain; charset=utf-8".
 *
 * The mobile client is responsible for displaying this content
 * in a formatted bill view. Object storage + CDN integration
 * (to persist the generated file) is handled by the content-service
 * and is out of scope for this service.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BillPdfService {

    private final BillItemRepository billItemRepository;
    private final ObjectMapper objectMapper;

    /**
     * Generates a plain-text bill statement for a bill.
     *
     * @param bill the bill to render
     * @return UTF-8 bytes of the statement
     */
    public byte[] generateStatement(Bill bill) {
        String tenantId = bill.getTenantId() != null ? bill.getTenantId() : TenantContext.get().getTenantId();

        List<BillItem> items = billItemRepository
                .findByTenantIdAndBillIdOrderByCreatedAtAsc(tenantId, bill.getBillId());

        ByteArrayOutputStream out = new ByteArrayOutputStream(4096);
        PrintWriter w = new PrintWriter(out, true, StandardCharsets.UTF_8);

        String currency = bill.getCurrency() != null ? bill.getCurrency() : "LKR";
        DateTimeFormatter df = DateTimeFormatter.ofPattern("dd MMM yyyy");

        // Header
        w.println("══════════════════════════════════════════════════════════════");
        w.println("                              selfcare SELF SERVICE");
        w.println("                             ACCOUNT STATEMENT");
        w.println("══════════════════════════════════════════════════════════════");
        w.println();
        w.printf("  Bill Number    : %s%n", nullSafe(bill.getBillNumber()));
        w.printf("  Bill Period    : %s  to  %s%n",
                bill.getBillingPeriodStart(),
                bill.getBillingPeriodEnd());
        w.printf("  Issue Date     : %s%n",
                bill.getIssueDate() != null ? bill.getIssueDate().format(df) : "N/A");
        w.printf("  Due Date       : %s%n",
                bill.getDueDate() != null ? bill.getDueDate().format(df) : "N/A");
        w.printf("  Connection ID  : %s%n", nullSafe(bill.getConnectionId()));
        w.printf("  Status         : %s%n", nullSafe(bill.getStatus()));
        w.println();
        w.println("──────────────────────────────────────────────────────────────");
        w.println("  CHARGES");
        w.println("──────────────────────────────────────────────────────────────");

        BigDecimal totalCharges = BigDecimal.ZERO;
        BigDecimal totalDiscounts = BigDecimal.ZERO;
        BigDecimal totalTax = BigDecimal.ZERO;

        for (BillItem item : items) {
            String prefix = switch (item.getItemType()) {
                case "CHARGE" -> "  ";
                case "DISCOUNT" -> "  -";
                case "TAX" -> "  ";
                case "FEE" -> "  ";
                default -> "  ";
            };

            w.printf("%s%-20s %-30s %12s%n",
                    prefix,
                    nullSafe(item.getCategory()),
                    nullSafe(item.getDescription()),
                    formatAmt(item.getAmount(), currency));
            totalCharges = totalCharges.add(
                    "CHARGE".equals(item.getItemType()) ? item.getAmount() : BigDecimal.ZERO);
            totalDiscounts = totalDiscounts.subtract(
                    "DISCOUNT".equals(item.getItemType()) ? item.getAmount() : BigDecimal.ZERO);
            totalTax = totalTax.add(
                    "TAX".equals(item.getItemType()) ? item.getAmount() : BigDecimal.ZERO);
        }

        w.println();
        w.println("──────────────────────────────────────────────────────────────");
        w.printf("  Subtotal (Charges) : %35s%n", formatAmt(totalCharges, currency));
        if (totalDiscounts.compareTo(BigDecimal.ZERO) > 0) {
            w.printf("  Discounts          : %35s%n", formatAmt(totalDiscounts.negate(), currency));
        }
        if (totalTax.compareTo(BigDecimal.ZERO) > 0) {
            w.printf("  Taxes              : %35s%n", formatAmt(totalTax, currency));
        }
        w.println("──────────────────────────────────────────────────────────────");
        w.printf("  TOTAL AMOUNT       : %35s%n", formatAmt(bill.getTotalAmount(), currency));
        if (bill.getPaidAmount() != null && bill.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            w.printf("  Amount Paid        : %35s%n", formatAmt(bill.getPaidAmount(), currency));
        }
        if (bill.getOutstandingAmount() != null) {
            w.printf("  Amount Due         : %35s%n", formatAmt(bill.getOutstandingAmount(), currency));
        }
        w.println("══════════════════════════════════════════════════════════════");
        w.println();
        w.println("  Please pay by the due date to avoid late payment charges.");
        w.println("  For queries, contact your service provider.");
        w.println();

        w.flush();
        return out.toByteArray();
    }

    private String nullSafe(String s) {
        return s != null ? s : "-";
    }

    private String formatAmt(BigDecimal amt, String currency) {
        if (amt == null) return currency + " 0.00";
        return String.format("%s %,.2f", currency, amt);
    }
}
