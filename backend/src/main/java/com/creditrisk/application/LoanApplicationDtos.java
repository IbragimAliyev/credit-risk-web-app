package com.creditrisk.application;

import com.creditrisk.risk.ApplicantProfile;
import com.creditrisk.risk.FactorContribution;
import com.creditrisk.risk.HomeOwnership;
import com.creditrisk.risk.LoanIntent;
import com.creditrisk.risk.RiskLabel;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

public final class LoanApplicationDtos {

    private LoanApplicationDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 150) String applicantName,
            @NotNull @Min(18) @Max(100) Integer age,
            @NotNull @DecimalMin(value = "0.01", message = "must be greater than 0")
            @DecimalMax("1000000000") @Digits(integer = 13, fraction = 2) BigDecimal income,
            @NotNull @DecimalMin(value = "0.01", message = "must be greater than 0")
            @DecimalMax("1000000000") @Digits(integer = 13, fraction = 2) BigDecimal loanAmount,
            @NotNull @Min(0) @Max(60) Integer employmentYears,
            @NotNull @Min(0) @Max(60) Integer creditHistoryYears,
            @NotNull Boolean previousDefault,
            @NotNull HomeOwnership homeOwnership,
            @NotNull LoanIntent loanIntent) {

        ApplicantProfile toProfile() {
            return new ApplicantProfile(age, income, loanAmount, employmentYears, creditHistoryYears,
                    previousDefault, homeOwnership, loanIntent);
        }
    }

    public enum Effect { INCREASES_RISK, DECREASES_RISK }

    /**
     * @param contribution effect on the log-odds of default versus an average applicant
     */
    public record BreakdownItem(String factor, double contribution, Effect effect, String detail) {
    }

    public record Response(
            Long id,
            String applicantName,
            int age,
            BigDecimal income,
            BigDecimal loanAmount,
            int employmentYears,
            int creditHistoryYears,
            boolean previousDefault,
            HomeOwnership homeOwnership,
            LoanIntent loanIntent,
            BigDecimal defaultProbability,
            int riskScore,
            RiskLabel riskLabel,
            List<BreakdownItem> breakdown,
            String modelVersion,
            Instant createdAt) {

        static Response from(LoanApplication app) {
            List<BreakdownItem> breakdown = app.getBreakdown().stream()
                    .map(c -> toItem(c, app))
                    .toList();
            return new Response(app.getId(), app.getApplicantName(), app.getAge(), app.getIncome(),
                    app.getLoanAmount(), app.getEmploymentYears(), app.getCreditHistoryYears(),
                    app.isPreviousDefault(), app.getHomeOwnership(), app.getLoanIntent(),
                    app.getDefaultProbability(), app.getRiskScore(), app.getRiskLabel(), breakdown,
                    app.getModelVersion(), app.getCreatedAt());
        }

        private static BreakdownItem toItem(FactorContribution c, LoanApplication app) {
            double rounded = Math.round(c.contribution() * 1000) / 1000.0;
            Effect effect = c.contribution() >= 0 ? Effect.INCREASES_RISK : Effect.DECREASES_RISK;
            return new BreakdownItem(c.factor(), rounded, effect, detailFor(c.factor(), app));
        }

        private static String detailFor(String factor, LoanApplication app) {
            return switch (factor) {
                case "Age" -> app.getAge() + " years old";
                case "Income" -> String.format(Locale.US, "$%,.0f per year", app.getIncome());
                case "Loan-to-income" -> {
                    BigDecimal pct = app.getLoanAmount().multiply(BigDecimal.valueOf(100))
                            .divide(app.getIncome(), 0, RoundingMode.HALF_UP);
                    yield "Loan is " + pct + "% of annual income";
                }
                case "Employment length" -> plural(app.getEmploymentYears(), "year") + " employed";
                case "Credit history length" -> plural(app.getCreditHistoryYears(), "year") + " of credit history";
                case "Previous default" -> app.isPreviousDefault() ? "Default on file" : "No default on file";
                case "Home ownership" -> switch (app.getHomeOwnership()) {
                    case RENT -> "Rents";
                    case OWN -> "Owns home outright";
                    case MORTGAGE -> "Has a mortgage";
                    case OTHER -> "Other arrangement";
                };
                case "Loan purpose" -> switch (app.getLoanIntent()) {
                    case EDUCATION -> "Education";
                    case MEDICAL -> "Medical";
                    case VENTURE -> "Business venture";
                    case PERSONAL -> "Personal";
                    case DEBTCONSOLIDATION -> "Debt consolidation";
                    case HOMEIMPROVEMENT -> "Home improvement";
                };
                default -> "";
            };
        }

        private static String plural(int n, String unit) {
            return n + " " + unit + (n == 1 ? "" : "s");
        }
    }
}
