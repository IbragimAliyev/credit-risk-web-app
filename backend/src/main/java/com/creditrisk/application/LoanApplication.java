package com.creditrisk.application;

import com.creditrisk.risk.ApplicantProfile;
import com.creditrisk.risk.FactorContribution;
import com.creditrisk.risk.HomeOwnership;
import com.creditrisk.risk.LoanIntent;
import com.creditrisk.risk.RiskAssessment;
import com.creditrisk.risk.RiskLabel;
import com.creditrisk.user.User;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

@Entity
@Table(name = "loan_applications")
public class LoanApplication {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // ---- applicant inputs
    @Column(name = "applicant_name", nullable = false)
    private String applicantName;

    @Column(nullable = false)
    private int age;

    /** Annual income. */
    @Column(nullable = false, precision = 15, scale = 2)
    private BigDecimal income;

    @Column(name = "loan_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal loanAmount;

    @Column(name = "employment_years", nullable = false)
    private int employmentYears;

    @Column(name = "credit_history_years", nullable = false)
    private int creditHistoryYears;

    @Column(name = "previous_default", nullable = false)
    private boolean previousDefault;

    @Enumerated(EnumType.STRING)
    @Column(name = "home_ownership", nullable = false, length = 20)
    private HomeOwnership homeOwnership;

    @Enumerated(EnumType.STRING)
    @Column(name = "loan_intent", nullable = false, length = 30)
    private LoanIntent loanIntent;

    // ---- model output
    @Column(name = "default_probability", nullable = false, precision = 6, scale = 5)
    private BigDecimal defaultProbability;

    @Column(name = "risk_score", nullable = false)
    private int riskScore;

    @Enumerated(EnumType.STRING)
    @Column(name = "risk_label", nullable = false, length = 10)
    private RiskLabel riskLabel;

    @Convert(converter = BreakdownConverter.class)
    @Column(name = "breakdown", nullable = false, columnDefinition = "TEXT")
    private List<FactorContribution> breakdown;

    @Column(name = "model_version", nullable = false, length = 50)
    private String modelVersion;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected LoanApplication() {
    }

    public LoanApplication(User user, String applicantName, ApplicantProfile profile, RiskAssessment assessment) {
        this.user = user;
        this.applicantName = applicantName;
        this.age = profile.age();
        this.income = profile.income();
        this.loanAmount = profile.loanAmount();
        this.employmentYears = profile.employmentYears();
        this.creditHistoryYears = profile.creditHistoryYears();
        this.previousDefault = profile.previousDefault();
        this.homeOwnership = profile.homeOwnership();
        this.loanIntent = profile.loanIntent();
        this.defaultProbability = BigDecimal.valueOf(assessment.defaultProbability()).setScale(5, RoundingMode.HALF_UP);
        this.riskScore = assessment.score();
        this.riskLabel = assessment.label();
        this.breakdown = assessment.contributions();
        this.modelVersion = assessment.modelVersion();
    }

    @PrePersist
    void onCreate() {
        this.createdAt = Instant.now();
    }

    public Long getId() { return id; }
    public User getUser() { return user; }
    public String getApplicantName() { return applicantName; }
    public int getAge() { return age; }
    public BigDecimal getIncome() { return income; }
    public BigDecimal getLoanAmount() { return loanAmount; }
    public int getEmploymentYears() { return employmentYears; }
    public int getCreditHistoryYears() { return creditHistoryYears; }
    public boolean isPreviousDefault() { return previousDefault; }
    public HomeOwnership getHomeOwnership() { return homeOwnership; }
    public LoanIntent getLoanIntent() { return loanIntent; }
    public BigDecimal getDefaultProbability() { return defaultProbability; }
    public int getRiskScore() { return riskScore; }
    public RiskLabel getRiskLabel() { return riskLabel; }
    public List<FactorContribution> getBreakdown() { return breakdown; }
    public String getModelVersion() { return modelVersion; }
    public Instant getCreatedAt() { return createdAt; }
}
