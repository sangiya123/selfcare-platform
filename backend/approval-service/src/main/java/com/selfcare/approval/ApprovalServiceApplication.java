package com.selfcare.approval;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Approval Service — four-eyes approval workflow for high-risk admin actions.
 *
 * Implements the selfcare platform's mandatory review process for the 9
 * high-risk actions defined in {@link com.selfcare.approval.domain.ApprovalAction}.
 *
 * Capabilities:
 * - Submit changes for approval (PENDING state, persisted)
 * - Approve / reject / cancel by reviewers and requesters
 * - Automatic expiry of stale PENDING requests (scheduled)
 * - Query by status, action, requester, approver
 * - Redis cache for pending-count metrics
 * - Audit-friendly immutable history of every decision
 *
 * Flow:
 *   1. Calling service (config, feature-flag, role) submits a request
 *   2. Requester can cancel; approver can approve or reject
 *   3. Approved request -> calling service applies the change
 *   4. Rejected / cancelled / expired -> calling service discards
 */
@SpringBootApplication
@EnableScheduling
@EnableJpaAuditing
@EnableJpaRepositories("com.selfcare.approval.repository")
@ComponentScan(basePackages = {
    "com.selfcare.approval",
    "com.selfcare.platform.common"
})
public class ApprovalServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApprovalServiceApplication.class, args);
    }
}
