package com.selfcare.aia.provider;

import com.selfcare.platform.common.adapter.InsuranceProvider.*;
import com.selfcare.platform.common.domain.insurance.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Mock data provider for AIA Insurance.
 *
 * Generates realistic, deterministic mock data for each tenant when
 * {@code selfcare.aia.mock-mode=true} (default in dev/test environments).
 * All IDs are AIA-prefixed (AIA-XXXX), and values are tailored to
 * the tenant's country (currency, plan names, hospital names, etc.).
 *
 * State is held in-memory in {@link AIAInsuranceProvider.MockState}
 * so that sequential calls (e.g. submit then get) work correctly.
 */
public final class MockData {

    private MockData() { }

    // ================================================================
    // Policies
    // ================================================================

    public static List<InsurancePolicy> getPolicies(String tenantId, String customerId,
                                                     AIAInsuranceProvider.MockState state) {
        String polId1 = "AIA-" + state.policySeq.incrementAndGet();
        String polId2 = "AIA-" + state.policySeq.incrementAndGet();

        InsurancePolicy lifePolicy = buildLifePolicy(tenantId, customerId, polId1, state);
        InsurancePolicy healthPolicy = buildHealthPolicy(tenantId, customerId, polId2, state);
        state.policies.put(polId1, lifePolicy);
        state.policies.put(polId2, healthPolicy);
        return List.of(lifePolicy, healthPolicy);
    }

    public static InsurancePolicy getPolicy(String tenantId, String customerId, String policyId,
                                            AIAInsuranceProvider.MockState state) {
        return state.policies.get(policyId);
    }

    public static InsurancePolicy getPolicyByRef(String tenantId, String customerId,
                                                 String insurerPolicyRef,
                                                 AIAInsuranceProvider.MockState state) {
        return state.policies.values().stream()
                .filter(p -> insurerPolicyRef.equals(p.getInsurerPolicyRef()))
                .findFirst()
                .orElse(null);
    }

    private static InsurancePolicy buildLifePolicy(String tenantId, String customerId,
                                                    String polId, AIAInsuranceProvider.MockState state) {
        String policyNumber = switch (tenantId) {
            case "aia-sg" -> "SGLP-2024-" + random4();
            case "aia-th" -> "THLP-2024-" + random4();
            case "aia-my" -> "MYLP-2024-" + random4();
            case "aia-hk" -> "HKLP-2024-" + random4();
            case "aia-in" -> "INLP-2024-" + random4();
            default -> "LKR-LP-2024-" + random4(); // aia-lk
        };
        BigDecimal sumAssured = switch (tenantId) {
            case "aia-sg" -> new BigDecimal("250000");
            case "aia-hk" -> new BigDecimal("300000");
            case "aia-in" -> new BigDecimal("2500000");
            case "aia-my" -> new BigDecimal("150000");
            case "aia-th" -> new BigDecimal("800000");
            default -> new BigDecimal("5000000"); // LKR
        };
        BigDecimal premiumAmount = switch (tenantId) {
            case "aia-sg" -> new BigDecimal("450.00");
            case "aia-hk" -> new BigDecimal("680.00");
            case "aia-in" -> new BigDecimal("5500.00");
            case "aia-my" -> new BigDecimal("320.00");
            case "aia-th" -> new BigDecimal("1800.00");
            default -> new BigDecimal("12000.00"); // LKR
        };
        return InsurancePolicy.builder()
                .id(polId)
                .tenantId(tenantId)
                .customerId(customerId)
                .policyNumber(policyNumber)
                .insurerPolicyRef("AIA-Ref-" + random6())
                .type(InsurancePolicy.PolicyType.LIFE)
                .status(InsurancePolicy.PolicyStatus.ACTIVE)
                .insurerStatus("In Force")
                .holder(InsurancePolicy.PolicyHolder.builder()
                        .customerId(customerId)
                        .fullName("Kamal Perera")
                        .nic("198801234567")
                        .dateOfBirth("1988-03-15")
                        .gender("Male")
                        .mobile(state.phonePrefix + "712345678")
                        .email("kamal.perera@example.com")
                        .address("123 Galle Road, " + state.countryName)
                        .build())
                .sumAssured(sumAssured)
                .premiumAmount(premiumAmount)
                .premiumFrequency("MONTHLY")
                .currency(state.currency)
                .coverageAmount(sumAssured)
                .coverageEndDate(LocalDate.of(2035, 3, 15))
                .maturityDate(LocalDate.of(2045, 3, 15))
                .planName("AIA SmartLife Plus")
                .planCode("SLP-2022")
                .productCode("LIFE-SLP")
                .accumulatedValue(new BigDecimal("85000"))
                .totalPremiumsPaid(new BigDecimal("420000"))
                .lastPremiumPaid(new BigDecimal(premiumAmount.toString()))
                .policyStartDate(LocalDate.of(2020, 3, 15))
                .policyEndDate(LocalDate.of(2045, 3, 15))
                .nextPremiumDueDate(LocalDate.now().plusMonths(1).withDayOfMonth(15))
                .renewalDate(LocalDate.of(2025, 3, 15))
                .beneficiaryIds(List.of("AIA-" + state.benefSeq.incrementAndGet()))
                .metadata(Map.of("riders", "CI,DTH,PDB", "paymentMethod", "AUTO_DEBIT"))
                .build();
    }

    private static InsurancePolicy buildHealthPolicy(String tenantId, String customerId,
                                                      String polId, AIAInsuranceProvider.MockState state) {
        String policyNumber = switch (tenantId) {
            case "aia-sg" -> "SGHP-2023-" + random4();
            case "aia-th" -> "THHP-2023-" + random4();
            case "aia-my" -> "MYHP-2023-" + random4();
            case "aia-hk" -> "HKHP-2023-" + random4();
            case "aia-in" -> "INHP-2023-" + random4();
            default -> "LKR-HP-2023-" + random4();
        };
        BigDecimal sumAssured = switch (tenantId) {
            case "aia-sg" -> new BigDecimal("500000");
            case "aia-hk" -> new BigDecimal("600000");
            case "aia-in" -> new BigDecimal("5000000");
            case "aia-my" -> new BigDecimal("300000");
            case "aia-th" -> new BigDecimal("1500000");
            default -> new BigDecimal("10000000");
        };
        String hospital = switch (tenantId) {
            case "aia-sg" -> "Mount Elizabeth Hospital";
            case "aia-hk" -> "Hong Kong Sanatorium";
            case "aia-in" -> "Apollo Hospitals";
            case "aia-my" -> "Prince Court Medical Centre";
            case "aia-th" -> "Bumrungrad International Hospital";
            default -> "Nawaloka Hospital PLC";
        };
        return InsurancePolicy.builder()
                .id(polId)
                .tenantId(tenantId)
                .customerId(customerId)
                .policyNumber(policyNumber)
                .insurerPolicyRef("AIA-HRef-" + random6())
                .type(InsurancePolicy.PolicyType.HEALTH)
                .status(InsurancePolicy.PolicyStatus.ACTIVE)
                .insurerStatus("In Force")
                .holder(InsurancePolicy.PolicyHolder.builder()
                        .customerId(customerId)
                        .fullName("Kamal Perera")
                        .nic("198801234567")
                        .dateOfBirth("1988-03-15")
                        .gender("Male")
                        .mobile(state.phonePrefix + "712345678")
                        .email("kamal.perera@example.com")
                        .address("123 Galle Road, " + state.countryName)
                        .build())
                .sumAssured(sumAssured)
                .premiumAmount(switch (tenantId) {
                    case "aia-sg" -> new BigDecimal("280.00");
                    case "aia-hk" -> new BigDecimal("420.00");
                    case "aia-in" -> new BigDecimal("3500.00");
                    case "aia-my" -> new BigDecimal("190.00");
                    case "aia-th" -> new BigDecimal("1100.00");
                    default -> new BigDecimal("8500.00");
                })
                .premiumFrequency("MONTHLY")
                .currency(state.currency)
                .coverageAmount(sumAssured)
                .coverageEndDate(LocalDate.of(2030, 1, 1))
                .maturityDate(null)
                .planName("AIA HealthShield Gold Max")
                .planCode("HSGM-2023")
                .productCode("HEALTH-HSGM")
                .accumulatedValue(null)
                .totalPremiumsPaid(new BigDecimal("180000"))
                .lastPremiumPaid(new BigDecimal("8500"))
                .policyStartDate(LocalDate.of(2023, 1, 1))
                .policyEndDate(LocalDate.of(2030, 1, 1))
                .nextPremiumDueDate(LocalDate.now().plusMonths(1).withDayOfMonth(1))
                .renewalDate(LocalDate.of(2025, 1, 1))
                .beneficiaryIds(List.of("AIA-" + state.benefSeq.incrementAndGet()))
                .metadata(Map.of("networkHospital", hospital, "coPay", "20%"))
                .build();
    }

    // ================================================================
    // Renewals
    // ================================================================

    public static RenewalOffer initiateRenewal(String tenantId, String policyId,
                                               AIAInsuranceProvider.MockState state) {
        InsurancePolicy policy = state.policies.get(policyId);
        if (policy == null) return null;

        BigDecimal current = policy.getPremiumAmount();
        BigDecimal newPremium = current.multiply(new BigDecimal("1.05"));
        BigDecimal newSum = policy.getSumAssured().multiply(new BigDecimal("1.10"));

        return new RenewalOffer(
                "AIA-REN-" + random6(),
                policyId,
                1,
                newPremium,
                newSum,
                LocalDate.now().plusDays(30),
                LocalDate.now().plusYears(1).plusDays(30),
                Map.of("option_a", "Extend 1 year at new premium",
                       "option_b", "Increase sum assured by 10%",
                       "option_c", "Convert to paid-up"));
    }

    public static RenewalResult confirmRenewal(String tenantId, String policyId,
                                                String renewalOption,
                                                AIAInsuranceProvider.MockState state) {
        String newPolNum = "AIA-RENEW-" + random6();
        return new RenewalResult(true, newPolNum, null);
    }

    // ================================================================
    // Claims
    // ================================================================

    public static List<InsuranceClaim> getClaims(String tenantId, String customerId,
                                                  AIAInsuranceProvider.MockState state) {
        String claimId = "AIA-" + state.claimSeq.incrementAndGet();
        String polId = state.policies.isEmpty() ? null : state.policies.keySet().iterator().next();
        String claimNumber = "CLM-" + random4();
        Instant now = Instant.now();

        InsuranceClaim claim = InsuranceClaim.builder()
                .id(claimId)
                .tenantId(tenantId)
                .customerId(customerId)
                .policyId(polId)
                .policyNumber("LKR-LP-2024-1234")
                .claimNumber(claimNumber)
                .insurerClaimRef("AIA-CLM-" + random6())
                .type(InsuranceClaim.ClaimType.HOSPITALISATION)
                .status(InsuranceClaim.ClaimStatus.UNDER_REVIEW)
                .incidentDate(LocalDate.now().minusDays(5))
                .reportedDate(LocalDate.now().minusDays(3))
                .incidentDescription("Hospitalisation for surgery")
                .claimedAmount(switch (tenantId) {
                    case "aia-sg" -> new BigDecimal("12000");
                    case "aia-hk" -> new BigDecimal("15000");
                    case "aia-in" -> new BigDecimal("120000");
                    case "aia-my" -> new BigDecimal("8000");
                    case "aia-th" -> new BigDecimal("45000");
                    default -> new BigDecimal("350000");
                })
                .currency(state.currency)
                .hospitalName("Nawaloka Hospital PLC")
                .admissionDate(LocalDate.now().minusDays(7))
                .dischargeDate(LocalDate.now().minusDays(5))
                .diagnosis("Acute appendicitis")
                .diagnosisCode("K35")
                .documentIds(List.of("AIA-" + state.docSeq.incrementAndGet()))
                .activities(List.of(
                        InsuranceClaim.ClaimActivity.builder()
                                .timestamp(now.minus(3, ChronoUnit.DAYS))
                                .action("Claim submitted")
                                .actor("CUSTOMER")
                                .notes("Online claim submitted via selfcare portal")
                                .build(),
                        InsuranceClaim.ClaimActivity.builder()
                                .timestamp(now.minus(2, ChronoUnit.DAYS))
                                .action("Documents received")
                                .actor("INSURER")
                                .notes("All required documents received")
                                .build(),
                        InsuranceClaim.ClaimActivity.builder()
                                .timestamp(now.minus(1, ChronoUnit.DAYS))
                                .action("Under medical review")
                                .actor("INSURER")
                                .notes("Assigned to medical team for assessment")
                                .build()))
                .build();
        state.claims.put(claimId, claim);
        return List.of(claim);
    }

    public static InsuranceClaim getClaim(String tenantId, String customerId, String claimId,
                                           AIAInsuranceProvider.MockState state) {
        return state.claims.get(claimId);
    }

    public static List<InsuranceClaim.ClaimActivity> getClaimActivities(String tenantId,
                                                                          String claimId,
                                                                          AIAInsuranceProvider.MockState state) {
        InsuranceClaim claim = state.claims.get(claimId);
        return claim != null ? claim.getActivities() : List.of();
    }

    public static ClaimSubmitResult submitClaim(String tenantId, String policyId,
                                                  ClaimSubmission submission,
                                                  AIAInsuranceProvider.MockState state) {
        String claimId = "AIA-" + state.claimSeq.incrementAndGet();
        String claimNumber = "CLM-" + random4();
        Instant now = Instant.now();

        InsuranceClaim claim = InsuranceClaim.builder()
                .id(claimId)
                .tenantId(tenantId)
                .customerId(state.policies.get(policyId) != null
                        ? state.policies.get(policyId).getCustomerId() : "MOCK-CUST")
                .policyId(policyId)
                .policyNumber(state.policies.get(policyId) != null
                        ? state.policies.get(policyId).getPolicyNumber() : "MOCK-POL")
                .claimNumber(claimNumber)
                .insurerClaimRef("AIA-CLM-" + random6())
                .type(submission.type())
                .status(InsuranceClaim.ClaimStatus.SUBMITTED)
                .incidentDate(submission.incidentDate())
                .reportedDate(LocalDate.now())
                .incidentDescription(submission.description())
                .claimedAmount(submission.claimedAmount())
                .currency(state.currency)
                .hospitalName(submission.hospitalName())
                .admissionDate(submission.admissionDate())
                .dischargeDate(submission.dischargeDate())
                .diagnosis(submission.diagnosis())
                .vehicleRegNumber(submission.vehicleRegNumber())
                .accidentLocation(submission.accidentLocation())
                .policeReportNumber(submission.policeReportNumber())
                .activities(List.of(
                        InsuranceClaim.ClaimActivity.builder()
                                .timestamp(now)
                                .action("Claim submitted")
                                .actor("CUSTOMER")
                                .notes("Submitted via selfcare portal")
                                .build()))
                .build();
        state.claims.put(claimId, claim);
        return new ClaimSubmitResult(true, claimId, claimNumber,
                InsuranceClaim.ClaimStatus.SUBMITTED, null);
    }

    public static DocumentUploadResult uploadClaimDocument(String tenantId, String claimId,
                                                            String fileName,
                                                            AIAInsuranceProvider.MockState state) {
        String docId = "AIA-" + state.docSeq.incrementAndGet();
        InsuranceClaim claim = state.claims.get(claimId);
        if (claim != null) {
            List<String> docs = new ArrayList<>(
                    claim.getDocumentIds() != null ? claim.getDocumentIds() : List.of());
            docs.add(docId);
            claim.setDocumentIds(docs);
        }
        return new DocumentUploadResult(true, docId, fileName, null);
    }

    // ================================================================
    // Beneficiaries
    // ================================================================

    public static List<InsuranceBeneficiary> getBeneficiaries(String tenantId, String policyId,
                                                               AIAInsuranceProvider.MockState state) {
        String custId = state.policies.get(policyId) != null
                ? state.policies.get(policyId).getCustomerId() : "MOCK-CUST";
        String benefId = "AIA-" + state.benefSeq.incrementAndGet();
        String benefId2 = "AIA-" + state.benefSeq.incrementAndGet();

        InsuranceBeneficiary b1 = InsuranceBeneficiary.builder()
                .id(benefId)
                .tenantId(tenantId)
                .customerId(custId)
                .policyId(policyId)
                .fullName("Samantha Perera")
                .nic("199005432112")
                .dateOfBirth(LocalDate.of(1990, 7, 22))
                .gender("Female")
                .relationship(InsuranceBeneficiary.Relationship.SPOUSE)
                .allocationPercent(new BigDecimal("60"))
                .mobile(state.phonePrefix + "723456789")
                .email("samantha.perera@example.com")
                .address("123 Galle Road, " + state.countryName)
                .status("ACTIVE")
                .build();

        InsuranceBeneficiary b2 = InsuranceBeneficiary.builder()
                .id(benefId2)
                .tenantId(tenantId)
                .customerId(custId)
                .policyId(policyId)
                .fullName("Nimal Perera")
                .nic("195512345678")
                .dateOfBirth(LocalDate.of(1955, 11, 3))
                .gender("Male")
                .relationship(InsuranceBeneficiary.Relationship.PARENT)
                .allocationPercent(new BigDecimal("40"))
                .mobile(state.phonePrefix + "714567890")
                .email("nimal.perera@example.com")
                .address("456 Kandy Road, " + state.countryName)
                .status("ACTIVE")
                .build();

        state.beneficiaries.put(benefId, b1);
        state.beneficiaries.put(benefId2, b2);
        return List.of(b1, b2);
    }

    public static BeneficiaryResult addBeneficiary(String tenantId, String policyId,
                                                     InsuranceBeneficiary beneficiary,
                                                     AIAInsuranceProvider.MockState state) {
        String benefId = "AIA-" + state.benefSeq.incrementAndGet();
        InsuranceBeneficiary b = InsuranceBeneficiary.builder()
                .id(benefId)
                .tenantId(tenantId)
                .customerId(state.policies.get(policyId) != null
                        ? state.policies.get(policyId).getCustomerId() : "MOCK-CUST")
                .policyId(policyId)
                .fullName(beneficiary.getFullName())
                .nic(beneficiary.getNic())
                .dateOfBirth(beneficiary.getDateOfBirth())
                .gender(beneficiary.getGender())
                .relationship(beneficiary.getRelationship())
                .allocationPercent(beneficiary.getAllocationPercent())
                .mobile(beneficiary.getMobile())
                .email(beneficiary.getEmail())
                .address(beneficiary.getAddress())
                .status("PENDING_ENDORSEMENT")
                .build();
        state.beneficiaries.put(benefId, b);
        return new BeneficiaryResult(true, "PENDING_ENDORSEMENT", null);
    }

    public static BeneficiaryResult updateBeneficiary(String tenantId, String beneficiaryId,
                                                       InsuranceBeneficiary updates,
                                                       AIAInsuranceProvider.MockState state) {
        InsuranceBeneficiary existing = state.beneficiaries.get(beneficiaryId);
        if (existing == null) {
            return new BeneficiaryResult(false, null, "Beneficiary not found");
        }
        if (updates.getAllocationPercent() != null) {
            existing.setAllocationPercent(updates.getAllocationPercent());
        }
        if (updates.getFullName() != null) {
            existing.setFullName(updates.getFullName());
        }
        if (updates.getRelationship() != null) {
            existing.setRelationship(updates.getRelationship());
        }
        state.beneficiaries.put(beneficiaryId, existing);
        return new BeneficiaryResult(true, "UPDATED", null);
    }

    public static BeneficiaryResult removeBeneficiary(String tenantId, String beneficiaryId,
                                                       AIAInsuranceProvider.MockState state) {
        InsuranceBeneficiary existing = state.beneficiaries.get(beneficiaryId);
        if (existing != null) {
            existing.setStatus("REVOKED");
            state.beneficiaries.put(beneficiaryId, existing);
            return new BeneficiaryResult(true, "REMOVED", null);
        }
        return new BeneficiaryResult(false, null, "Beneficiary not found");
    }

    // ================================================================
    // Premium Payments
    // ================================================================

    public static List<InsurancePremium> getPremiumSchedule(String tenantId, String policyId,
                                                            AIAInsuranceProvider.MockState state) {
        String custId = state.policies.get(policyId) != null
                ? state.policies.get(policyId).getCustomerId() : "MOCK-CUST";
        String polNum = state.policies.get(policyId) != null
                ? state.policies.get(policyId).getPolicyNumber() : "MOCK-POL";

        List<InsurancePremium> schedule = new ArrayList<>();
        for (int i = 0; i < 6; i++) {
            LocalDate dueDate = LocalDate.now().plusMonths(i).withDayOfMonth(15);
            InsurancePremium.PremiumStatus status = i == 0
                    ? InsurancePremium.PremiumStatus.PENDING
                    : InsurancePremium.PremiumStatus.PAID;
            String premId = "AIA-" + state.premiumSeq.incrementAndGet();
            InsurancePremium p = InsurancePremium.builder()
                    .id(premId)
                    .tenantId(tenantId)
                    .customerId(custId)
                    .policyId(policyId)
                    .policyNumber(polNum)
                    .status(status)
                    .dueAmount(new BigDecimal("12000.00"))
                    .currency(state.currency)
                    .dueDate(dueDate)
                    .paidAmount(status == InsurancePremium.PremiumStatus.PAID
                            ? new BigDecimal("12000.00") : null)
                    .paidDate(status == InsurancePremium.PremiumStatus.PAID
                            ? dueDate.minusDays(3) : null)
                    .paymentMethod(status == InsurancePremium.PremiumStatus.PAID ? "AUTO_DEBIT" : null)
                    .paymentReference(status == InsurancePremium.PremiumStatus.PAID
                            ? "PAY-AIA-" + random6() : null)
                    .autoDebit(true)
                    .debitAccount("****4521")
                    .debitMethod("BANK")
                    .gracePeriodDays(30)
                    .build();
            schedule.add(p);
            state.premiums.put(premId, p);
        }
        return schedule;
    }

    public static InsurancePremium getNextDuePremium(String tenantId, String policyId,
                                                      AIAInsuranceProvider.MockState state) {
        String premId = "AIA-" + state.premiumSeq.incrementAndGet();
        String custId = state.policies.get(policyId) != null
                ? state.policies.get(policyId).getCustomerId() : "MOCK-CUST";
        String polNum = state.policies.get(policyId) != null
                ? state.policies.get(policyId).getPolicyNumber() : "MOCK-POL";

        InsurancePremium next = InsurancePremium.builder()
                .id(premId)
                .tenantId(tenantId)
                .customerId(custId)
                .policyId(policyId)
                .policyNumber(polNum)
                .status(InsurancePremium.PremiumStatus.PENDING)
                .dueAmount(new BigDecimal("12000.00"))
                .currency(state.currency)
                .dueDate(LocalDate.now().plusMonths(1).withDayOfMonth(15))
                .autoDebit(true)
                .debitAccount("****4521")
                .debitMethod("BANK")
                .gracePeriodDays(30)
                .build();
        state.premiums.put(premId, next);
        return next;
    }

    public static PremiumPaymentResult payPremium(String tenantId, String premiumId,
                                                    PremiumPayment payment,
                                                    AIAInsuranceProvider.MockState state) {
        InsurancePremium premium = state.premiums.get(premiumId);
        if (premium != null) {
            premium.setStatus(InsurancePremium.PremiumStatus.PAID);
            premium.setPaidAmount(payment.amount());
            premium.setPaidDate(LocalDate.now());
            premium.setPaymentMethod(payment.method());
            premium.setPaymentReference("PAY-AIA-" + random6());
            state.premiums.put(premiumId, premium);
        }
        return new PremiumPaymentResult(true, "PAY-AIA-" + random6(),
                InsurancePremium.PremiumStatus.PAID, null);
    }

    public static AutoDebitResult setupAutoDebit(String tenantId, String policyId,
                                                  AutoDebitSetup setup,
                                                  AIAInsuranceProvider.MockState state) {
        state.autoDebit.put(policyId, true);
        String mandateRef = "AIA-" + state.mandateSeq.incrementAndGet();
        return new AutoDebitResult(true, mandateRef, null);
    }

    public static void cancelAutoDebit(String tenantId, String policyId,
                                        AIAInsuranceProvider.MockState state) {
        state.autoDebit.put(policyId, false);
    }

    // ================================================================
    // Utilities
    // ================================================================

    private static String random4() {
        return String.format("%04d", new Random().nextInt(10000));
    }

    private static String random6() {
        return String.format("%06d", new Random().nextInt(1000000));
    }
}
