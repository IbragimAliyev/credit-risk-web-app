package com.creditrisk.risk;

import java.util.List;

/**
 * Result of scoring an application.
 *
 * @param defaultProbability model's estimated probability of default (0..1)
 * @param score              the same probability as a 0..100 risk score
 * @param label              LOW / MEDIUM / HIGH band
 * @param contributions      per-factor effect, sorted by absolute impact (largest first)
 * @param modelVersion       version of the model that produced this result
 */
public record RiskAssessment(
        double defaultProbability,
        int score,
        RiskLabel label,
        List<FactorContribution> contributions,
        String modelVersion) {
}
