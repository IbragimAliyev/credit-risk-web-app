package com.creditrisk.risk;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RiskModelTest {

    private static RiskModel model;

    @BeforeAll
    static void loadModel() throws Exception {
        try (InputStream in = RiskModelTest.class.getResourceAsStream("/model/credit_risk_model.json")) {
            model = RiskModel.load(in);
        }
    }

    /** Java must reproduce the Python-trained model's probabilities exactly. */
    @Test
    void matchesPythonOnExportedTestVectors() {
        assertFalse(model.definition().testVectors().isEmpty());
        for (RiskModel.TestVector v : model.definition().testVectors()) {
            double p = model.score(v.input()).defaultProbability();
            assertEquals(v.expectedProbability(), p, 1e-9, () -> "Mismatch for " + v.input());
        }
    }

    @Test
    void contributionsExplainTheWholeScore() {
        RiskAssessment r = model.score(applicant(30, 50_000, 20_000, false, HomeOwnership.RENT));
        double logit = Math.log(r.defaultProbability() / (1 - r.defaultProbability()));
        double sum = r.contributions().stream().mapToDouble(FactorContribution::contribution).sum();
        assertEquals(logit - model.definition().intercept(), sum, 1e-9);
        // Sorted by absolute impact, largest first.
        for (int i = 1; i < r.contributions().size(); i++) {
            assertTrue(Math.abs(r.contributions().get(i - 1).contribution())
                    >= Math.abs(r.contributions().get(i).contribution()));
        }
    }

    @Test
    void riskMovesInTheExpectedDirection() {
        double base = pd(applicant(30, 50_000, 10_000, false, HomeOwnership.RENT));
        assertTrue(pd(applicant(30, 50_000, 30_000, false, HomeOwnership.RENT)) > base, "bigger loan vs income");
        assertTrue(pd(applicant(30, 50_000, 10_000, true, HomeOwnership.RENT)) > base, "previous default");
        assertTrue(pd(applicant(30, 50_000, 10_000, false, HomeOwnership.OWN)) < base, "home owner");
        assertTrue(pd(applicant(30, 120_000, 24_000, false, HomeOwnership.RENT))
                < pd(applicant(30, 50_000, 10_000, false, HomeOwnership.RENT)), "higher income, same ratio");
    }

    @Test
    void scoreAndLabelFollowProbability() {
        RiskAssessment r = model.score(applicant(30, 50_000, 20_000, false, HomeOwnership.RENT));
        assertEquals(Math.round(r.defaultProbability() * 100), r.score());
        assertEquals(model.labelFor(r.defaultProbability()), r.label());

        RiskModel.Thresholds t = model.definition().thresholds();
        assertEquals(RiskLabel.LOW, model.labelFor(t.low() - 1e-6));
        assertEquals(RiskLabel.MEDIUM, model.labelFor(t.low()));
        assertEquals(RiskLabel.MEDIUM, model.labelFor(t.high() - 1e-6));
        assertEquals(RiskLabel.HIGH, model.labelFor(t.high()));
    }

    @Test
    void inputsOutsideTrainingRangeAreClipped() {
        double maxAge = model.definition().clip().get("age").get(1);
        assertEquals(pd(withAge((int) maxAge)), pd(withAge(100)), 1e-12);
    }

    @Test
    void rejectsUnknownColumns() {
        String json = """
                {"modelVersion":"x","intercept":0,
                 "columns":[{"name":"shoe_size","factor":"Shoes","mean":0,"scale":1,"coefficient":1}],
                 "thresholds":{"low":0.1,"high":0.5}}""";
        assertThrows(IllegalStateException.class,
                () -> RiskModel.load(new ByteArrayInputStream(json.getBytes(StandardCharsets.UTF_8))));
    }

    private static double pd(ApplicantProfile a) {
        return model.score(a).defaultProbability();
    }

    private static ApplicantProfile withAge(int age) {
        return new ApplicantProfile(age, BigDecimal.valueOf(50_000), BigDecimal.valueOf(10_000), 5, 6, false,
                HomeOwnership.RENT, LoanIntent.PERSONAL);
    }

    private static ApplicantProfile applicant(int age, long income, long loan, boolean previousDefault,
                                              HomeOwnership home) {
        return new ApplicantProfile(age, BigDecimal.valueOf(income), BigDecimal.valueOf(loan), 4, 5,
                previousDefault, home, LoanIntent.PERSONAL);
    }
}
