package com.creditrisk.risk;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

/** Spring entry point to the credit risk model; loads the exported model once at startup. */
@Service
public class RiskScoringService {

    private final RiskModel model;

    public RiskScoringService(@Value("${app.model.location:classpath:model/credit_risk_model.json}") Resource modelFile) {
        try (InputStream in = modelFile.getInputStream()) {
            this.model = RiskModel.load(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not load risk model from " + modelFile, e);
        }
    }

    public RiskAssessment assess(ApplicantProfile applicant) {
        if (applicant.income() == null || applicant.income().signum() <= 0) {
            throw new IllegalArgumentException("income must be positive");
        }
        if (applicant.loanAmount() == null || applicant.loanAmount().signum() <= 0) {
            throw new IllegalArgumentException("loanAmount must be positive");
        }
        return model.score(applicant);
    }

    public RiskModel model() {
        return model;
    }
}
