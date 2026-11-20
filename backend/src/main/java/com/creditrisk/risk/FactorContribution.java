package com.creditrisk.risk;

/**
 * How much one factor moved the applicant's risk compared with an average applicant,
 * in log-odds. Positive = increases default risk, negative = decreases it.
 */
public record FactorContribution(String factor, double contribution) {
}
