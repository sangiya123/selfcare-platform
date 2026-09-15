package com.selfcare.platform.common.adapter;

import java.time.Instant;
import java.util.List;

/**
 * Document Provider contract for telco and insurance industry packs.
 *
 * Each operator (Dialog, Hutch, Airtel, AIA, ...) implements this interface
 * to expose canonical document upload / download / list capabilities used
 * by the document-service, claim-management, KYC, and BFFs.
 *
 * <ul>
 *   <li>Upload a document (ID, photo, claim evidence, policy document)</li>
 *   <li>List all documents for a customer</li>
 *   <li>Get a signed / pre-signed download URL for a document</li>
 *   <li>Delete a document (subject to retention rules)</li>
 * </ul>
 *
 * This interface is designed to work across both telco and insurance packs.
 * Use the {@code category} field to distinguish document types.
 *
 * All methods take {@code tenantId} as the first argument.
 */
public interface DocumentProvider extends ApiAdapter {

    /**
     * Upload a document.
     *
     * @param tenantId the tenant identifier (e.g. {@code "aia-lk"})
     * @param customerId the customer identifier
     * @param fileName the file name
     * @param content the binary file content
     * @param mimeType the MIME type (e.g. {@code application/pdf})
     * @param metadata additional metadata (category, claimId, policyId, ...)
     * @return upload result with document ID
     */
    UploadResult uploadDocument(String tenantId, String customerId,
                                String fileName, byte[] content, String mimeType,
                                DocumentMetadata metadata);

    /**
     * List all documents for a customer, optionally filtered by category.
     *
     * @param tenantId the tenant identifier
     * @param customerId the customer identifier
     * @param category optional category filter (null for all)
     * @return list of document summaries
     */
    List<DocumentSummary> listDocuments(String tenantId, String customerId, String category);

    /**
     * Get a temporary / pre-signed download URL for a document.
     *
     * @param tenantId the tenant identifier
     * @param documentId the document ID
     * @return signed URL valid for a limited time; null if document is not available
     */
    String getDownloadUrl(String tenantId, String documentId);

    /**
     * Delete a document. Subject to retention / business rules in the
     * underlying system — some documents cannot be deleted (e.g. legally
     * retained claim evidence).
     *
     * @param tenantId the tenant identifier
     * @param documentId the document ID
     * @return true if deletion succeeded
     */
    boolean deleteDocument(String tenantId, String documentId);

    // ================================================================
    // Domain Objects
    // ================================================================

    /**
     * Metadata for an uploaded document.
     */
    record DocumentMetadata(
            String category,        // ID_PROOF, PHOTO, CLAIM_EVIDENCE, POLICY, KYC, BILL, OTHER
            String relatedEntityId,  // claimId, policyId, ticketId, etc.
            String relatedEntityType,// CLAIM, POLICY, TICKET, ...
            String description,
            String uploadedBy
    ) {}

    /**
     * Result of a document upload.
     */
    record UploadResult(
            boolean success,
            String documentId,
            String fileName,
            String mimeType,
            Long sizeBytes,
            Instant uploadedAt,
            String failureReason
    ) {}

    /**
     * Summary of an uploaded document.
     */
    record DocumentSummary(
            String documentId,
            String customerId,
            String fileName,
            String mimeType,
            Long sizeBytes,
            String category,
            String relatedEntityId,
            String relatedEntityType,
            Instant uploadedAt,
            Instant expiresAt
    ) {}
}
