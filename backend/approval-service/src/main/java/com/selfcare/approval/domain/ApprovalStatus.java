package com.selfcare.approval.domain;

/**
 * Lifecycle state of an approval request.
 *
 * State transitions:
 *   PENDING  -> APPROVED  (after positive decision)
 *   PENDING  -> REJECTED  (after negative decision)
 *   PENDING  -> CANCELLED (by requester before decision)
 *   PENDING  -> EXPIRED   (by scheduled job after expiry threshold)
 */
public enum ApprovalStatus {

    /** Awaiting an approver's decision. */
    PENDING,

    /** Approved by a reviewer. The associated change may proceed. */
    APPROVED,

    /** Rejected by a reviewer. The associated change must not proceed. */
    REJECTED,

    /** Withdrawn by the original requester. */
    CANCELLED,

    /** Automatically expired because the approval window elapsed. */
    EXPIRED
}
