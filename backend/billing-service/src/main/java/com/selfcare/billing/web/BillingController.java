package com.selfcare.billing.web;

import com.selfcare.billing.domain.Bill;
import com.selfcare.billing.domain.BillItem;
import com.selfcare.billing.service.BillPdfService;
import com.selfcare.billing.service.BillingService;
import com.selfcare.billing.service.LateFeeService;
import com.selfcare.platform.common.dto.PaginationRequest;
import com.selfcare.platform.common.dto.PaginationResponse;
import com.selfcare.platform.common.tenant.TenantContext;
import com.selfcare.platform.common.web.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Billing REST API.
 *
 * Endpoints:
 *   GET  /api/v1/bills                                  — List bills (paginated)
 *   GET  /api/v1/bills/{billId}                         — Bill detail
 *   GET  /api/v1/bills/current                          — Current outstanding bill
 *   GET  /api/v1/bills/{billId}/items                   — Bill line items
 *   GET  /api/v1/bills/{billId}/statement               — Generate bill statement
 *   POST /api/v1/bills/{billId}/late-fee                — Apply late fee now
 *   GET  /api/v1/bills/{billId}/late-fee/preview        — Preview late fee without applying
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Billing", description = "Bills, invoices, documents, line items, late fees")
public class BillingController {

    private final BillingService billingService;
    private final BillPdfService billPdfService;
    private final LateFeeService lateFeeService;

    @GetMapping("/bills")
    @Operation(summary = "List bills")
    public ResponseEntity<ApiResponse<PaginationResponse<Bill>>> listBills(
            @RequestParam(required = false) String connectionId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        PaginationResponse<Bill> response = billingService.listBills(
                connectionId, status, fromDate, toDate, PaginationRequest.builder().page(page).size(size).build());
        return ResponseEntity.ok(ApiResponse.of(response, TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/bills/{billId}")
    @Operation(summary = "Get bill detail")
    public ResponseEntity<ApiResponse<Bill>> getBill(@PathVariable String billId) {
        Bill bill = billingService.getBill(billId);
        return ResponseEntity.ok(ApiResponse.of(bill, TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/bills/current")
    @Operation(summary = "Get current outstanding bill for a connection")
    public ResponseEntity<ApiResponse<Bill>> getCurrentBill(@RequestParam String connectionId) {
        Bill bill = billingService.getCurrentBill(connectionId);
        return ResponseEntity.ok(ApiResponse.of(bill, TenantContext.get().getCorrelationId()));
    }

    @GetMapping("/bills/{billId}/items")
    @Operation(summary = "List bill line items")
    public ResponseEntity<ApiResponse<List<BillItem>>> getBillItems(@PathVariable String billId) {
        return ResponseEntity.ok(ApiResponse.of(
                billingService.getBillItems(billId),
                TenantContext.get().getCorrelationId()));
    }

    @GetMapping(value = "/bills/{billId}/statement", produces = "text/plain; charset=utf-8")
    @Operation(summary = "Generate bill statement as plain text")
    public ResponseEntity<byte[]> getStatement(@PathVariable String billId) {
        Bill bill = billingService.getBill(billId);
        byte[] body = billPdfService.generateStatement(bill);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/plain; charset=utf-8"));
        headers.setContentDispositionFormData("attachment",
                "bill-" + (bill.getBillNumber() != null ? bill.getBillNumber() : billId) + ".txt");
        return ResponseEntity.ok().headers(headers).body(body);
    }

    @GetMapping("/bills/{billId}/late-fee/preview")
    @Operation(summary = "Preview late fee for a bill (does not apply)")
    public ResponseEntity<ApiResponse<java.math.BigDecimal>> previewLateFee(@PathVariable String billId) {
        return ResponseEntity.ok(ApiResponse.of(
                lateFeeService.calculateLateFee(billId),
                TenantContext.get().getCorrelationId()));
    }

    @PostMapping("/bills/{billId}/late-fee")
    @Operation(summary = "Apply late fee to a bill")
    public ResponseEntity<ApiResponse<BillItem>> applyLateFee(@PathVariable String billId) {
        BillItem item = lateFeeService.applyLateFee(billId);
        return ResponseEntity.ok(ApiResponse.of(item, TenantContext.get().getCorrelationId()));
    }
}
