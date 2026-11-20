package com.creditrisk.risk;

import java.math.BigDecimal;

/** The applicant data the model scores. Field names match the training data export. */
public record ApplicantProfile(
        int age,
        BigDecimal income,
        BigDecimal loanAmount,
        int employmentYears,
        int creditHistoryYears,
        boolean previousDefault,
        HomeOwnership homeOwnership,
        LoanIntent loanIntent) {
}
