package com.creditrisk.risk;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Logistic regression credit risk model, trained in Python (ml/train.py) and exported as JSON.
 *
 * <p>Scoring: each input is turned into the same engineered features used in training,
 * standardized with the training mean and scale, then combined linearly:
 * <pre>
 *   z  = intercept + sum( coefficient_i * (x_i - mean_i) / scale_i )
 *   PD = 1 / (1 + e^-z)          probability of default
 * </pre>
 * Because the features are standardized, {@code coefficient_i * (x_i - mean_i) / scale_i} is
 * exactly how far feature i moves this applicant away from an average applicant (in log-odds).
 * Summing those per factor gives an honest, model-faithful score breakdown.
 */
public final class RiskModel {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Column(String name, String factor, double mean, double scale, double coefficient) {
    }

    public record Thresholds(double low, double high) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Metrics(double testAUC, double gini, double ks, int trainingRows, int testRows,
                          double defaultRate) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TestVector(ApplicantProfile input, double expectedProbability) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Definition(String modelVersion, String algorithm, String trainedOn, String target,
                             double intercept, List<Column> columns, Map<String, List<Double>> clip,
                             Thresholds thresholds, Metrics metrics, List<TestVector> testVectors) {
    }

    private final Definition definition;

    private RiskModel(Definition definition) {
        this.definition = definition;
        validate(definition);
    }

    public static RiskModel load(InputStream json) throws IOException {
        return new RiskModel(new ObjectMapper().readValue(json, Definition.class));
    }

    public Definition definition() {
        return definition;
    }

    /** Probability of default for an applicant with every feature at the training average. */
    public double baselineProbability() {
        return sigmoid(definition.intercept());
    }

    public RiskAssessment score(ApplicantProfile applicant) {
        double z = definition.intercept();
        Map<String, Double> byFactor = new LinkedHashMap<>();

        for (Column column : definition.columns()) {
            double x = featureValue(column.name(), applicant);
            double effect = column.coefficient() * (x - column.mean()) / column.scale();
            z += effect;
            byFactor.merge(column.factor(), effect, Double::sum);
        }

        double probability = sigmoid(z);
        List<FactorContribution> contributions = new ArrayList<>();
        byFactor.forEach((factor, value) -> contributions.add(new FactorContribution(factor, value)));
        contributions.sort(Comparator.comparingDouble((FactorContribution c) -> Math.abs(c.contribution())).reversed());

        int score = (int) Math.max(0, Math.min(100, Math.round(probability * 100)));
        return new RiskAssessment(probability, score, labelFor(probability), List.copyOf(contributions),
                definition.modelVersion());
    }

    public RiskLabel labelFor(double probability) {
        if (probability < definition.thresholds().low()) return RiskLabel.LOW;
        if (probability < definition.thresholds().high()) return RiskLabel.MEDIUM;
        return RiskLabel.HIGH;
    }

    // ------------------------------------------------------------------ feature engineering
    // Must mirror design_matrix() in ml/train.py exactly (verified by RiskModelTest).

    private double featureValue(String name, ApplicantProfile a) {
        double age = clip("age", a.age());
        double income = clip("income", a.income().doubleValue());
        double loanAmount = clip("loanAmount", a.loanAmount().doubleValue());
        double employmentYears = clip("employmentYears", a.employmentYears());
        double creditHistoryYears = clip("creditHistoryYears", a.creditHistoryYears());

        switch (name) {
            case "age": return age;
            case "log_income": return Math.log(income);
            case "loan_to_income": return loanAmount / income;
            case "employment_years": return employmentYears;
            case "credit_history_years": return creditHistoryYears;
            case "previous_default": return a.previousDefault() ? 1.0 : 0.0;
            default:
                if (name.startsWith(HOME_PREFIX)) {
                    return a.homeOwnership().name().equals(name.substring(HOME_PREFIX.length())) ? 1.0 : 0.0;
                }
                if (name.startsWith(INTENT_PREFIX)) {
                    return a.loanIntent().name().equals(name.substring(INTENT_PREFIX.length())) ? 1.0 : 0.0;
                }
                throw new IllegalStateException("Unknown model column: " + name);
        }
    }

    private static final String HOME_PREFIX = "home_ownership=";
    private static final String INTENT_PREFIX = "loan_intent=";
    private static final List<String> NUMERIC_COLUMNS = List.of(
            "age", "log_income", "loan_to_income",
            "employment_years", "credit_history_years", "previous_default");

    /** Inputs outside the training range are clamped to it; the model never extrapolates. */
    private double clip(String input, double value) {
        List<Double> range = definition.clip() == null ? null : definition.clip().get(input);
        if (range == null) return value;
        return Math.max(range.get(0), Math.min(range.get(1), value));
    }

    private static double sigmoid(double z) {
        return 1.0 / (1.0 + Math.exp(-z));
    }

    /** Fail fast at startup if the JSON doesn't match what this code knows how to compute. */
    private static void validate(Definition d) {
        if (d.columns() == null || d.columns().isEmpty() || d.thresholds() == null) {
            throw new IllegalStateException("Model definition is missing columns or thresholds");
        }
        if (!(d.thresholds().low() < d.thresholds().high())) {
            throw new IllegalStateException("Model thresholds must satisfy low < high");
        }
        for (Column c : d.columns()) {
            String n = c.name();
            if (n.startsWith(HOME_PREFIX)) {
                HomeOwnership.valueOf(n.substring(HOME_PREFIX.length()));
            } else if (n.startsWith(INTENT_PREFIX)) {
                LoanIntent.valueOf(n.substring(INTENT_PREFIX.length()));
            } else if (!NUMERIC_COLUMNS.contains(n)) {
                throw new IllegalStateException("Unknown model column: " + n);
            }
            if (!(c.scale() > 0)) {
                throw new IllegalStateException("Column " + n + " has non-positive scale");
            }
        }
    }
}
